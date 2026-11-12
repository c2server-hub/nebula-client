#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec4 RectData;     // halfWidth, halfHeight, radius, borderWidth
in vec4 BorderColor;
in vec4 ShapeParams;  // softness, intensity, hollow, opacity

out vec4 vFill;
out vec4 vBorder;
out vec2 vLocal;
flat out vec4 vRect;
flat out vec4 vParams;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vFill = Color;
    vBorder = BorderColor;
    vLocal = UV0;
    vRect = RectData;
    vParams = ShapeParams;
}
