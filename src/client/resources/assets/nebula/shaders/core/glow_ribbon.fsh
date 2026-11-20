#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Soft glowing ribbon: V (vUv.y) spans the width in [-1, 1], U runs along the ribbon.
// ModelOffset.x = strength of the white-hot centre line.

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

void main() {
    float d = abs(vUv.y);
    if (d >= 1.0) {
        discard;
    }
    float halo = exp(-d * d * 4.0) * (1.0 - d);
    float core = exp(-d * d * 40.0) * ModelOffset.x;
    float a = (halo + core) * vColor.a;
    vec3 col = (vColor.rgb * halo + vec3(core)) * vColor.a;
    fragColor = vec4(col, a) * ColorModulator;
}
