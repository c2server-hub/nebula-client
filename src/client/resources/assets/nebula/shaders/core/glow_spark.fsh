#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

// Soft emissive point: gaussian halo plus a tight white core.
void main() {
    float r2 = dot(vUv, vUv);
    if (r2 >= 1.0) {
        discard;
    }
    float halo = exp(-r2 * 6.0) * (1.0 - r2);
    float core = exp(-r2 * 45.0);
    float a = (halo + core) * vColor.a;
    vec3 col = (vColor.rgb * halo + vec3(core)) * vColor.a;
    fragColor = vec4(col, a) * ColorModulator;
}
