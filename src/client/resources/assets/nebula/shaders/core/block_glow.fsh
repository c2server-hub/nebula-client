#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Box face with UVs in [-1, 1]. ModelOffset: x = line width (px), y = fill opacity, z = time.

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

void main() {
    // Distance to the face edges in screen pixels, so lines keep a constant width at any distance.
    float dx = (1.0 - abs(vUv.x)) / max(fwidth(vUv.x), 1e-5);
    float dy = (1.0 - abs(vUv.y)) / max(fwidth(vUv.y), 1e-5);
    float d = min(dx, dy);
    float w = ModelOffset.x;
    float line = 1.0 - smoothstep(w * 0.5, w * 0.5 + 1.0, d);
    float glow = exp(-d / (w * 3.0)) * 0.55;
    // Corners shine brighter.
    float corner = exp(-max(dx, dy) / (w * 6.0)) * 0.6;
    float pulse = 0.75 + 0.25 * sin(ModelOffset.z * 3.0);
    float fill = ModelOffset.y * pulse;
    float a = clamp(line + glow + corner + fill, 0.0, 1.5) * vColor.a;
    vec3 col = mix(vColor.rgb, vec3(1.0), line * 0.35 + corner * 0.3);
    fragColor = vec4(col * a, a) * ColorModulator;
}
