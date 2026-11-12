#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// ModelOffset.x = ring radius (quad units, quad spans [-1, 1]),
// ModelOffset.y = ring thickness, ModelOffset.z = inner fill strength.

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

void main() {
    float r = length(vUv);
    if (r >= 1.0) {
        discard;
    }
    float radius = ModelOffset.x;
    float w = max(ModelOffset.y, 1e-3);
    float d = abs(r - radius);
    float core = exp(-(d * d) / (w * w));
    float glow = exp(-(d * d) / (w * w * 16.0)) * 0.45;
    float fill = r < radius ? ModelOffset.z * pow(r / radius, 3.0) : 0.0;
    float a = (core + glow + fill) * smoothstep(1.0, 0.9, r) * vColor.a;
    // White-hot centre line on the ring itself.
    vec3 col = mix(vColor.rgb, vec3(1.0), core * 0.45);
    fragColor = vec4(col * a, a) * ColorModulator;
}
