#version 150

// Fullscreen triangle without vertex data: ids 0, 1, 2 -> (-1,-1), (3,-1), (-1,3)
void main() {
    vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
}
