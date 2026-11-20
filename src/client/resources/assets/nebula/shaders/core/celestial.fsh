#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:fog.glsl>

// Custom sun and moon, drawn on the far plane so terrain occludes them correctly.
// The sun direction is the exact vanilla formula: sunDir = (-sin(2*pi*f), cos(2*pi*f), 0)
// with f = dayTime fraction - 0.25, so the discs always sit where the vanilla sun is.
//
// TextureMat columns: 0 = sun colour, 1 = moon colour, 2 = ray tint, 3 = (moon size / 30, ray speed, 0, 0).
// ColorModulator: x = sun brightness, y = moon brightness, z = ray strength, w = time (s).
// ModelOffset: x = raw dayTime fraction (noon = 0.25), y = sun disc size (cos of angular radius), z = moon phase 0..1.

in vec3 vRay;
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

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.07 + vec3(9.2, 1.7, 5.1);
        a *= 0.5;
    }
    return v;
}

/** Stable local frame around a celestial direction (handles the zenith degenerate case). */
mat3 localFrame(vec3 dir) {
    vec3 ref = abs(dir.y) < 0.95 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 right = normalize(cross(dir, ref));
    vec3 up = cross(right, dir);
    return mat3(right, up, dir);
}

void main() {
    vec3 d = normalize(vRay);
    float time = ColorModulator.w;

    float f = ModelOffset.x - 0.25;
    float ang = f * 6.2831853;
    vec3 sunDir = vec3(-sin(ang), cos(ang), 0.0);
    vec3 moonDir = -sunDir;

    float cosSun = dot(d, sunDir);
    float cosMoon = dot(d, moonDir);
    float sunDisc = ModelOffset.y;
    float moonDisc = cos(radians(TextureMat[3].x * 30.0));
    float aa = 0.0015 + fwidth(cosSun) * 1.5;
    vec3 col = vec3(0.0);
    float alpha = 0.0;

    // ---- Sun: white-hot core, warm corona and slow rotating flare rays. ----
    float sunFade = smoothstep(-0.14, 0.04, sunDir.y) * ColorModulator.x;
    if (sunFade > 0.003 && cosSun > sunDisc - 0.28) {
        float core = smoothstep(sunDisc - aa, sunDisc + aa, cosSun);
        vec3 sunCol = TextureMat[0].rgb;
        vec3 hot = mix(sunCol, vec3(1.0), smoothstep(sunDisc, 0.999, cosSun));
        col += hot * core * 1.6 * sunFade;
        alpha = max(alpha, core * sunFade);

        float halo = exp(-(sunDisc - min(cosSun, sunDisc)) * 26.0);
        col += sunCol * halo * 0.55 * sunFade;
        alpha = max(alpha, halo * 0.5 * sunFade);

        if (ColorModulator.z > 0.003) {
            float rayTime = time * (0.006 + 0.024 * TextureMat[3].y);
            mat3 frame = localFrame(sunDir);
            vec2 local = vec2(dot(d, frame[0]), dot(d, frame[1]));
            float angle = atan(local.y, local.x);
            float rays = fbm(vec3(cos(angle) * 2.4, sin(angle) * 2.4, rayTime)) * 0.6
                       + fbm(vec3(angle * 2.6, rayTime * 1.5, 3.7)) * 0.4;
            rays = pow(max(rays, 0.0), 2.2);
            float rayMask = exp(-(sunDisc - min(cosSun, sunDisc)) * 13.0) * smoothstep(sunDisc - 0.06, sunDisc + 0.01, cosSun);
            col += mix(sunCol, TextureMat[2].rgb, 0.5) * rays * rayMask * ColorModulator.z * sunFade;
            alpha = max(alpha, min(rays, 1.0) * rayMask * ColorModulator.z * 0.55 * sunFade);
        }
    }

    // ---- Moon: phase crescent, crater dimples and a cool rim. ----
    float moonFade = smoothstep(-0.14, 0.04, moonDir.y) * ColorModulator.y;
    if (moonFade > 0.003 && cosMoon > moonDisc - 0.16) {
        float core = smoothstep(moonDisc - aa, moonDisc + aa, cosMoon);
        mat3 frame = localFrame(moonDir);
        vec2 local = vec2(dot(d, frame[0]), dot(d, frame[1]));

        // Phase terminator: phase 0 = full moon, 1 = new moon.
        float phase = clamp(ModelOffset.z, 0.0, 1.0);
        float shade = clamp((local.x + phase * 2.4 - 1.2) / 0.12, 0.0, 1.0);
        float craters = fbm(vec3(local * 14.0, 11.3)) * 0.35 + fbm(vec3(local * 34.0, 4.2)) * 0.2;
        float surface = 1.0 - craters;

        vec3 moonCol = TextureMat[1].rgb;
        vec3 lit = moonCol * (0.75 + 0.25 * surface) * (1.0 - shade * 0.92);
        // Earthshine: the dark side stays barely visible.
        vec3 night = moonCol * 0.06 * shade;
        col += (lit + night) * core * 1.25 * moonFade;
        alpha = max(alpha, core * moonFade);

        float rim = exp(-(moonDisc - min(cosMoon, moonDisc)) * 55.0) * (1.0 - core);
        col += moonCol * rim * 0.35 * moonFade * (1.0 - shade * 0.6);
        alpha = max(alpha, rim * 0.3 * moonFade);
    }

    // Softly blend into the fog near the horizon so the discs set behind the fog like vanilla.
    float fogBlend = exp(-max(d.y, -0.05) * 9.0);
    col = mix(col, FogColor.rgb * alpha, fogBlend * 0.6);

    fragColor = vec4(col, clamp(alpha, 0.0, 1.0));
}
