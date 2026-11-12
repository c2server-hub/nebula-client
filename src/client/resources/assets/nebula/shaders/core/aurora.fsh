#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Aurora curtains: the view ray crosses a stack of horizontal layers; on each layer, ridged
// noise traces thin bright folds (the curtains). Layers are brightest at the lower edge and fade
// upwards, which gives the characteristic vertical rays.
// TextureMat columns: low colour, mid colour, top colour.
// ColorModulator: x = intensity (already scaled by visibility), y = speed, z = height, w = time.
// ModelOffset.x = layer count.

in vec3 vRay;
out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y);
}

float fbm2(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * noise2(p);
        p = mat2(1.6, 1.2, -1.2, 1.6) * p;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec3 rd = normalize(vRay);
    if (rd.y <= 0.02) {
        discard;
    }
    float time = ColorModulator.w * ColorModulator.y;
    float layers = max(ModelOffset.x, 8.0);
    float base = ColorModulator.z * 10.0;
    float dither = hash12(gl_FragCoord.xy) / layers;
    vec3 col = vec3(0.0);
    for (float s = 0.0; s < layers; s += 1.0) {
        float k = s / layers + dither;
        float alt = base + k * 9.0;
        float t = alt / rd.y;
        vec2 p = rd.xz * t * 0.045;
        vec2 flow = vec2(time * 0.018, time * 0.007);
        float warp = fbm2(p * 0.6 + flow);
        float ridge = 1.0 - abs(fbm2(p * 1.1 + vec2(warp * 2.4, warp) - flow) * 2.0 - 1.0);
        float curtain = pow(ridge, 9.0);
        // Fine vertical rays along the curtain.
        curtain *= 0.55 + 0.45 * noise2(vec2(p.x * 9.0 + warp * 6.0, time * 0.4));
        float profile = smoothstep(0.0, 0.06, k) * exp(-k * 3.2);
        float distanceFade = exp(-t * 0.004);
        vec3 tint = mix(mix(TextureMat[0].rgb, TextureMat[1].rgb, smoothstep(0.0, 0.35, k)), TextureMat[2].rgb, smoothstep(0.3, 0.9, k));
        col += tint * curtain * profile * distanceFade;
    }
    col *= 9.0 / layers;
    col *= smoothstep(0.02, 0.18, rd.y);
    fragColor = vec4(col * ColorModulator.x, 1.0);
}
