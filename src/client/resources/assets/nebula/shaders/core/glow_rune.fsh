#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Procedural magic circle drawn on a flat quad (UV spans [-1, 1]).
// ModelOffset.x = rotation (radians), ModelOffset.y = line thickness, ModelOffset.z = rune / star strength.

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

const float PI = 3.14159265;

float ringLine(float r, float radius, float w) {
    float d = r - radius;
    return exp(-(d * d) / (w * w));
}

void main() {
    float r = length(vUv);
    if (r >= 1.0) {
        discard;
    }
    float w = max(ModelOffset.y, 1e-3);
    float rot = ModelOffset.x;
    float ang = atan(vUv.y, vUv.x);

    float outer = ringLine(r, 0.86, w);
    float inner = ringLine(r, 0.62, w * 0.8);
    float glow = exp(-((r - 0.86) * (r - 0.86)) / (w * w * 16.0)) * 0.4;

    // Rotating rune marks in the band between the two rings.
    float band = smoothstep(0.66, 0.70, r) * smoothstep(0.82, 0.78, r);
    float seg = fract((ang + rot) * 12.0 / (2.0 * PI));
    float ticks = band * (smoothstep(0.0, 0.08, seg) * smoothstep(0.35, 0.27, seg)
        + 0.6 * smoothstep(0.55, 0.60, seg) * smoothstep(0.70, 0.65, seg));

    // Counter-rotating hexagram: six chords at distance 0.31 inscribed in the inner ring.
    float star = 0.0;
    for (int i = 0; i < 3; i++) {
        float la = -rot * 0.6 + float(i) * 2.0 * PI / 3.0;
        vec2 n = vec2(cos(la), sin(la));
        float p = dot(vUv, n);
        float d1 = p - 0.31;
        float d2 = p + 0.31;
        star += exp(-(d1 * d1) / (w * w * 0.5)) + exp(-(d2 * d2) / (w * w * 0.5));
    }
    star *= smoothstep(0.63, 0.60, r);

    float fill = 0.12 * r * r * step(r, 0.86);
    float a = (outer + inner * 0.8 + glow + fill + (ticks + star * 0.55) * ModelOffset.z) * smoothstep(1.0, 0.92, r) * vColor.a;
    vec3 col = mix(vColor.rgb, vec3(1.0), outer * 0.4);
    fragColor = vec4(col * a, a) * ColorModulator;
}
