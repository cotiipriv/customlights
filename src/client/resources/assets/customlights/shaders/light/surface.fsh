// CustomLights: light on the visible surfaces (half resolution, one draw per light, clipped to the light's
// Output: rgb is the light arriving (the composite multiplies it by the surface color), alpha darkens (blend
// ONE_MINUS_SRC_ALPHA).

uniform vec2 TargetSize;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / TargetSize;
    bool hand;
    bool sky;
    vec3 view = scenePosition(uv, hand, sky);
    if (sky) {
        discard;
    }
    vec3 world = toWorld(view, hand);
    // Cheap test first: most pixels of the light's screen box are nowhere near it
    vec3 fromBound = world - LightBound.xyz;
    if (dot(fromBound, fromBound) > LightBound.w * LightBound.w) {
        discard;
    }
    vec3 q = sweptShape(world - LightPos.xyz);
    int shape = shapeId();

    vec3 toLightQ;
    float lit;
    float inside = 0.0;
    if (shape == FLASHLIGHT || shape == SIREN) {
        lit = shape == FLASHLIGHT ? flashlightLight(q) : sirenLight(q);
        toLightQ = -normalize(q);
    } else {
        float edge = shapeDistance(q, toLightQ);
        lit = coverageAt(q, edge) * interiorShade(q);
        // Fully inside the shape, then easing out over the blur (a hard switch would draw a rim)
        inside = 1.0 - smoothstep(0.0, max(blurDistance() * 0.6, 0.08), edge);
    }
    if (lit <= 0.001) {
        discard;
    }

    vec3 normal = sceneNormal(uv);
    if (Occluded == 1) {
        // Solid blocks between this surface and the light stop it (starting just off the surface)
        lit *= blockVisibility(world + normal * 0.08, LightPos.xyz, 192);
        if (lit <= 0.001) {
            discard;
        }
    }
    vec3 toLight = normalize(vectorToWorld(toLightQ));
    float facing = dot(normal, toLight);
    float diffuse;
    if (shape == FLASHLIGHT) {
        // A real beam: faces turned away get almost nothing
        diffuse = clamp(facing * 0.85 + 0.15, 0.0, 1.0);
    } else {
        // Wrapped diffuse: faces turned away still catch a little, which suits blocky shapes; inside the shape
        // everything is mostly lit
        diffuse = clamp(facing * 0.6 + 0.4, 0.0, 1.0);
        diffuse = mix(diffuse, 1.0, inside * 0.55);
    }

    // Luminosity: the middle of the light burns brighter and towards white, easing back to its own colour outwards
    vec3 color = LightColor.rgb;
    float boost = 1.0;
    if (Luminosity > 0.0) {
        // The white middle grows with luminosity and holds its brightness before falling off, instead of fading the
        // whole way out
        float reach = max(lightRadius(), 0.001) * (0.45 + 0.16 * Luminosity) + blurDistance() * 0.25;
        float core = 1.0 - smoothstep(reach * 0.55, reach, length(q));
        boost = 1.0 + Luminosity * 2.2 * core;
        color = mix(color, vec3(1.0), clamp(Luminosity * 0.3, 0.0, 1.0) * core);
    }
    vec3 added = color * (lit * diffuse * max(LightColor.w, 0.0) * boost);
    fragColor = vec4(added, darkening(min(lit, 1.0)));
}
