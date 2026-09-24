// Shared scene-edge fog math. Kept equivalent to the proven vanilla renderer.
float sceneFogAmount(
    vec3 worldPosition,
    int useFog,
    float fogWest,
    float fogEast,
    float fogSouth,
    float fogNorth,
    float fogDepth
) {
    if (useFog == 0 || fogDepth <= 0.0) {
        return 0.0;
    }

    float xDistance = min(worldPosition.x - fogWest, fogEast - worldPosition.x);
    float zDistance = min(worldPosition.z - fogSouth, fogNorth - worldPosition.z);
    float nearest = min(xDistance, zDistance);
    float second = max(xDistance, zDistance);
    float rounding = 192.0;
    float distance = nearest - rounding * max(
        0.0,
        (nearest + rounding * rounding) / (second + rounding * rounding)
    );
    return 1.0 - clamp(distance / fogDepth, 0.0, 1.0);
}
