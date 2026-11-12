#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position; // unit sphere vertex == object-space normal

out vec3 vNormal;
out vec3 vViewDir;
out vec3 vObject;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    vNormal = normalize(mat3(ModelViewMat) * Position);
    vViewDir = normalize(-viewPos.xyz);
    vObject = Position;
}
