#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position; // quad corner in [-1, 1]

out vec2 vCorner;

// ModelViewMat places the orb centre; ModelOffset.z is the halo radius in blocks.
void main() {
    vec4 centre = ModelViewMat * vec4(0.0, 0.0, 0.0, 1.0);
    vec4 viewPos = centre + vec4(Position.xy * ModelOffset.z, 0.0, 0.0);
    // Pull towards the camera so the halo is not swallowed by the orb's own depth.
    viewPos.xyz += normalize(-centre.xyz) * ModelOffset.z * 0.5;
    gl_Position = ProjMat * viewPos;
    vCorner = Position.xy;
}
