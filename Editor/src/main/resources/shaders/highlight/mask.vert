#version 330 core

// Highlight mask geometry: world-space positions only, projected exactly as
// scene/vanilla.vert does (without face bias), so the outline sits on the
// scene image pixel for pixel.
layout(location = 0) in vec3 aPosition;

#include "/common/frame_uniforms.glsl"

void main() {
    vec3 d = aPosition - uCamera;
    float cy = cos(uYaw), sy = sin(uYaw);
    float x = d.x * cy - d.z * sy;
    float forward = d.x * sy + d.z * cy;
    float cp = cos(uPitch), sp = sin(uPitch);
    float up = -d.y;
    float y = up * cp - forward * sp;
    float depth = up * sp + forward * cp;
    gl_Position = vec4(uFocal / uAspect * x, uFocal * y,
                       uDepthA * depth + uDepthB, depth);
}
