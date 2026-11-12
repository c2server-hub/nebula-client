#version 330

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

// 4 bilinear taps = 16 texel box filter, avoids aliasing when halving resolution.
void main() {
    vec2 t = 1.0 / vec2(textureSize(InSampler, 0));
    vec4 c = texture(InSampler, texCoord + t * vec2(-0.5, -0.5))
           + texture(InSampler, texCoord + t * vec2(0.5, -0.5))
           + texture(InSampler, texCoord + t * vec2(-0.5, 0.5))
           + texture(InSampler, texCoord + t * vec2(0.5, 0.5));
    fragColor = vec4(c.rgb * 0.25, 1.0);
}
