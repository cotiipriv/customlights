// CustomLights: light scattered by the air (half resolution, one draw per light, added into a float target).
// rgb: light, alpha: darkness.

uniform vec2 TargetSize;
// Samples along each ray for this light (set per light from its size on screen and how many lights there are)
uniform int AtmosphereSteps;
uniform float FarDistance;

out vec4 fragColor;

const float SCATTER = 0.45;
const int MAX_STEPS = 64;

// Point lights: glow that fades with the square of the distance, integrated exactly along the ray (no noise, no
// solid ball).
float sphereGlow(vec3 o, vec3 d, float from, float to) {
    float r = lightRadius();
    float end = r + max(blurDistance(), r) + 0.5;
    float s = r * 0.7 + blurDistance() * 0.25 + 0.05;
    float a = dot(d, d);
    float b = dot(o, d);
    float c = dot(o, o);
    float h = b * b - a * (c - end * end);
    if (h <= 0.0) {
        return 0.0;
    }
    h = sqrt(h);
    float t0 = max((-b - h) / a, from);
    float t1 = min((-b + h) / a, to);
    if (t1 <= t0) {
        return 0.0;
    }
    float k = c + s * s;
    float root = sqrt(max(a * k - b * b, 1.0e-8));
    float integral = s * s / root * (atan((a * t1 + b) / root) - atan((a * t0 + b) / root));
    float edge = s * s / (s * s + end * end);
    return max(integral - edge * (t1 - t0), 0.0) / s * 0.9;
}

// Narrows [t0, t1] to where the ray (q space) is inside the cylinder of `radius` around Y between y0 and y1
bool clipToCylinder(vec3 o, vec3 d, float radius, float y0, float y1, inout float t0, inout float t1) {
    float a = d.x * d.x + d.z * d.z;
    float b = o.x * d.x + o.z * d.z;
    float c = o.x * o.x + o.z * o.z - radius * radius;
    if (a < 1.0e-8) {
        if (c > 0.0) {
            return false;
        }
    } else {
        float h = b * b - a * c;
        if (h <= 0.0) {
            return false;
        }
        h = sqrt(h);
        t0 = max(t0, (-b - h) / a);
        t1 = min(t1, (-b + h) / a);
    }
    if (abs(d.y) < 1.0e-6) {
        if (o.y < y0 || o.y > y1) {
            return false;
        }
    } else {
        float s0 = (y0 - o.y) / d.y;
        float s1 = (y1 - o.y) / d.y;
        t0 = max(t0, min(s0, s1));
        t1 = min(t1, max(s0, s1));
    }
    return t1 > t0;
}

// Brighter when looking back along the light
float forwardGlow(vec3 world, vec3 rayDir, float power) {
    vec3 fromLight = normalize(world - LightPos.xyz + 1.0e-4);
    return pow(max(0.0, -dot(rayDir, fromLight)), power);
}

// The spotlight: a beam that widens, softens and fades along its length, brightest in the middle
float spotlightDensity(vec3 q, vec3 world, vec3 rayDir) {
    float r = max(lightRadius(), 0.001);
    float height = coneHeight(SPOTLIGHT, r);
    float t = q.y / height;
    if (t <= 0.0 || t > 1.4) {
        return 0.0;
    }
    float width = height * coneSpread() * t;
    float rho = length(q.xz);
    float soft = max(width * 0.35, blurDistance() * t * 0.6 + 0.04);
    float beam = 1.0 - smoothstep(width - soft, width + soft, rho);
    float along = smoothstep(0.0, 0.08, t) * (1.0 - smoothstep(0.55, 1.35, t)) / (1.0 + 2.5 * t);
    float hot = mix(1.3, 0.6, clamp(rho / max(width, 1.0e-3), 0.0, 1.0));
    return beam * along * hot * (0.7 + 0.35 * forwardGlow(world, rayDir, 8.0));
}

// Density of the lit air at q (q space); `rayDir` and `world` give the viewing angle
float density(vec3 q, vec3 world, vec3 rayDir) {
    int shape = shapeId();
    if (shape == SPOTLIGHT) {
        return spotlightDensity(q, world, rayDir);
    }
    if (shape == FLASHLIGHT) {
        // A faint beam in the air, brighter when looking along it
        return flashlightLight(q) * 0.22 * (0.6 + 0.6 * forwardGlow(world, rayDir, 6.0));
    }
    if (shape == SIREN) {
        return sirenLight(q) * 0.3 * (0.7 + 0.5 * forwardGlow(world, rayDir, 6.0));
    }
    vec3 toLightQ;
    float edge = shapeDistance(q, toLightQ);
    float r = max(lightRadius(), 0.001);
    if (shape == LASER_DOOR) {
        // Each bar is a soft line of light rather than a hard thread: a thread that thin breaks up into blocks when
        // the haze is sampled, a soft one resamples cleanly
        float softDoor = max(blurDistance(), r * 0.075);
        float dens = edge <= 0.0 ? 1.0 : falloff(edge / softDoor);
        // The barest sheet over the doorway, enough to tell there is a screen there without filling the gaps
        float doorH = coneHeight(LASER_DOOR, r);
        float panel = max(max(abs(q.x) - r, max(q.y - doorH, -q.y)), abs(q.z) - max(r * 0.05, 0.04));
        float screen = panel <= 0.0 ? 1.0 : falloff(panel / max(r * 0.1, 0.05));
        return dens * 2.8 + screen * 0.15;
    }
    if (shape == FIRE) {
        // Fire in the air is thick and tight where it burns and thin, soft smoke by the tip
        float up = clamp(q.y / coneHeight(FIRE, r), 0.0, 1.0);
        float softFire = max(blurDistance() * (0.5 + 2.2 * up * up), 0.08);
        float dens = edge <= 0.0 ? 1.0 : falloff(edge / softFire);
        return dens * mix(1.15, 0.05, pow(up, 0.7)) * 0.6;
    }
    bool thin = shape == PAD || shape == STAR || shape == CROSS || shape == TRIANGLE || shape == RING || shape == PORTAL || shape == AURORA
            || shape == SINGULARITY || shape == RUNE || shape == LASER_DOOR;
    // Flat shapes only glow close to their surface, volumes glow through their blur
    float soft = thin ? max(blurDistance() * 0.5, 0.15) : max(blurDistance(), r * 0.35 + 0.1);
    float amount = edge <= 0.0 ? 1.0 : falloff(edge / soft);
    if (amount <= 0.0) {
        return 0.0;
    }
    if (shape == CONE) {
        float along = clamp(q.y / coneHeight(shape, r), 0.0, 1.0);
        amount *= smoothstep(0.0, 0.1, along) * mix(1.0, 0.3, along);
        amount *= 0.7 + 0.35 * forwardGlow(world, rayDir, 8.0);
    } else if (shape == BEAM || shape == INVERTED_CONE) {
        float along = clamp(q.y / coneHeight(shape, r), 0.0, 1.0);
        amount *= mix(1.0, 0.45, along);
        amount *= 0.75 + 0.5 * pow(max(0.0, -dot(rayDir, LightBasis[1])), 4.0);
    } else if (shape != BOX) {
        amount *= 0.35;
    }
    return amount;
}

void main() {
    vec2 uv = gl_FragCoord.xy / TargetSize;
    bool hand;
    bool sky;
    vec3 view = scenePosition(uv, hand, sky);
    vec3 world = toWorld(view, hand);
    float rayLength = sky ? FarDistance : length(world);
    vec3 rayDir = world / max(length(world), 1.0e-4);

    vec3 center = LightBound.xyz;
    float b = dot(center, rayDir);
    float h = b * b - (dot(center, center) - LightBound.w * LightBound.w);
    if (h <= 0.0) {
        discard;
    }
    h = sqrt(h);
    float start = max(b - h, 0.0);
    float end = min(b + h, rayLength);
    if (end <= start) {
        discard;
    }

    int shape = shapeId();
    float r = max(lightRadius(), 0.001);
    vec3 o = toShape(-LightPos.xyz);
    vec3 d = directionToShape(rayDir);
    float scatter;
    bool swept = isSwept();
    if (shape == SPHERE && !swept) {
        if (Occluded == 1) {
            // The glow in pieces, each lit only when no block stands between it and the light
            int pieces = clamp(AtmosphereSteps, 4, 24);
            float piece = (end - start) / float(pieces);
            float jitter = interleavedNoise(gl_FragCoord.xy);
            scatter = 0.0;
            for (int i = 0; i < pieces; i++) {
                float t0 = start + float(i) * piece;
                float glow = sphereGlow(o, d, t0, t0 + piece);
                if (glow > 0.0) {
                    scatter += glow * blockVisibility(rayDir * (t0 + piece * jitter), LightPos.xyz, 96);
                }
            }
        } else {
            scatter = sphereGlow(o, d, start, end);
        }
    } else {
        // Long shapes: only march where the ray crosses their volume
        if (!swept && (shape == CONE || shape == SPOTLIGHT || shape == INVERTED_CONE || shape == BEAM || shape == FLASHLIGHT)) {
            float margin = shape == FLASHLIGHT ? 0.1 : max(blurDistance(), r * 0.35 + 0.1);
            float height = coneHeight(shape, r) * (shape == SPOTLIGHT ? 1.4 : 1.0);
            float width = shape == BEAM ? r : height * coneSpread() * (shape == FLASHLIGHT ? 1.9 : 1.0);
            if (!clipToCylinder(o, d, width + margin, -margin, height + margin, start, end)) {
                discard;
            }
        }
        // Samples follow the shape's size, so thin and stretched lights stay smooth
        float minStretch = min(abs(LightStretch.x), abs(LightStretch.z));
        float spacing = clamp((r * 0.5 + blurDistance() * 0.3) * minStretch * 0.5, 0.06, 4.0);
        int budget = clamp(AtmosphereSteps, 4, MAX_STEPS);
        int steps = int(clamp(ceil((end - start) / spacing), float(max(budget / 2, 4)), float(budget)));
        float stepLength = (end - start) / float(steps);
        float jitter = interleavedNoise(gl_FragCoord.xy);
        float sum = 0.0;
        for (int i = 0; i < MAX_STEPS; i++) {
            if (i >= steps) {
                break;
            }
            float t = start + (float(i) + jitter) * stepLength;
            vec3 q = swept ? sweptShape(rayDir * t - LightPos.xyz) : o + d * t;
            float amount = density(q, rayDir * t, rayDir);
            if (amount > 0.0 && Occluded == 1) {
                amount *= blockVisibility(rayDir * t, LightPos.xyz, 96);
            }
            sum += amount;
        }
        scatter = sum * stepLength * SCATTER;
    }
    scatter *= LightParams.y;
    // Luminosity: the light also lands on what it reaches, so a dark floor takes its colour instead of only a tint
    float glow = 0.0;
    float core = 0.0;
    if (Luminosity > 0.0 && !sky) {
        vec3 surfaceQ = swept ? sweptShape(world - LightPos.xyz) : toShape(world - LightPos.xyz);
        float lit;
        if (shape == FLASHLIGHT) {
            lit = flashlightLight(surfaceQ);
        } else if (shape == SIREN) {
            lit = sirenLight(surfaceQ);
        } else {
            vec3 toLightQ;
            float edge = shapeDistance(surfaceQ, toLightQ);
            lit = coverageAt(surfaceQ, edge) * interiorShade(surfaceQ);
        }
        if (lit > 0.0 && Occluded == 1) {
            lit *= blockVisibility(world + sceneNormal(uv) * 0.08, LightPos.xyz, 192);
        }
        glow = max(lit, 0.0) * Luminosity * 1.1;
        // Towards white in the middle of the light, back to its own colour further out
        float reach = max(lightRadius(), 0.001) * (0.45 + 0.16 * Luminosity) + blurDistance() * 0.25;
        core = (1.0 - smoothstep(reach * 0.55, reach, length(surfaceQ))) * clamp(Luminosity * 0.3, 0.0, 1.0);
    }
    if (scatter + glow <= 1.0e-4) {
        discard;
    }
    vec3 added = mix(LightColor.rgb, vec3(1.0), core) * ((scatter + glow) * max(LightColor.w, 0.0));
    fragColor = vec4(added, darkening(1.0 - exp(-(scatter + glow * 0.6) * 1.5)));
}
