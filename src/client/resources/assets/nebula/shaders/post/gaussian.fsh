#version 330

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    vec2 Direction; // unit axis
    float Radius;   // kernel radius in texels of InSampler
};

in vec2 texCoord;
out vec4 fragColor;

// Separable gaussian; adjacent taps are merged into one bilinear fetch (linear sampling trick).
void main() {
    vec2 texel = Direction / vec2(textureSize(InSampler, 0));
    float sigma = max(Radius * 0.5, 0.5);
    float k = -0.5 / (sigma * sigma);
    vec4 sum = texture(InSampler, texCoord);
    float total = 1.0;
    int r = int(ceil(Radius));
    for (int i = 1; i <= r; i += 2) {
        float w1 = exp(float(i * i) * k);
        float w2 = exp(float((i + 1) * (i + 1)) * k);
        float w = w1 + w2;
        float offset = (float(i) * w1 + float(i + 1) * w2) / w;
        sum += (texture(InSampler, texCoord + texel * offset) + texture(InSampler, texCoord - texel * offset)) * w;
        total += 2.0 * w;
    }
    fragColor = vec4((sum / total).rgb, 1.0);
}
