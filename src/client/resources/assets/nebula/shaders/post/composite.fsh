#version 330

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    vec2 Direction;
    float Radius; // reused as bloom strength
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 bloom = texture(InSampler, texCoord).rgb * Radius;
    fragColor = vec4(bloom, 1.0);
}
