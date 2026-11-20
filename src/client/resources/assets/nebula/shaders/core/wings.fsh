#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// One energy feather blade. UV: x = -1 at the wing root, +1 at the tip; y = across the blade.
// ModelOffset: x = time (s), y = flow speed, z = edge glow.

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

void main() {
    float along = vUv.x;
    float across = vUv.y;

    // Blade silhouette: widest near the shoulder, tapering to a glowing tip.
    float width = mix(0.55, 0.95, smoothstep(-1.0, -0.2, along));
    width = mix(width, 0.18, smoothstep(0.1, 1.0, along));
    float d = abs(across) - width;
    float aa = fwidth(across) * 1.5 + 1e-4;
    float shape = 1.0 - smoothstep(-aa, aa, d);
    if (shape <= 0.002) {
        discard;
    }

    // Energy flowing from the body towards the wing tip.
    float flow = 0.62 + 0.38 * sin(along * 7.5 - ModelOffset.x * ModelOffset.y * 4.0);
    float spine = exp(-across * across * 14.0) * (1.0 - 0.4 * smoothstep(-0.4, 0.9, along));
    float edge = exp(-(d * d) / 0.004) * ModelOffset.z;
    float tip = smoothstep(1.0, 0.72, along);
    float root = smoothstep(-1.0, -0.82, along);

    float a = shape * (0.22 + 0.4 * flow) * tip * root * vColor.a;
    vec3 col = vColor.rgb * (0.75 + 0.35 * flow);
    col = mix(col, vec3(1.0), spine * 0.5);
    col += vColor.rgb * edge * 0.8;
    fragColor = vec4(col * a, a) * ColorModulator;
}
