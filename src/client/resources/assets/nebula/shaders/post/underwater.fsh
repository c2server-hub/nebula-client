#version 330

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    vec2 Direction; // x = time (s), y = strength
    float Radius;   // unused
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float t = Direction.x;
    float s = Direction.y;
    vec2 uv = texCoord;
    vec2 off = vec2(
        sin(uv.y * 22.0 + t * 1.9) + sin(uv.y * 9.0 - t * 1.3) * 0.6,
        cos(uv.x * 18.0 + t * 1.6) + cos(uv.x * 7.0 + t * 1.1) * 0.6
    ) * 0.0022 * s;
    // Keep the screen edges stable so no garbage is sampled outside the frame.
    vec2 edge = smoothstep(vec2(0.0), vec2(0.04), uv) * smoothstep(vec2(0.0), vec2(0.04), 1.0 - uv);
    vec3 c = texture(InSampler, clamp(uv + off * edge.x * edge.y, vec2(0.001), vec2(0.999))).rgb;
    fragColor = vec4(c, 1.0);
}
