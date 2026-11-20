#version 330

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    vec2 Direction; // unused
    float Radius;   // brightness threshold
};

in vec2 texCoord;
out vec4 fragColor;

// Soft-knee bright pass (Unreal-style): pixels below the threshold vanish smoothly,
// bright pixels keep their colour so the bloom composite stays lively instead of washed out.
void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float t = max(Radius, 0.02);
    float knee = t * 0.5 + 1e-4;
    float soft = clamp(l - t + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee);
    float contribution = max(soft, l - t) / max(l, 1e-4);
    fragColor = vec4(c * contribution, 1.0);
}
