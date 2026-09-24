#version 330 core
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aUv;
layout(location = 2) in float aEncodedColor;
layout(location = 3) in float aAlpha;
layout(location = 4) in float aRenderType;
layout(location = 5) in vec3 aColor;
layout(location = 6) in float aPriority;
layout(location = 7) in vec4 aNormal;

#include "/common/frame_uniforms.glsl"

uniform float uFaceBias;
// Constant depth-buffer separation per bias step, on top of the
// view-space bias. The view-space term is correct up close but its
// depth-buffer effect falls off as 1 / depth^2, so coplanar pairs
// stop separating once the camera is far out. This uniform is added
// to Z_ndc directly - i.e. it is applied to clip Z scaled by the
// unbiased W (k * depth), not as a bare clip-space offset, which W
// would divide back down by depth and turn into yet another
// distance-dependent term. Only Z moves and W stays unbiased, so
// screen X/Y and the silhouette are unchanged.
out vec2 vUv;
noperspective out float vEncodedColor;
out float vAlpha;
out float vRenderType;
out vec3 vColor;
out float vFogAmount;
out vec3 vNormal;
out float vViewDepth;

#include "/common/fog.glsl"

void main() {
    vec3 d = aPosition - uCamera;
    float cy = cos(uYaw), sy = sin(uYaw);
    float x = d.x * cy - d.z * sy;
    float forward = d.x * sy + d.z * cy;
    float cp = cos(uPitch), sp = sin(uPitch);
    // The cache stores higher OSRS terrain with a smaller (more
    // negative) world-Y value. Negate that down-axis before the
    // camera pitch rotation; otherwise slopes render backwards.
    float up = -d.y;
    float y = up * cp - forward * sp;
    float depth = up * sp + forward * cp;
    // The real client subtracts faceBias * 2 from the vertex's
    // VIEW-SPACE depth, in world units, and applies it to the
    // depth value only - screen x/y come from the unbiased
    // divisor (Model.java: field3037[v] - faceBias * 2, while
    // modelViewportXs/Ys divide by the raw field3037). So keep w
    // at the true depth and rewrite z so that, after the
    // perspective divide, z_ndc equals uDepthA + uDepthB /
    // biasedDepth. A clip-space "z += bias / 128" instead makes
    // the offset shrink with proximity, which is why flush wall
    // decorations z-fought their wall when zoomed in.
    float biasedDepth = max(depth - uFaceBias * 2.0, 1.0);
    // Multiplying the nudge by depth (the unbiased W) is what makes
    // it survive the perspective divide as a constant separation in
    // normalised depth, independent of camera distance.
    vec4 projected = vec4(uFocal / uAspect * x, uFocal * y,
                          uDepthA * depth + uDepthB * (depth / biasedDepth)
                                  + uFaceBias * uDepthBiasNudge * depth,
                          depth);
    gl_Position = projected;
    vUv = aUv;
    vEncodedColor = aEncodedColor;
    vAlpha = aAlpha;
    vRenderType = aRenderType;
    vColor = aColor;
    vNormal = aNormal.xyz;
    vViewDepth = depth;
    vFogAmount = sceneFogAmount(
        aPosition, uUseFog, uFogWest, uFogEast,
        uFogSouth, uFogNorth, uFogDepth
    );
}
