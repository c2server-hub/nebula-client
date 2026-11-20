#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Pure gaussian blob without a white core: mist, ground light pools, footprints.

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

void main() {
    float r2 = dot(vUv, vUv);
    if (r2 >= 1.0) {
        discard;
    }
    float a = exp(-r2 * 3.5) * (1.0 - r2) * vColor.a;
    fragColor = vec4(vColor.rgb * a, a) * ColorModulator;
}
