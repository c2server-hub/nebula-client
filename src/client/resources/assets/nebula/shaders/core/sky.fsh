#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:fog.glsl>

// TextureMat columns: zenith, horizon, nebula A, nebula B colours.
// ColorModulator: x = opacity, y = star density, z = nebula amount, w = time (s).
// ModelOffset: x = fog match, y = star rotation speed, z = shooting stars (0/1).

in vec3 vRay;
out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
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

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 5.1);
        a *= 0.5;
    }
    return v;
}

vec3 rotateY(vec3 v, float a) {
    float c = cos(a), s = sin(a);
    return vec3(c * v.x + s * v.z, v.y, -s * v.x + c * v.z);
}

// One cell-hashed star layer; scale controls apparent star size and spacing.
vec3 stars(vec3 d, float scale, float density, float time) {
    vec3 p = d * scale;
    vec3 cell = floor(p);
    vec3 r = hash33(cell);
    if (r.x > density) {
        return vec3(0.0);
    }
    vec3 centre = cell + 0.2 + 0.6 * hash33(cell + 17.0);
    float dist = length(p - centre);
    float px = max(fwidth(p.x) + fwidth(p.y), 1e-4);
    float core = smoothstep(px * 1.6, 0.0, dist);
    float twinkle = 0.65 + 0.35 * sin(time * (1.5 + 3.0 * r.y) + r.z * 40.0);
    vec3 tint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.85, 0.7), r.z);
    return tint * core * twinkle * (0.5 + r.y);
}

float segmentGlow(vec3 d, vec3 a, vec3 b, float width) {
    vec3 ab = b - a;
    float t = clamp(dot(d - a, ab) / dot(ab, ab), 0.0, 1.0);
    float dist = length(d - (a + ab * t));
    return exp(-dist * dist / (width * width)) * t;
}

void main() {
    vec3 d = normalize(vRay);
    float time = ColorModulator.w;
    float h = d.y;

    vec3 zenith = TextureMat[0].rgb;
    vec3 horizon = TextureMat[1].rgb;
    vec3 col = mix(horizon, zenith, pow(clamp(h, 0.0, 1.0), 0.55));
    col = mix(col, zenith * 0.35, clamp(-h * 3.0, 0.0, 1.0));

    // Milky-way-like band of nebula gas.
    vec3 g = normalize(vec3(0.35, 0.62, 0.70));
    float band = exp(-pow(dot(d, g), 2.0) * 4.0);
    vec3 nd = rotateY(d, time * ModelOffset.y * 0.01);
    float n = fbm(nd * 2.2 + vec3(0.0, 0.0, time * 0.004));
    float detail = fbm(nd * 5.5 - vec3(time * 0.006));
    float gas = smoothstep(0.38, 0.95, n * 0.75 + detail * 0.45) * band * ColorModulator.z;
    vec3 nebula = mix(TextureMat[2].rgb, TextureMat[3].rgb, smoothstep(0.3, 0.75, detail));
    col += nebula * gas * 1.3;
    col *= 1.0 - 0.35 * smoothstep(0.55, 0.8, detail) * band * ColorModulator.z; // dark dust lanes

    float above = smoothstep(-0.05, 0.08, h);
    vec3 sd = rotateY(d, time * ModelOffset.y * 0.01);
    vec3 s = stars(sd, 180.0, ColorModulator.y * 0.25, time) + stars(sd, 70.0, ColorModulator.y * 0.06, time) * 1.6;
    col += s * above * (1.0 - 0.5 * gas);

    if (ModelOffset.z > 0.5) {
        for (int k = 0; k < 2; k++) {
            float period = 7.0 + float(k) * 3.0;
            float slot = floor(time / period + float(k) * 0.37);
            float phase = fract(time / period + float(k) * 0.37);
            vec3 r = hash33(vec3(slot, float(k), 3.0));
            vec3 start = normalize(vec3(r.x * 2.0 - 1.0, 0.35 + 0.5 * r.y, r.z * 2.0 - 1.0));
            vec3 dir = normalize(cross(start, vec3(0.3, 1.0, 0.2)));
            float life = smoothstep(0.0, 0.05, phase) * smoothstep(0.35, 0.15, phase);
            vec3 head = normalize(start + dir * phase * 1.2);
            vec3 tail = normalize(start + dir * max(0.0, phase * 1.2 - 0.18));
            col += vec3(0.9, 0.95, 1.0) * segmentGlow(d, tail, head, 0.003) * life * above * 2.0;
        }
    }

    // Blend into the vanilla fog colour near the horizon so distant terrain does not show a seam.
    float fogBlend = ModelOffset.x * exp(-abs(h) * 7.0);
    col = mix(col, FogColor.rgb, fogBlend);

    fragColor = vec4(col, ColorModulator.x);
}
