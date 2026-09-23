// CustomLights: puts the half resolution lighting on the frame.
// that are at a similar depth (so nothing bleeds over object edges), multiplies the surface light (already
// how dark the game made that place) by the pixel's color and adds the atmosphere on top.
// Writes the final color of lit pixels (no blending); unlit pixels are left alone.

uniform sampler2D Lighting;
uniform sampler2D Atmosphere;
uniform sampler2D SceneColor;
// The game's own light where the camera is (0..1): a smooth stand-in for how lit the area already is
uniform float AreaLight;
uniform sampler2D WorldDepth;
uniform sampler2D HandDepth;
uniform int HasHand;
uniform int HasAtmosphere;
// Size of the half resolution lighting and G-buffer, and of the atmosphere target (half or quarter resolution)
uniform vec2 TargetSize;
uniform vec2 HazeSize;

out vec4 fragColor;

bool isEmpty(vec4 value) {
    return value.a <= 0.0005 && max(value.r, max(value.g, value.b)) <= 0.0005;
}

// Depth at a point of the G-buffer (the sky is far away)
float lowDepth(vec2 uv) {
    float storedDepth = texture(GBuffer, uv).a;
    return storedDepth == 0.0 ? 1.0e5 : abs(storedDepth);
}

// Full resolution depth of this pixel
float pixelDepth(vec2 uv) {
    float depth;
    if (HasHand == 1 && farness(texture(HandDepth, uv).r) < 1.0) {
        return -viewPosition(HandDepth, InvHandProjection, uv, depth).z;
    }
    vec3 view = viewPosition(WorldDepth, InvWorldProjection, uv, depth);
    return depth >= 1.0 ? 1.0e5 : -view.z;
}

float peakOf(vec3 color) {
    return max(max(color.r, color.g), color.b);
}

// Depth-aware blend of the 4x4 low resolution texels around this pixel
vec4 upsample(sampler2D source, vec2 size, float depth, bool sharp, out bool empty) {
    if (all(greaterThanEqual(size, ScreenSize - 0.5))) {
        // Full resolution: every pixel has its own value, nothing to blend
        vec4 own = texelFetch(source, ivec2(gl_FragCoord.xy), 0);
        empty = isEmpty(own);
        return own;
    }
    vec2 coord = gl_FragCoord.xy * (size / ScreenSize) - 0.5;
    ivec2 base = ivec2(floor(coord));
    vec2 fraction = coord - vec2(base);
    ivec2 limit = ivec2(size) - 1;
    vec4 sum = vec4(0.0);
    float weights = 0.0;
    vec4 nearest = vec4(0.0);
    float nearestDifference = 1.0e9;
    for (int y = -1; y <= 2; y++) {
        for (int x = -1; x <= 2; x++) {
            vec2 offset = vec2(x, y) - fraction;
            float spatial = max(0.0, 1.0 - length(offset) / 1.9);
            if (spatial <= 0.0) {
                continue;
            }
            ivec2 texel = clamp(base + ivec2(x, y), ivec2(0), limit);
            vec4 value = texelFetch(source, texel, 0);
            float difference = abs(lowDepth((vec2(texel) + 0.5) / size) - depth);
            float slack = sharp ? 0.01 + depth * depth * 0.0002 : 0.12 + depth * depth * 0.0025;
            float similar = 1.0 / (1.0 + difference * difference / slack);
            float weight = spatial * similar;
            sum += value * weight;
            weights += weight;
            if (difference < nearestDifference) {
                nearestDifference = difference;
                nearest = value;
            }
        }
    }
    // Nothing near enough in depth: leave the pixel alone instead of borrowing light from behind an edge
    vec4 result = weights > 0.05 ? sum / weights : (!sharp || nearestDifference < max(0.25, depth * 0.05) ? nearest : vec4(0.0));
    empty = isEmpty(result);
    return result;
}

// Whether the low resolution texels around this pixel hold something (one filtered read of the 2x2 nearest)
bool nearbyLight(sampler2D source) {
    return !isEmpty(texture(source, gl_FragCoord.xy / ScreenSize) * 4.0);
}

void main() {
    // Most of the screen has no light at all: stop before any real work
    bool lit = nearbyLight(Lighting);
    bool hazy = HasAtmosphere == 1 && nearbyLight(Atmosphere);
    if (!lit && !hazy) {
        discard;
    }
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = pixelDepth(uv);
    bool noLight = true;
    bool noHaze = true;
    vec4 light = lit ? upsample(Lighting, TargetSize, depth, true, noLight) : vec4(0.0);
    vec4 haze = hazy ? upsample(Atmosphere, HazeSize, depth, false, noHaze) : vec4(0.0);
    if (noLight && noHaze) {
        discard;
    }

    vec3 smoothArriving = vec3(0.0);
    vec3 surface = vec3(0.0);
    if (!noLight && peakOf(light.rgb) > 0.0005 && depth < 1.0e4) {
        // Several strong lights together ease off instead of burning to white (a single light is left as it is)
        vec3 arriving = max(light.rgb, 0.0);
        arriving *= 1.15 / (1.0 + peakOf(arriving) * 0.15);
        smoothArriving = arriving;
        surface = texture(SceneColor, uv).rgb * arriving;
    }
    // Bright haze saturates softly and keeps its color instead of burning to white
    vec3 air = max(haze.rgb, 0.0);
    air /= 1.0 + peakOf(air) * 0.6;
    float hazeAlpha = clamp(haze.a, 0.0, 0.95);
    float lightAlpha = clamp(light.a, 0.0, 1.0);
    float darkness = 1.0 - (1.0 - hazeAlpha) * (1.0 - lightAlpha);
    vec3 scene = texture(SceneColor, uv).rgb;
    vec3 result = scene * (1.0 - darkness) + air + (1.0 - hazeAlpha) * surface;
    // Strong light rolls off softly towards white.
    // haze and the area's own light, never the pixel), so every texture pixel is scaled the same way and keeps its
    // contrast.
    // average surface under it is about 0.6 of its brightness times the area's light
    float areaBefore = AreaLight * 0.6;
    float area = areaBefore * (1.0 - darkness) + peakOf(air) + (1.0 - hazeAlpha) * 0.6 * peakOf(smoothArriving) * max(AreaLight, 1.0 / 6.0);
    float start = max(0.75, areaBefore);
    if (area > start) {
        float room = max(1.0 - start, 1.0e-3);
        result *= (start + room * (1.0 - exp(-(area - start) / room))) / area;
    }
    // Pixels still too bright ease into white softly (keeping their hue and some of their texture) instead of
    // (starting at the pixel's own brightness, so where the light fades out nothing changes)
    float peak = peakOf(result);
    float shoulder = min(max(0.8, peakOf(scene)), 0.999);
    if (peak > shoulder) {
        float room = 1.0 - shoulder;
        result *= (shoulder + room * (1.0 - exp(-(peak - shoulder) / room))) / peak;
    }
    fragColor = vec4(result, 1.0);
}
