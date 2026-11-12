#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 vCorner;
out vec4 fragColor;

void main() {
    float r2 = dot(vCorner, vCorner);
    if (r2 >= 1.0) {
        discard;
    }
    // Gaussian falloff with a smooth zero at the quad edge.
    float a = exp(-r2 * 5.0) * (1.0 - r2);
    vec3 col = ColorModulator.rgb * a * ColorModulator.a;
    fragColor = vec4(col, a);
}
