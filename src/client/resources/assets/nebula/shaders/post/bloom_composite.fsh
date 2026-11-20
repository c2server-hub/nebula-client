#version 330

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    vec2 Direction; // x = luminance threshold
    float Radius;   // reused as bloom strength
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float k = smoothstep(Direction.x, 1.0, l);
    fragColor = vec4(c * k * Radius, 1.0);
}
