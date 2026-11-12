#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

void main() {
    float across = 1.0 - abs(vUv.y * 2.0 - 1.0);
    float a = pow(across, 1.6) * (1.0 - vUv.x) * vColor.a;
    // Bright core line in the middle of the ribbon.
    vec3 col = mix(vColor.rgb, vec3(1.0), pow(across, 12.0) * 0.6);
    fragColor = vec4(col * a, a) * ColorModulator;
}
