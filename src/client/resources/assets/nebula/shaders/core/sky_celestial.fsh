#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:fog.glsl>

// Celestial sky: atmosphere gradient driven by the real sun elevation, sun and moon discs,
// stars that fade with daylight and a lit cloud deck.
// TextureMat[0].rgb = zenith, [1].rgb = horizon, [2].rgb = sun / cloud highlight,
// [3].rgb = cloud shadow. TextureMat[0..2].w = sun direction; [3].w = moon phase (0 = full).
// ColorModulator: x = opacity, y = star density, z = cloud coverage, w = time (s).
// ModelOffset: x = fog match, y = cloud speed, z = cloud detail (octaves).

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
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x),
               mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y);
}

// Fixed-bound loop with an early break: the compiler sees a constant trip count and the
// slider still controls how much detail is paid for.
float fbm2(vec2 p, int octaves) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) {
            break;
        }
        v += a * noise2(p);
        p = mat2(1.63, 1.21, -1.21, 1.63) * p + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

vec3 rotateY(vec3 v, float a) {
    float c = cos(a), s = sin(a);
    return vec3(c * v.x + s * v.z, v.y, -s * v.x + c * v.z);
}

// One cell-hashed star layer. Density is the fraction of occupied cells.
vec3 starLayer(vec3 d, float scale, float density, float time) {
    vec3 p = d * scale;
    vec3 cell = floor(p);
    vec3 r = hash33(cell);
    if (r.x > density) {
        return vec3(0.0);
    }
    vec3 centre = cell + 0.2 + 0.6 * hash33(cell + 17.0);
    float dist = length(p - centre);
    float px = max(fwidth(p.x) + fwidth(p.y), 1e-4);
    float core = smoothstep(px * 1.7, 0.0, dist);
    float twinkle = 0.65 + 0.35 * sin(time * (1.2 + 3.0 * r.y) + r.z * 40.0);
    vec3 tint = mix(vec3(0.72, 0.83, 1.0), vec3(1.0, 0.86, 0.68), r.z);
    return tint * core * twinkle * (0.45 + r.y);
}

// Domain-warped cloud density on a domed projection: keeps puffs readable near the horizon
// instead of stretching a ground plane into radial streaks.
float cloudNoise(vec2 p, int octaves) {
    float warp = fbm2(p * 1.15 + vec2(2.3, 7.1), min(octaves, 4));
    return fbm2(p + vec2(warp * 1.6, warp * 1.1), octaves);
}

void main() {
    vec3 d = normalize(vRay);
    float h = d.y;
    float time = ColorModulator.w;

    vec3 sunDir = normalize(vec3(TextureMat[0].w, TextureMat[1].w, TextureMat[2].w));
    float moonPhase = TextureMat[3].w;

    vec3 zenith = TextureMat[0].rgb;
    vec3 horizon = TextureMat[1].rgb;
    vec3 sunCol = TextureMat[2].rgb;
    vec3 cloudShade = TextureMat[3].rgb;

    float day = smoothstep(-0.16, 0.10, sunDir.y);
    float night = 1.0 - day;
    float twilight = exp(-sunDir.y * sunDir.y * 24.0);
    float sunDot = max(dot(d, sunDir), 0.0);
    float above = clamp(h, 0.0, 1.0);

    // Base atmosphere. Night keeps a hint of the palette but settles toward a cool blue.
    vec3 daySky = mix(horizon, zenith, pow(above, 0.55));
    vec3 paletteNight = mix(horizon, zenith, pow(above, 0.65)) * 0.07;
    vec3 nightSky = mix(paletteNight, vec3(0.007, 0.014, 0.030), 0.50);
    vec3 col = mix(nightSky, daySky, day);

    // Below the horizon: darken progressively instead of showing a flat slab when flying high.
    float below = 1.0 - smoothstep(-0.25, 0.0, h);
    vec3 lowCol = mix(horizon * 0.22, zenith * 0.08, clamp(-h * 2.5, 0.0, 1.0));
    col = mix(col, lowCol * (0.45 + 0.55 * night), below);

    // Warm scattering on the sun side of the horizon.
    float horizonBand = exp(-abs(h) * 7.5);
    col += sunCol * horizonBand * pow(sunDot, 3.0) * (0.08 + 0.55 * twilight) * (0.30 + 0.70 * day);

    // Sun disc and corona.
    float sunVis = smoothstep(-0.06, 0.03, sunDir.y);
    float sunDisc = smoothstep(0.99980, 0.99991, dot(d, sunDir));
    float corona = pow(sunDot, 900.0) * 0.35 + pow(sunDot, 60.0) * 0.06;
    col += sunCol * (sunDisc * 3.5 + corona) * sunVis;

    // Moon: phase 0 is full (no shadow), phase 0.5 is new (shadow centred on the disc).
    vec3 moonDir = -sunDir;
    vec3 moonSide = normalize(cross(moonDir, vec3(0.0, 1.0, 0.0)));
    float moonDot = max(dot(d, moonDir), 0.0);
    float moonDisc = smoothstep(0.99955, 0.99968, moonDot);
    float illumination = 0.5 + 0.5 * cos(moonPhase * 6.2831853);
    float shadowOffset = (1.0 - illumination) * 0.07;
    float moonShadow = smoothstep(0.99955, 0.99968, dot(d, normalize(moonDir + moonSide * shadowOffset)));
    float moon = max(0.0, moonDisc - moonShadow * 0.94 * (1.0 - illumination));
    float moonGlow = pow(moonDot, 900.0) * 0.30 + pow(moonDot, 60.0) * 0.04;
    vec3 moonCol = mix(vec3(0.72, 0.82, 1.0), sunCol, 0.25);
    col += moonCol * (moon * 1.6 + moonGlow) * (1.0 - day * 0.85);

    // Stars.
    vec3 starD = rotateY(d, time * 0.006);
    vec3 stars = starLayer(starD, 190.0, ColorModulator.y * 0.30, time)
               + starLayer(starD, 74.0, ColorModulator.y * 0.07, time) * 1.7;
    col += stars * night * smoothstep(0.03, 0.28, h);

    // Cloud deck on a stylised dome, lit from the sun side.
    if (ColorModulator.z > 0.01 && h > 0.02) {
        float hh = max(h, 0.03);
        vec2 uv = d.xz / (hh + 0.18) * 0.75;
        uv += vec2(time * 0.004 * ModelOffset.y, time * 0.0016 * ModelOffset.y);
        int octaves = int(clamp(ModelOffset.z, 2.0, 6.0));
        float base = cloudNoise(uv, octaves);
        vec2 sunShift = normalize(sunDir.xz + vec2(1e-5, 0.0)) * 0.09;
        float shifted = cloudNoise(uv + sunShift, max(2, octaves - 2));
        float cover = smoothstep(0.55 - ColorModulator.z * 0.38, 0.78 - ColorModulator.z * 0.30, base);
        cover *= smoothstep(0.0, 0.10, ColorModulator.z);
        float light = clamp(0.5 + (base - shifted) * 6.0, 0.0, 1.0);
        vec3 cloudCol = mix(cloudShade, mix(sunCol, vec3(1.0), 0.30), light);
        cloudCol *= 0.20 + 0.80 * day;
        cloudCol += sunCol * pow(sunDot, 24.0) * 0.22;
        cloudCol += moonCol * pow(moonDot, 10.0) * 0.16 * night;
        // Fade into the horizon haze and thin the deck at the zenith so it does not read as a lid.
        float band = smoothstep(0.02, 0.20, h) * (1.0 - 0.35 * smoothstep(0.75, 1.0, h));
        col = mix(col, cloudCol, cover * band * 0.9);
    }

    // Blend into the vanilla fog colour at the horizon to hide the terrain seam.
    float fogBlend = ModelOffset.x * exp(-abs(h) * 7.0);
    col = mix(col, FogColor.rgb, fogBlend);

    fragColor = vec4(col, ColorModulator.x);
}
