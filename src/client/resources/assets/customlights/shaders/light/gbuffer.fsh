// CustomLights: once per frame at half resolution, the visible surfaces' normal and depth, so every light reads
// with one lookup.
// rgb: world normal, a: view depth (negative for the first person hand, 0 for the sky).

uniform sampler2D WorldDepth;
uniform sampler2D HandDepth;
uniform int HasHand;
// Size of the (half resolution) G-buffer
uniform vec2 TargetSize;
// How many full resolution pixels one sample covers on each side
uniform int Divisor;

out vec4 fragColor;

// Normal from the depth buffer, taking on each axis the neighbour closest in depth so edges do not smear.
// One pixel wide details (both neighbours far away) face the camera instead of getting a random normal.
vec3 viewNormal(sampler2D depthTexture, mat4 inverseProjection, vec2 uv, vec3 center) {
    vec2 texel = 1.0 / ScreenSize;
    float ignored;
    vec3 right = viewPosition(depthTexture, inverseProjection, uv + vec2(texel.x, 0.0), ignored) - center;
    vec3 left = center - viewPosition(depthTexture, inverseProjection, uv - vec2(texel.x, 0.0), ignored);
    vec3 up = viewPosition(depthTexture, inverseProjection, uv + vec2(0.0, texel.y), ignored) - center;
    vec3 down = center - viewPosition(depthTexture, inverseProjection, uv - vec2(0.0, texel.y), ignored);
    vec3 dx = abs(right.z) < abs(left.z) ? right : left;
    vec3 dy = abs(up.z) < abs(down.z) ? up : down;
    float limit = abs(center.z) * 0.08 + 0.02;
    float stepX = max(abs(dx.x), abs(dx.y));
    float stepY = max(abs(dy.x), abs(dy.y));
    if (abs(dx.z) > limit + stepX * 25.0 || abs(dy.z) > limit + stepY * 25.0) {
        return normalize(-center);
    }
    vec3 normal = normalize(cross(dx, dy));
    return dot(normal, center) > 0.0 ? -normal : normal;
}

void main() {
    // Of the full resolution pixels this sample stands for, the one closest to the camera (the hand first): thin
    // details in front (held items, model pixels, edges) always get a sample, so they never borrow the light of
    // what is behind them
    ivec2 limit = ivec2(ScreenSize) - 1;
    ivec2 origin = ivec2(floor(gl_FragCoord.xy - 0.5)) * Divisor;
    int stride = max(Divisor / 2, 1);
    int count = Divisor > 1 ? 2 : 1;
    ivec2 chosen = clamp(origin, ivec2(0), limit);
    float best = 1.0e9;
    for (int y = 0; y < count; y++) {
        for (int x = 0; x < count; x++) {
            ivec2 pixel = clamp(origin + ivec2(x, y) * stride, ivec2(0), limit);
            float handDepth = HasHand == 1 ? farness(texelFetch(HandDepth, pixel, 0).r) : 1.0;
            float score = handDepth < 1.0 ? handDepth - 2.0 : farness(texelFetch(WorldDepth, pixel, 0).r);
            if (score < best) {
                best = score;
                chosen = pixel;
            }
        }
    }
    vec2 uv = (vec2(chosen) + 0.5) / ScreenSize;
    bool hand = best < 0.0;
    float depth;
    vec3 view;
    vec3 normal;
    if (hand) {
        view = viewPosition(HandDepth, InvHandProjection, uv, depth);
        normal = viewNormal(HandDepth, InvHandProjection, uv, view);
    } else {
        view = viewPosition(WorldDepth, InvWorldProjection, uv, depth);
        if (depth >= 1.0) {
            fragColor = vec4(0.0);
            return;
        }
        normal = viewNormal(WorldDepth, InvWorldProjection, uv, view);
    }
    float viewDepth = max(-view.z, 1.0e-3);
    fragColor = vec4(normalize(ViewToWorld * normal), hand ? -viewDepth : viewDepth);
}
