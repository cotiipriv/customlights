// CustomLights shared code: scene reconstruction and light shapes.
// A light is a shape of size `radius` that is fully lit (flat), whose light blurs out over `distance` past its
// surface.
// axes (Y = where it points) and divided by the stretch.

// Written once per frame by gbuffer.fsh: rgb = world normal, a = view depth (negative for the hand, 0 for the
uniform sampler2D GBuffer;
uniform mat4 InvWorldProjection;
uniform mat4 InvHandProjection;
uniform mat3 ViewToWorld;
uniform vec2 ScreenSize;

// xyz: position relative to the camera, w: radius
uniform vec4 LightPos;
// rgb: color, w: intensity (fade included)
uniform vec4 LightColor;
// x: blur distance, y: atmosphere 0..1, z: shape id, w: half width per unit of length of cones (tan of half the
uniform vec4 LightParams;
// Light axes in world space (columns X, Y = direction, Z)
uniform mat3 LightBasis;
uniform vec3 LightStretch;
// The point of the light the stretch grows from, in its own unstretched units (0 = its middle)
uniform vec3 LightPivot;
// Extra glow the light leaves on the surfaces it reaches, whatever colour they are (0 = off)
uniform float Luminosity;
// Seconds since the game started, for the shapes that move on their own
uniform float Time;
// Each light gets its own offset in that time, so two fires never burn in step
uniform float LightPhase;
// Half sizes (world axes) of the box the light is swept over (a group of objects lit as one); 0 for single
uniform vec3 LightExtent;
// xyz: bounds centre relative to the camera, w: bounds radius (world)
uniform vec4 LightBound;

const int SPHERE = 0;
const int PAD = 1;
const int CONE = 2;
const int INVERTED_CONE = 3;
const int SPOTLIGHT = 4;
const int BEAM = 5;
const int RING = 6;
const int BOX = 7;
const int STAR = 8;
const int CROSS = 10;
const int TRIANGLE = 11;
const int FLASHLIGHT = 12;
const int SIREN = 13;
const int FIRE = 14;
const int PORTAL = 15;
const int LIGHTNING = 16;
const int AURORA = 17;
const int RIPPLE = 18;
const int PLASMA = 20;
const int TORNADO = 21;
const int SINGULARITY = 22;
const int RUNE = 23;
const int FLARE = 24;
const int MIST = 25;
const int COMET = 26;
const int LASER_DOOR = 27;


int shapeId() {
    return int(LightParams.z + 0.5);
}

float lightRadius() {
    return max(LightPos.w, 0.0);
}

float blurDistance() {
    return max(LightParams.x, 0.0);
}

float coneSpread() {
    return max(LightParams.w, 0.001);
}

// ── Spaces
// ──────────────────────────────────────────────────────────

vec3 toShape(vec3 v) {
    return ((transpose(LightBasis) * v) - LightPivot) / LightStretch + LightPivot;
}

// The same for a direction: no pivot, it only shifts points
vec3 directionToShape(vec3 v) {
    return (transpose(LightBasis) * v) / LightStretch;
}

// A point relative to the light, in the light's own space, after the sweep: every point of the swept box counts
// the light's centre, so a group lights like all its members together
vec3 sweptShape(vec3 fromLight) {
    return toShape(fromLight - clamp(fromLight, -LightExtent, LightExtent));
}

bool isSwept() {
    return LightExtent.x > 0.0 || LightExtent.y > 0.0 || LightExtent.z > 0.0;
}

vec3 vectorToWorld(vec3 v) {
    return LightBasis * (v * LightStretch);
}

vec3 normalToShape(vec3 n) {
    return normalize((transpose(LightBasis) * n) * LightStretch);
}

// ── Scene
// ───────────────────────────────────────────────────────────

// A stored depth as 0 at the near plane up to 1 for nothing drawn, also where the game keeps its depth reversed
float farness(float stored) {
#ifdef REVERSED_DEPTH
    return 1.0 - stored;
#else
    return stored;
#endif
}

// `depth` comes out as farness (1 = nothing drawn there)
vec3 viewPosition(sampler2D depthTexture, mat4 inverseProjection, vec2 uv, out float depth) {
    float stored = textureLod(depthTexture, uv, 0.0).r;
    depth = farness(stored);
#ifdef DEPTH_ZERO_TO_ONE
    float ndcDepth = stored;
#else
    float ndcDepth = stored * 2.0 - 1.0;
#endif
    vec4 view = inverseProjection * vec4(uv * 2.0 - 1.0, ndcDepth, 1.0);
    return view.xyz / view.w;
}

// Where view rays start (not quite the origin while the view bobs), per projection
uniform vec3 WorldEye;
uniform vec3 HandEye;

// Point on the far plane seen through uv (view space)
vec3 viewRay(vec2 uv, bool hand) {
    vec4 far = (hand ? InvHandProjection : InvWorldProjection) * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    return far.xyz / far.w;
}

// View-space position of the visible surface (from the G-buffer); for the sky, a point far along the view ray
vec3 scenePosition(vec2 uv, out bool hand, out bool sky) {
    float storedDepth = texture(GBuffer, uv).a;
    sky = storedDepth == 0.0;
    hand = storedDepth < 0.0;
    vec3 far = viewRay(uv, hand);
    if (sky) {
        return far;
    }
    // Along the ray from the eye to the point at that depth
    vec3 eye = hand ? HandEye : WorldEye;
    vec3 ray = far - eye;
    return eye + ray * ((-abs(storedDepth) - eye.z) / min(ray.z, -1.0e-4));
}

// From view space to where the camera really is. The view bobs, which moves the origin of view space a little away
// from the camera; lights are placed against the camera, so that offset has to come out or everything slides with the
// bobbing (a grid of thin beams shows it plainly).
vec3 toWorld(vec3 view, bool hand) {
    return ViewToWorld * (view - (hand ? HandEye : WorldEye));
}

// World-space normal of the visible surface
vec3 sceneNormal(vec2 uv) {
    return texture(GBuffer, uv).rgb;
}

// ── 2D shapes (unit size, negative inside)
// ──────────────────────────

float sdStar(vec2 p) {
    const vec2 k1 = vec2(0.809016994375, -0.587785252292);
    const vec2 k2 = vec2(-0.809016994375, -0.587785252292);
    p.x = abs(p.x);
    p -= 2.0 * max(dot(k1, p), 0.0) * k1;
    p -= 2.0 * max(dot(k2, p), 0.0) * k2;
    p.x = abs(p.x);
    p.y -= 1.0;
    vec2 ba = 0.45 * vec2(-k1.y, k1.x) - vec2(0.0, 1.0);
    float h = clamp(dot(p, ba) / dot(ba, ba), 0.0, 1.0);
    return length(p - ba * h) * sign(p.y * ba.x - p.x * ba.y);
}

float sdBox2(vec2 p, vec2 b) {
    vec2 d = abs(p) - b;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}

float sdTriangle(vec2 p, vec2 p0, vec2 p1, vec2 p2) {
    vec2 e0 = p1 - p0;
    vec2 e1 = p2 - p1;
    vec2 e2 = p0 - p2;
    vec2 v0 = p - p0;
    vec2 v1 = p - p1;
    vec2 v2 = p - p2;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
                     vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
                     vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y);
}

float flatDistance(int shape, vec2 p) {
    if (shape == STAR) {
        return sdStar(p);
    }
    if (shape == CROSS) {
        return min(sdBox2(p, vec2(1.0, 0.32)), sdBox2(p, vec2(0.32, 1.0)));
    }
    if (shape == TRIANGLE) {
        return sdTriangle(p, vec2(0.0, 1.0), vec2(0.866, -0.5), vec2(-0.866, -0.5));
    }
    return length(p) - 1.0;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Smooth noise, for the shapes that move on their own
float valueNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(cell);
    float b = hash12(cell + vec2(1.0, 0.0));
    float c = hash12(cell + vec2(0.0, 1.0));
    float d = hash12(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Three octaves of it: soft, cloudy edges
float fbm(vec2 p) {
    float sum = 0.0;
    float amount = 0.5;
    for (int i = 0; i < 3; i++) {
        sum += valueNoise(p) * amount;
        p *= 2.03;
        amount *= 0.5;
    }
    return sum;
}

// ── 3D shapes

// ───────────────────────────────────────────────────────

// Length of cones and beams (q space); matches LightShape.length
float coneHeight(int shape, float radius) {
    if (shape == SPOTLIGHT) {
        return radius * 6.0;
    }
    if (shape == BEAM) {
        return radius * 16.0;
    }
    if (shape == FLASHLIGHT) {
        return radius * 12.0;
    }
    if (shape == SIREN) {
        return radius * 16.0;
    }
    if (shape == FIRE) {
        return radius * 4.0;
    }
    if (shape == LIGHTNING) {
        return radius * 10.0;
    }
    if (shape == AURORA) {
        return radius * 4.0;
    }
    if (shape == TORNADO) {
        return radius * 8.0;
    }
    if (shape == COMET) {
        return radius * 7.0;
    }
    if (shape == LASER_DOOR) {
        return radius * 2.4;
    }



    return radius * 2.0;
}

// Signed distance (q space) from q to the lit shape.
float shapeDistance(vec3 q, out vec3 toLight) {
    int shape = shapeId();
    float r = max(lightRadius(), 0.001);
    float thin = max(0.05, r * 0.08);
    float len = length(q);
    toLight = len > 1.0e-4 ? -q / len : vec3(0.0, 1.0, 0.0);

    if (shape == SPHERE) {
        return len - r;
    }
    if (shape == BOX) {
        vec3 d = abs(q) - vec3(r);
        return length(max(d, 0.0)) + min(max(d.x, max(d.y, d.z)), 0.0);
    }
    if (shape == PAD || shape == STAR || shape == CROSS || shape == TRIANGLE) {
        float d2 = flatDistance(shape, q.xz / r) * r;
        vec2 w = vec2(d2, abs(q.y) - thin);
        toLight = vec3(0.0, q.y > 0.0 ? -1.0 : 1.0, 0.0);
        return min(max(w.x, w.y), 0.0) + length(max(w, 0.0));
    }
    if (shape == RING) {
        float rho = length(q.xz);
        vec2 t = vec2(rho - r, q.y);
        vec3 nearest = rho > 1.0e-4 ? vec3(q.x / rho * r, 0.0, q.z / rho * r) : vec3(r, 0.0, 0.0);
        vec3 v = nearest - q;
        toLight = length(v) > 1.0e-4 ? normalize(v) : vec3(0.0, 1.0, 0.0);
        return length(t) - thin;
    }
    if (shape == FIRE) {
        float h = coneHeight(FIRE, r);
        float up = clamp(q.y / h, 0.0, 1.0);
        float time = Time * 1.3 + LightPhase;
        // The noise scrolls down the flame, which is what makes it look like it is rising
        float rising = fbm(vec2(up * 3.1 - time * 1.7, LightPhase * 2.7)) - 0.5;
        float rising2 = fbm(vec2(up * 3.1 - time * 1.7, LightPhase * 2.7 + 8.4)) - 0.5;
        // It leans more the higher it goes
        vec2 lean = vec2(rising, rising2) * r * 2.2 * up * up;
        // How tall it reaches changes as it burns, and the tongue thins out long before that
        float reach = 0.72 + 0.24 * fbm(vec2(time * 1.2, LightPhase + 21.0));
        float taper = pow(clamp(1.0 - up / reach, 0.0, 1.0), 1.15);
        float base = smoothstep(0.0, 0.14, up + 0.03);
        // Noise around the flame as well as up it, so it is not a ring stacked on a ring
        float around = atan(q.z - lean.y, q.x - lean.x);
        float swirl = fbm(vec2(around * 1.7 + up * 2.2, time * 1.5 + LightPhase)) - 0.5;
        float lick = 0.7 + 0.55 * fbm(vec2(up * 4.2 - time * 2.4, LightPhase + 3.1)) + 0.5 * swirl;
        float width = max(r * taper * base * lick, 1.0e-4);
        vec3 axis = vec3(lean.x, clamp(q.y, 0.0, h), lean.y) - q;
        toLight = length(axis) > 1.0e-4 ? normalize(axis) : vec3(0.0, 1.0, 0.0);
        float d = max(length(q.xz - lean) - width, max(q.y - h, -q.y - r * 0.3));
        // The noise makes it steeper than a real distance, so it is kept short of one
        return d * 0.5;
    }
    if (shape == PORTAL) {
        float rho = length(q.xz);
        float around = atan(q.z, q.x);
        float time = Time * 1.1 + LightPhase;
        // A ring that ripples as it turns, with a thin sheet inside it
        float ringRadius = r * (1.0 + 0.14 * sin(around * 5.0 + time * 2.4) + 0.07 * sin(around * 3.0 - time * 1.7));
        float tube = thin * 2.4 * (0.75 + 0.45 * sin(around * 5.0 - time * 3.1));
        vec2 nearest = rho > 1.0e-4 ? q.xz / rho * ringRadius : vec2(ringRadius, 0.0);
        vec3 toRing = vec3(nearest.x, 0.0, nearest.y) - q;
        toLight = length(toRing) > 1.0e-4 ? normalize(toRing) : vec3(0.0, 1.0, 0.0);
        float ring = length(vec2(rho - ringRadius, q.y)) - tube;
        vec2 disc = vec2(rho - ringRadius * 0.82, abs(q.y) - thin * 0.7);
        return min(ring, min(max(disc.x, disc.y), 0.0) + length(max(disc, 0.0)));
    }
    if (shape == LIGHTNING) {
        float h = coneHeight(LIGHTNING, r);
        float up = clamp(q.y / h, 0.0, 1.0);
        // The bolt is redrawn several times a second instead of sliding around
        float strike = floor(Time * 5.0 + LightPhase);
        vec2 kink = vec2(valueNoise(vec2(up * 7.0, strike)) - 0.5, valueNoise(vec2(up * 7.0, strike + 19.7)) - 0.5) * r * 4.0 * up;
        float core = thin * 2.0 + r * 0.55;
        vec3 axis = vec3(kink.x, clamp(q.y, 0.0, h), kink.y) - q;
        toLight = length(axis) > 1.0e-4 ? normalize(axis) : vec3(0.0, 1.0, 0.0);
        float bolt = max(length(q.xz - kink) - core, max(q.y - h, -q.y));
        // A branch leaves the middle of it and stops short
        float split = clamp((up - 0.35) / 0.45, 0.0, 1.0);
        vec2 away = vec2(valueNoise(vec2(strike, 5.2)) - 0.5, valueNoise(vec2(strike, 9.9)) - 0.5) * r * 6.0 * split;
        float branch = max(length(q.xz - kink - away) - core * 0.6, max(q.y - h * 0.8, -q.y + h * 0.3));
        return min(bolt, branch) * 0.7;
    }
    if (shape == AURORA) {
        float h = coneHeight(AURORA, r);
        float up = clamp(q.y / h, 0.0, 1.0);
        float time = Time * 0.5 + LightPhase;
        float across = q.x / max(r, 0.001);
        // A curtain that waves along its width, thicker at the bottom and fading out towards the top
        float wave = (sin(across * 2.2 + time * 1.7) * 0.4 + sin(across * 4.7 - time * 1.1) * 0.16
                + (fbm(vec2(across * 1.3, time * 0.8)) - 0.5) * 0.5) * r;
        float thickness = (thin * 2.0 + r * 0.16) * (1.0 - 0.85 * smoothstep(0.25, 1.0, up));
        float sheet = abs(q.z - wave) - max(thickness, 1.0e-4);
        // The sides and the top close in smoothly, so the curtain has no straight edge
        float sides = abs(q.x) - r * (1.0 - 0.25 * up * up);
        toLight = vec3(0.0, 0.0, q.z > wave ? -1.0 : 1.0);
        return max(sheet, max(sides, max(q.y - h, -q.y))) * 0.8;
    }
    if (shape == LASER_DOOR) {
        float h = coneHeight(LASER_DOOR, r);
        float time = Time * 0.25 + LightPhase;
        // Bars as thin as a real beam, spaced so you can see between them
        float gapX = max(r * 0.3, 0.12);
        float gapY = max(r * 0.3, 0.12);
        float beam = max(r * 0.026, 0.014);
        // The uprights stand still and the rungs creep upwards
        float bars = abs(mod(q.x + gapX * 0.5, gapX) - gapX * 0.5) - beam;
        float rungs = abs(mod(q.y - time * gapY + gapY * 0.5, gapY) - gapY * 0.5) - beam;
        // The frame around the doorway, a little heavier than the bars
        float edgeX = abs(abs(q.x) - r) - beam * 1.8;
        float edgeY = abs(abs(q.y - h * 0.5) - h * 0.5) - beam * 1.8;
        float frame = min(edgeX, edgeY);
        float lines = min(min(bars, rungs), frame);
        float sheet = abs(q.z) - max(r * 0.015, 0.012);
        float inside = max(abs(q.x) - r, max(q.y - h, -q.y));
        toLight = vec3(0.0, 0.0, q.z > 0.0 ? -1.0 : 1.0);
        return max(max(lines, sheet), inside);
    }
    if (shape == SINGULARITY) {

        float rho = length(q.xz);
        float around = atan(q.z, q.x);
        float time = Time * 1.2 + LightPhase;
        float outward = rho / max(r, 0.001);
        // Arms wound into a spiral, dragged faster the closer they are to the middle
        float spin = around * 2.0 + outward * 5.0 - time * 3.0;
        float arms = 0.45 + 0.55 * (0.5 + 0.5 * sin(spin));
        float band = abs(rho - r * 0.7) - r * 0.4 * arms;
        // The disc is thin at its rim and a little deeper near the core
        float plate = abs(q.y) - (thin * 2.0 + r * 0.08) * (1.0 - 0.7 * smoothstep(0.2, 1.1, outward));
        vec2 nearest = rho > 1.0e-4 ? q.xz / rho * r * 0.7 : vec2(r * 0.7, 0.0);
        vec3 toDisc = vec3(nearest.x, 0.0, nearest.y) - q;
        toLight = length(toDisc) > 1.0e-4 ? normalize(toDisc) : vec3(0.0, 1.0, 0.0);
        // Nothing comes out of the middle
        return max(max(band, plate), r * 0.26 - rho);
    }
    if (shape == RUNE) {
        float rho = length(q.xz);
        float around = atan(q.z, q.x);
        float time = Time * 0.7 + LightPhase;
        float beat = 1.0 + 0.03 * sin(time * 3.0);
        float slab = abs(q.y) - (thin * 1.6 + r * 0.04);
        float outer = abs(rho - r * beat) - r * 0.055;
        float middle = abs(rho - r * 0.62 * beat) - r * 0.04;
        float inner = abs(rho - r * 0.3) - r * 0.03;
        // Marks between the two outer rings, turning the other way
        float sector = 0.7853982;
        float mark = abs(mod(around + time + sector * 0.5, sector) - sector * 0.5);
        float spokes = max(sin(mark) * rho - r * 0.035, max(rho - r * 0.98, r * 0.64 - rho));
        toLight = vec3(0.0, q.y > 0.0 ? -1.0 : 1.0, 0.0);
        return max(min(min(outer, middle), min(inner, spokes)), slab);
    }
    if (shape == FLARE) {
        float len2 = length(q);
        vec3 dir = len2 > 1.0e-4 ? q / len2 : vec3(0.0, 1.0, 0.0);
        float time = Time * 0.8 + LightPhase;
        // Spikes that grow and pull back in, in their own directions
        float spike = fbm(vec2(dir.x * 3.2 + dir.z * 1.9, dir.y * 3.0 - time));
        float reach = r * (0.4 + 1.3 * smoothstep(0.3, 0.85, spike));
        toLight = -dir;
        return (len2 - reach) * 0.7;
    }
    if (shape == MIST) {
        float time = Time * 0.12 + LightPhase;
        // A low bank of fog whose top rolls slowly
        float top = r * 0.4 * (0.5 + 0.9 * fbm(vec2(q.x * 0.5 + time, q.z * 0.5 - time * 0.8)));
        float body = max(q.y - top, -q.y - r * 0.12);
        float sides = length(q.xz) - r * (0.85 + 0.3 * fbm(vec2(atan(q.z, q.x) * 1.3, time * 0.6)));
        toLight = vec3(0.0, 1.0, 0.0);
        return max(body, sides) * 0.7;
    }
    if (shape == COMET) {
        float h = coneHeight(COMET, r);
        float up = clamp(q.y / h, 0.0, 1.0);
        float time = Time * 1.5 + LightPhase;
        // The tail streams away, wavering and thinning out to nothing
        vec2 drift = vec2(fbm(vec2(up * 2.4 - time, LightPhase)) - 0.5, fbm(vec2(up * 2.4 - time, LightPhase + 5.1)) - 0.5) * r * 2.0 * up;
        float width = r * pow(clamp(1.0 - up, 0.0, 1.0), 1.3) * (0.75 + 0.5 * fbm(vec2(up * 3.6 - time * 1.6, LightPhase + 2.0)));
        float tail = max(length(q.xz - drift) - max(width, 1.0e-4), max(q.y - h, -q.y));
        float head = length(q) - r * (0.9 + 0.08 * sin(time * 4.0));
        float len2 = length(q);
        toLight = len2 > 1.0e-4 ? -q / len2 : vec3(0.0, 1.0, 0.0);
        return min(head, tail * 0.8);
    }
    if (shape == RIPPLE) {
        float rho = length(q.xz);
        float outward = rho / max(r, 0.001);
        float time = Time * 1.4 + LightPhase;
        // A round pool whose rim rises and falls as the wave runs outwards
        float wave = sin(outward * 3.4 - time * 2.2) * 0.5 + 0.5;
        float lift = r * 0.7 * smoothstep(0.2, 1.0, outward) * (0.25 + 0.75 * wave);
        float sheet = abs(q.y - lift) - (thin * 1.8 + r * 0.05);
        toLight = vec3(0.0, q.y > lift ? -1.0 : 1.0, 0.0);
        return max(sheet, rho - r);
    }
    if (shape == PLASMA) {
        float len2 = length(q);
        vec3 dir = len2 > 1.0e-4 ? q / len2 : vec3(0.0, 1.0, 0.0);
        float time = Time * 0.7 + LightPhase;
        // A ball whose surface boils
        float boil = fbm(vec2(dir.x * 2.6 + time, dir.y * 2.2 - dir.z * 1.9 + time * 0.6)) - 0.5;
        toLight = -dir;
        return len2 - r * (1.0 + 0.4 * boil);
    }
    if (shape == TORNADO) {
        float h = coneHeight(TORNADO, r);
        float up = clamp(q.y / h, 0.0, 1.0);
        float time = Time * 1.0 + LightPhase;
        // A funnel: narrow where it touches the ground, wide and turning at the top
        float twist = up * 5.5 - time * 2.6;
        vec2 middle = vec2(cos(twist), sin(twist)) * r * 0.4 * up;
        float width = r * (0.2 + 1.2 * up * up);
        float wall = abs(length(q.xz - middle) - width) - (thin * 2.0 + r * 0.14);
        vec3 axis = vec3(middle.x, clamp(q.y, 0.0, h), middle.y) - q;
        toLight = length(axis) > 1.0e-4 ? normalize(axis) : vec3(0.0, 1.0, 0.0);
        return max(wall, max(q.y - h, -q.y)) * 0.8;
    }
    float height = coneHeight(shape, r);
    vec2 p = vec2(length(q.xz), q.y);
    if (shape == BEAM) {
        vec3 axis = vec3(0.0, clamp(q.y, 0.0, height), 0.0) - q;
        toLight = length(axis) > 1.0e-4 ? normalize(axis) : vec3(0.0, 1.0, 0.0);
        // Straight for most of its length and then drawn into a point, instead of ending flat
        float straight = height * 0.72;
        vec2 w = vec2(p.x - r, abs(q.y - straight * 0.5) - straight * 0.5);
        float body = min(max(w.x, w.y), 0.0) + length(max(w, 0.0));
        float point = sdTriangle(p, vec2(-r, straight), vec2(r, straight), vec2(0.0, height));
        return min(body, point);
    }
    float width = height * coneSpread();
    if (shape == INVERTED_CONE) {
        toLight = vec3(0.0, q.y < 0.0 ? 1.0 : -1.0, 0.0);
        return sdTriangle(p, vec2(-width, 0.0), vec2(width, 0.0), vec2(0.0, height));
    }
    // CONE, SPOTLIGHT and FLASHLIGHT: tip at the light, opening along Y
    return sdTriangle(p, vec2(0.0, 0.0), vec2(width, height), vec2(-width, height));
}

// A torch: bright centre, a faint dark ring like a real reflector, a soft rim and a little spill around it,
// with distance.
float flashlightLight(vec3 q) {
    float r = max(lightRadius(), 0.001);
    float reach = coneHeight(FLASHLIGHT, r);
    float t = q.y / reach;
    if (t <= 0.0 || t >= 1.0) {
        return 0.0;
    }
    float x = length(q.xz) / (q.y * coneSpread());
    float soft = min(0.08 + blurDistance() * 0.2, 1.5);
    float core = 1.0 - smoothstep(1.0 - soft, 1.0 + soft, x);
    // Like a real torch: a tight bright hotspot, a slightly darker ring at the reflector's edge, then a wide soft
    // corona of spill light around the beam, all dimming with distance almost like the inverse square
    float hot = 1.0 + 1.6 * exp(-x * x * 9.0);
    float ringX = (x - 0.75) / 0.08;
    float ring = 1.0 - 0.28 * exp(-ringX * ringX);
    float spill = 0.34 * (1.0 - smoothstep(0.8, 2.8, x)) * (1.0 - 0.4 * t);
    float fade = 1.0 / (1.0 + t * t * 10.0);
    float ends = smoothstep(0.0, 0.015, t) * (1.0 - smoothstep(0.7, 1.0, t));
    return (core * hot * ring + spill) * fade * ends;
}

// A warning light: a glowing core and two opposite beams along X that sweep as the light turns around Y.
// 0..~1.6
float sirenLight(vec3 q) {
    float r = max(lightRadius(), 0.001);
    float reach = coneHeight(SIREN, r);
    float d = length(q);
    float coreSize = r * 0.35;
    float core = 1.0 - smoothstep(coreSize, coreSize + blurDistance() * 0.5 + r * 0.6, d);
    float along = abs(q.x);
    float t = along / reach;
    if (t >= 1.0) {
        return core * 1.3;
    }
    float across = length(q.yz) / max(along, 1.0e-3) / coneSpread();
    float soft = min(0.15 + blurDistance() * 0.2, 1.5);
    float beam = 1.0 - smoothstep(1.0 - soft, 1.0 + soft, across);
    beam *= 1.0 + 0.7 * exp(-across * across * 4.0);
    beam *= smoothstep(0.0, 0.03, t) * (1.0 - smoothstep(0.6, 1.0, t)) / (1.0 + t * t * 3.0);
    return max(core * 1.3, beam * 2.0);
}

// Extra shading inside the shape: the spotlight has a hot centre and weakens with distance
float interiorShade(vec3 q) {
    int shape = shapeId();
    if (shape == FIRE) {
        // Hottest where it burns, gone by the time it reaches the tip
        float up = clamp(q.y / coneHeight(FIRE, max(lightRadius(), 0.001)), 0.0, 1.0);
        return mix(1.8, 0.0, pow(up, 0.7));
    }
    if (shape == PLASMA) {
        return 1.3;
    }
    if (shape == FLARE) {
        // Bright core, dimmer spikes
        return mix(1.7, 0.6, clamp(length(q) / (max(lightRadius(), 0.001) * 1.3), 0.0, 1.0));
    }
    if (shape == COMET) {
        // The head burns, the tail fades out behind it
        return mix(1.8, 0.15, clamp(q.y / coneHeight(COMET, max(lightRadius(), 0.001)), 0.0, 1.0));
    }
    if (shape == MIST) {
        return 0.8;
    }
    if (shape == LASER_DOOR) {
        return 1.25;
    }
    if (shape != SPOTLIGHT) {
        return 1.0;
    }
    float r = max(lightRadius(), 0.001);
    float height = coneHeight(shape, r);
    float t = clamp(q.y / height, 0.0, 1.0);
    float width = max(height * coneSpread() * t, 1.0e-3);
    float across = length(q.xz) / width;
    return mix(1.35, 0.75, clamp(across * across, 0.0, 1.0)) * mix(1.0, 0.5, t);
}

float falloff(float x) {
    x = clamp(x, 0.0, 1.0);
    float s = 1.0 - x * x;
    return s * s;
}

// Cones and spotlights blur more the further the light has travelled
float blurAt(vec3 q) {
    int shape = shapeId();
    if (shape == FIRE) {
        // Crisp where it burns, smoke-soft at the tip
        float up = clamp(q.y / coneHeight(FIRE, max(lightRadius(), 0.001)), 0.0, 1.0);
        return blurDistance() * (0.45 + 2.4 * up * up);
    }
    if (shape == COMET) {
        float up = clamp(q.y / coneHeight(COMET, max(lightRadius(), 0.001)), 0.0, 1.0);
        return blurDistance() * (0.4 + 2.6 * up * up);
    }
    if (shape != CONE && shape != SPOTLIGHT) {
        return blurDistance();
    }
    float t = q.y / coneHeight(shape, max(lightRadius(), 0.001));
    return blurDistance() * clamp(0.25 + t, 0.25, 1.25);
}

// How lit q is: 1 inside the shape, blurring to 0 over `distance` past it (a hard edge when the distance is 0)
float coverageAt(vec3 q, float d) {
    if (d <= 0.0) {
        return 1.0;
    }
    float blur = blurAt(q);
    if (blur <= 0.0) {
        return 1.0 - smoothstep(0.0, 0.03, d);
    }
    return falloff(d / blur);
}

float coverage(float d) {
    if (d <= 0.0) {
        return 1.0;
    }
    float blur = blurDistance();
    if (blur <= 0.0) {
        return 1.0 - smoothstep(0.0, 0.03, d);
    }
    return falloff(d / blur);
}

float interleavedNoise(vec2 position) {
    return fract(52.9829189 * fract(dot(position, vec2(0.06711056, 0.00583715))));
}

// How much a light darkens: dark colors darken what they light (black is pure darkness)
float darkening(float amount) {
    float intensity = LightColor.w;
    if (intensity < 0.0) {
        return clamp(amount * -intensity, 0.0, 1.0);
    }
    float brightness = max(LightColor.r, max(LightColor.g, LightColor.b));
    return clamp(amount * (1.0 - brightness) * min(intensity, 1.0), 0.0, 1.0);
}

// ── Solid blocks between a point and the light (lights that do not pass through blocks) ──

uniform sampler3D Occupancy;
// 1: this light is stopped by solid blocks (Occupancy holds the blocks around it)
uniform int Occluded;
// Corner of the block grid (relative to the camera) and its size in blocks
uniform vec3 OccupancyOrigin;
uniform vec3 OccupancySize;

// Cells are half blocks: the block they belong to
ivec3 blockOf(ivec3 cell) {
    return ivec3(floor(vec3(cell) * 0.5));
}

// Whether this half block stops light (its bit in the block's texel; 255 is the whole block)
bool solidCell(ivec3 cell) {
    if (any(lessThan(cell, ivec3(0))) || any(greaterThanEqual(vec3(cell), OccupancySize * 2.0))) {
        return false;
    }
    ivec3 block = cell / 2;
    float stored = texelFetch(Occupancy, block, 0).r;
    if (stored <= 0.0) {
        return false;
    }
    int mask = int(stored * 255.0 + 0.5);
    if (mask == 255) {
        return true;
    }
    ivec3 inner = cell - block * 2;
    return (mask & (1 << (inner.x + inner.y * 2 + inner.z * 4))) != 0;
}

// 1 when nothing solid lies between `from` and `to` (camera-relative), else 0. Walks half a block at a time, so a
// slab or a stair casts the shadow of the part that is really there; the blocks holding the two ends do not count
// (a light inside a glowing block still shines out of it).
float blockVisibility(vec3 from, vec3 to, int maxCells) {
    if (Occluded == 0) {
        return 1.0;
    }
    vec3 a = (from - OccupancyOrigin) * 2.0;
    vec3 b = (to - OccupancyOrigin) * 2.0;
    vec3 delta = b - a;
    float total = length(delta);
    if (total < 1.0e-4) {
        return 1.0;
    }
    vec3 dir = delta / total;
    ivec3 cell = ivec3(floor(a));
    ivec3 startBlock = blockOf(cell);
    ivec3 lastBlock = blockOf(ivec3(floor(b)));
    ivec3 stepDir = ivec3(sign(dir));
    vec3 tDelta = 1.0 / max(abs(dir), vec3(1.0e-6));
    vec3 boundary = vec3(cell) + max(vec3(stepDir), vec3(0.0));
    vec3 tMax = abs(boundary - a) * tDelta;
    tMax = mix(tMax, vec3(1.0e9), equal(stepDir, ivec3(0)));
    for (int i = 0; i < maxCells; i++) {
        float t;
        if (tMax.x < tMax.y && tMax.x < tMax.z) {
            t = tMax.x;
            tMax.x += tDelta.x;
            cell.x += stepDir.x;
        } else if (tMax.y < tMax.z) {
            t = tMax.y;
            tMax.y += tDelta.y;
            cell.y += stepDir.y;
        } else {
            t = tMax.z;
            tMax.z += tDelta.z;
            cell.z += stepDir.z;
        }
        if (t >= total) {
            return 1.0;
        }
        ivec3 block = blockOf(cell);
        if (block == lastBlock) {
            return 1.0;
        }
        if (block != startBlock && solidCell(cell)) {
            return 0.0;
        }
    }
    return 1.0;
}
