#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <nebula:sdf.glsl>

uniform sampler2D Sampler0; // gaussian-blurred copy of the frame behind the GUI

in vec4 vFill;
in vec4 vBorder;
in vec2 vLocal;
flat in vec4 vRect;
flat in vec4 vParams;

out vec4 fragColor;

void main() {
    float d = nebula_sdRoundBox(vLocal, vRect.xy, vRect.z);
    float aa = max(fwidth(d), 1e-4);
    float outer = clamp(0.5 - d / aa, 0.0, 1.0);
    if (outer <= 0.0) {
        discard;
    }

    // Surface normal of the SDF, used to bend the backdrop near the rim (liquid-glass lensing).
    vec2 e = vec2(0.5, 0.0);
    vec2 n = vec2(
        nebula_sdRoundBox(vLocal + e.xy, vRect.xy, vRect.z) - nebula_sdRoundBox(vLocal - e.xy, vRect.xy, vRect.z),
        nebula_sdRoundBox(vLocal + e.yx, vRect.xy, vRect.z) - nebula_sdRoundBox(vLocal - e.yx, vRect.xy, vRect.z)
    );
    n = length(n) > 1e-5 ? normalize(n) : vec2(0.0);
    float rim = clamp(1.0 + d / 14.0, 0.0, 1.0);
    float lens = rim * rim * rim;

    vec2 uv = gl_FragCoord.xy / ScreenSize;
    // GUI y grows downwards, framebuffer y grows upwards.
    uv += vec2(-n.x, n.y) * lens * vParams.y * 18.0 / ScreenSize;
    vec3 backdrop = texture(Sampler0, uv).rgb;

    vec3 glass = mix(backdrop, vFill.rgb, vFill.a);
    // Specular rim lit from the top-left plus a faint vertical sheen.
    float light = clamp(dot(n, normalize(vec2(-0.55, -0.85))), 0.0, 1.0);
    glass += vec3(1.0) * pow(rim, 6.0) * (0.10 + 0.25 * light) * vParams.y;
    float sheen = 1.0 - clamp((vLocal.y + vRect.y) / (2.0 * vRect.y), 0.0, 1.0);
    glass += vec3(0.035) * sheen * vParams.y;

    float inner = vRect.w > 0.0 ? clamp(0.5 - (d + vRect.w) / aa, 0.0, 1.0) : outer;
    float borderCov = outer - inner;
    vec3 rgb = mix(glass, vBorder.rgb, vBorder.a * (borderCov / max(outer, 1e-4)));
    fragColor = vec4(rgb, outer * vParams.w) * ColorModulator;
}
