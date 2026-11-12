#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <nebula:sdf.glsl>

in vec4 vFill;
in vec4 vBorder;
in vec2 vLocal;
flat in vec4 vRect;
flat in vec4 vParams;

out vec4 fragColor;

void main() {
    float d = nebula_sdRoundBox(vLocal, vRect.xy, vRect.z);
    vec4 color;
    if (vParams.x > 0.0) {
        // Soft shadow / glow: analytic gaussian-blurred rounded box.
        float a = nebula_gaussianCoverage(d, vParams.x * 0.5) * vParams.y;
        if (vParams.z > 0.5) {
            // Hollow glow: keep the halo outside the shape only.
            a *= smoothstep(-1.0, 1.0, d);
        }
        color = vec4(vFill.rgb, vFill.a * clamp(a, 0.0, 1.0));
    } else {
        // Screen-space derivative gives exactly one pixel of anti-aliasing at any GUI scale.
        float aa = max(fwidth(d), 1e-4);
        float outer = clamp(0.5 - d / aa, 0.0, 1.0);
        float inner = vRect.w > 0.0 ? clamp(0.5 - (d + vRect.w) / aa, 0.0, 1.0) : outer;
        float borderCov = outer - inner;
        vec3 rgb = vFill.rgb * vFill.a * inner + vBorder.rgb * vBorder.a * borderCov;
        float alpha = vFill.a * inner + vBorder.a * borderCov;
        color = alpha > 0.0 ? vec4(rgb / alpha, alpha) : vec4(0.0);
    }
    if (color.a <= 0.002) {
        discard;
    }
    color.a *= vParams.w;
    fragColor = color * ColorModulator;
}
