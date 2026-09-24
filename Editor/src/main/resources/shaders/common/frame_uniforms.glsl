// Shared OpenGL 3.3 std140 frame block.
//
// Six 16-byte slots keep the Java/native layout explicit and avoid relying on
// scalar/vec3 padding rules. Per-draw material state intentionally stays out.
layout(std140) uniform FrameUniforms {
    vec4 frameCameraPitch;              // camera.xyz, pitch
    vec4 frameYawFocalAspectDepthA;     // yaw, focal, aspect, depthA
    vec4 frameDepthPresentation;        // depthB, depthBiasNudge, brightness, exposure
    ivec4 frameFlags;                   // smoothBanding, useFog, clientCycle, reserved
    vec4 frameFogBounds;                // west, east, south, north
    vec4 frameFogColorDepth;            // fogColor.rgb, fogDepth
};

#define uCamera frameCameraPitch.xyz
#define uPitch frameCameraPitch.w
#define uYaw frameYawFocalAspectDepthA.x
#define uFocal frameYawFocalAspectDepthA.y
#define uAspect frameYawFocalAspectDepthA.z
#define uDepthA frameYawFocalAspectDepthA.w
#define uDepthB frameDepthPresentation.x
#define uDepthBiasNudge frameDepthPresentation.y
#define uBrightness frameDepthPresentation.z
#define uExposure frameDepthPresentation.w
#define uSmoothBanding frameFlags.x
#define uUseFog frameFlags.y
#define uClientCycle frameFlags.z
#define uFogWest frameFogBounds.x
#define uFogEast frameFogBounds.y
#define uFogSouth frameFogBounds.z
#define uFogNorth frameFogBounds.w
#define uFogColor frameFogColorDepth.rgb
#define uFogDepth frameFogColorDepth.w
