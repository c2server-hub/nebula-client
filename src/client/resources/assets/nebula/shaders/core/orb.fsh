#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// ColorModulator = orb colour (rgb) and emission intensity (a).
// ModelOffset.x = animation time in seconds, ModelOffset.y = per-orb seed.

in vec3 vNormal;
in vec3 vViewDir;
in vec3 vObject;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i + vec3(0, 0, 0)), hash(i + vec3(1, 0, 0)), f.x),
                   mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                   mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void main() {
    float t = ModelOffset.x;
    vec3 n = normalize(vNormal);
    float facing = clamp(dot(n, normalize(vViewDir)), 0.0, 1.0);

    // Animated plasma swirling over the surface.
    vec3 p = vObject * 2.6 + vec3(ModelOffset.y * 7.0);
    float plasma = noise(p + vec3(0.0, t * 0.9, t * 0.45)) * 0.6 + noise(p * 2.1 - vec3(t * 0.7)) * 0.4;

    vec3 base = ColorModulator.rgb;
    float intensity = ColorModulator.a;
    vec3 core = mix(base, vec3(1.0), pow(facing, 3.0) * 0.85);            // white-hot centre
    vec3 rim = base * pow(1.0 - facing, 2.2) * 1.6;                          // fresnel rim
    vec3 col = (core * (0.55 + 0.45 * plasma) + rim) * intensity;
    // Soft tonemap so high intensities saturate gracefully in an 8-bit target.
    col = vec3(1.0) - exp(-col * 1.4);
    fragColor = vec4(col, 1.0);
}
