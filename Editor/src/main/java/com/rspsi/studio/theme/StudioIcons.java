package com.rspsi.studio.theme;

/** Semantic icon registry for the Displee-style native shell. */
public final class StudioIcons {
    /*
     * These names intentionally mirror Google Material icon names. The
     * current imgui-java binding exposes 16-bit glyph ranges while the
     * bundled Material Design Icons font uses the supplementary private-use
     * range, so FontAwesome remains the compatible visual fallback until the
     * native binding is rebuilt with 32-bit glyph support.
     */
    public static final String SEARCH = "\uf002";       // material: search
    public static final String SELECT = "\uf245";       // material: near_me
    public static final String TILE = "\uf00a";         // material: grid_on
    public static final String AREA = "\uf0b2";         // material: select_all
    public static final String TERRAIN = "\uf1fc";      // material: terrain
    public static final String WATER = "\uf043";        // material: water_drop
    public static final String HEIGHT = "\uf06e";       // material: visibility
    public static final String OBJECT = "\uf1b2";       // material: category
    public static final String MAP = "\uf279";          // material: map
    public static final String ENVIRONMENT = "\uf185";  // material: wb_sunny
    public static final String PREFAB = "\uf0c5";       // material: content_copy
    public static final String VALIDATE = "\uf00c";     // material: check
    public static final String SETTINGS = "\uf013";     // material: settings
    public static final String INFO = "\uf05a";         // material: info
    public static final String OUTLINER = "\uf03a";     // material: list

    private StudioIcons() {
    }
}
