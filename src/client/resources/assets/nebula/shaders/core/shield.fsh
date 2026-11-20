#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Fresnel energy bubble around a player. Reuses orb.vsh (unit sphere: vNormal, vViewDir, vObject).
// ColorModulator: rgb = tint, a = overall intensity.
// ModelOffset: x = time (s), y = damage pulse phase (0..1, < 0 = inactive), z = pattern strength.

in vec3 vNormal;
in vec3 vViewDir;
in vec3 vObject;

out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x),
                   mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x),
                   mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

// Hexagonal lattice: 1 on the cell borders, 0 inside (classic hex SDF).
float hexPattern(vec2 p) {
    vec2 r = vec2(1.0, 1.7320508);
    vec2 h = r * 0.5;
    vec2 a = mod(p, r) - h;
    vec2 b = mod(p - h, r) - h;
    vec2 gv = dot(a, a) < dot(b, b) ? a : b;
    gv = abs(gv);
    float d = max(dot(gv, vec2(0.8660254, 0.5)), gv.x); // 0 at the centre, 0.5 at the border
    return smoothstep(0.40, 0.49, d);
}

void main() {
    vec3 n = normalize(vNormal);
    float facing = clamp(dot(n, normalize(vViewDir)), 0.0, 1.0);
    float t = ModelOffset.x;

    // The shell is mostly invisible head-on and lights up towards the rim.
    float rim = pow(1.0 - facing, 2.6);

    // Slow plasma breathing over the surface, plus a faint hex tech pattern.
    float plasma = noise(vObject * 2.2 + vec3(0.0, t * 0.25, t * 0.12)) * 0.6
                 + noise(vObject * 4.5 - vec3(t * 0.2)) * 0.4;
    float hexLines = hexPattern(vObject.xz * 9.0 + vec2(vObject.y * 4.5)) * ModelOffset.z;

    vec3 base = ColorModulator.rgb;
    float intensity = ColorModulator.a;
    vec3 col = base * (rim * 1.15 + plasma * 0.10 + hexLines * (0.12 + 0.18 * rim));
    col += vec3(1.0) * pow(facing, 24.0) * 0.10; // specular kiss where the bubble faces you

    // Impact wave: a bright band sweeping from the centre to the rim after damage.
    float phase = ModelOffset.y;
    if (phase >= 0.0 && phase < 1.0) {
        float band = exp(-pow((facing - (1.0 - phase * 1.35)) * 3.4, 2.0));
        col += mix(base, vec3(1.0), 0.55) * band * (1.0 - phase) * 1.8;
    }

    float a = clamp(max(rim * 0.85 + hexLines * 0.3 + plasma * 0.05, 0.0) * intensity, 0.0, 1.0);
    if (phase >= 0.0 && phase < 1.0) {
        a = clamp(a + (1.0 - phase) * 0.5, 0.0, 1.0);
    }
    if (a <= 0.004) {
        discard;
    }
    fragColor = vec4(col * a, a);
}
