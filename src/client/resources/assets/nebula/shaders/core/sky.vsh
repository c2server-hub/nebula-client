#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Full-screen triangle placed exactly on the far plane: with a LEQUAL depth test it only
// covers pixels where nothing was drawn (depth still 1.0), i.e. the sky.
// ModelViewMat holds the camera rotation; the per-pixel world-space view ray is interpolated.

out vec3 vRay;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vec2 ndc = uv * 2.0 - 1.0;
    gl_Position = vec4(ndc, 1.0, 1.0);
    vec4 view = inverse(ProjMat) * vec4(ndc, 1.0, 1.0);
    vRay = transpose(mat3(ModelViewMat)) * (view.xyz / view.w);
}
