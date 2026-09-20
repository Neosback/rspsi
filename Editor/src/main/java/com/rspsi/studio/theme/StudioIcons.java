package com.rspsi.studio.theme;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Semantic icon registry for OpenRune Studio backed by Google Fonts Material Icons
 * (https://fonts.google.com/icons).
 *
 * Codepoints are mapped to the 16-bit BMP Private Use Area (0xE000 - 0xF8FF) and
 * rendered natively via Dear ImGui's font atlas.
 */
public final class StudioIcons {

    // --- Search & Discovery ---
    public static final String SEARCH = "\ue8b6";           // search
    public static final String EXPLORE = "\ue87a";          // explore
    public static final String NAVIGATION = "\ue55d";       // navigation
    public static final String SPEED = "\ue9e4";            // speed

    // --- Navigation, Viewports & Core Shell ---
    public static final String HOME = "\ue88a";             // home
    public static final String MAP = "\ue55b";              // map
    public static final String VIEWPORT = "\ue8f4";         // visibility (eye)
    public static final String VISIBILITY_OFF = "\ue8f5";   // visibility_off
    public static final String SETTINGS = "\ue8b8";         // settings (gear)
    public static final String SLIDERS = "\ue429";          // tune
    public static final String TUNE = "\ue429";             // tune
    public static final String INFO = "\ue88e";             // info
    public static final String HELP = "\ue887";             // help
    public static final String PIN = "\uf10d";              // push_pin
    public static final String CLOSE = "\ue5cd";            // close
    public static final String REFRESH = "\ue5d5";          // refresh
    public static final String FULLSCREEN = "\ue5d0";       // fullscreen
    public static final String OPEN_IN_NEW = "\ue89e";      // open_in_new

    // --- Selection & Editing Tools ---
    public static final String SELECT = "\ue569";           // near_me (pointer cursor)
    public static final String TILE = "\ue3c6";             // crop_square (single tile)
    public static final String AREA = "\ue162";             // select_all (marquee range)
    public static final String GRID = "\ue3ec";             // grid_on
    public static final String BRUSH = "\ue3ae";            // brush
    public static final String PALETTE = "\ue40a";          // palette
    public static final String COLOR_LENS = "\ue3b7";       // color_lens
    public static final String TEXTURE = "\ue421";          // texture
    public static final String LAYERS = "\ue53b";           // layers
    public static final String TERRAIN = "\ue564";          // terrain (mountain/landscape)
    public static final String HEIGHT = "\uea3b";           // architecture (elevation)
    public static final String STRAIGHTEN = "\ue41c";       // straighten
    public static final String TRENDING_UP = "\ue8e5";      // trending_up
    public static final String WATER = "\ue798";            // water_drop
    public static final String ENVIRONMENT = "\uea63";      // park (nature/environment)
    public static final String FLAG = "\ue153";             // flag (tile collision/mask)
    public static final String PATH = "\ue922";             // timeline (spline path)
    public static final String ROUTE = "\ueacd";            // route

    // --- 3D Objects & Hierarchy ---
    public static final String OBJECT = "\ue9fe";           // view_in_ar (3D cube)
    public static final String ADD_OBJECT = "\ue146";       // add_box
    public static final String PREFAB = "\ue1bd";           // widgets
    public static final String OUTLINER = "\ue8ef";         // view_list
    public static final String LIST = "\ue241";             // format_list_bulleted

    // --- Actions, History & File Operations ---
    public static final String HISTORY = "\ue889";          // history
    public static final String UNDO = "\ue166";             // undo
    public static final String REDO = "\ue15a";             // redo
    public static final String CHECK = "\ue5ca";            // check
    public static final String VALIDATE = "\ue5ca";         // check
    public static final String TERMINAL = "\ueb8e";         // terminal
    public static final String CODE = "\ue86f";             // code
    public static final String BUG_REPORT = "\ue868";       // bug_report
    public static final String FOLDER = "\ue2c7";           // folder
    public static final String FOLDER_OPEN = "\ue2c8";      // folder_open
    public static final String SAVE = "\ue161";             // save
    public static final String DELETE = "\ue872";           // delete
    public static final String EDIT = "\ue3c9";             // edit
    public static final String COPY = "\ue14d";             // content_copy
    public static final String PASTE = "\ue14f";            // content_paste
    public static final String PLAY = "\ue037";             // play_arrow

    // --- Chevrons & UI Directionals ---
    public static final String EXPAND_MORE = "\ue5cf";      // expand_more (down chevron)
    public static final String EXPAND_LESS = "\ue5ce";      // expand_less (up chevron)
    public static final String CHEVRON_LEFT = "\ue5cb";     // chevron_left
    public static final String CHEVRON_RIGHT = "\ue5cc";    // chevron_right
    public static final String ARROW_DROP_DOWN = "\ue5c5";  // arrow_drop_down
    public static final String ARROW_DROP_UP = "\ue5c7";    // arrow_drop_up

    private static final Map<String, String> ICONS_BY_NAME;

    static {
        Map<String, String> map = new HashMap<>(2500);
        try (InputStream is = StudioIcons.class.getResourceAsStream("/font/MaterialIcons-Regular.codepoints")) {
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int space = line.indexOf(' ');
                        if (space > 0) {
                            String name = line.substring(0, space).trim();
                            String hex = line.substring(space + 1).trim();
                            try {
                                int cp = Integer.parseInt(hex, 16);
                                if (cp <= 0xFFFF) {
                                    map.put(name, String.valueOf((char) cp));
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        ICONS_BY_NAME = Collections.unmodifiableMap(map);
    }

    private StudioIcons() {
    }

    /**
     * Looks up a Google Fonts Material Icon glyph by its standard name
     * (e.g. "brush", "water_drop", "sports_esports", "layers").
     *
     * @param name The Google Fonts icon name.
     * @return The icon glyph string, or fallback if not found.
     */
    public static String byName(String name, String fallback) {
        if (name == null || name.isBlank()) return fallback;
        return ICONS_BY_NAME.getOrDefault(name.toLowerCase().trim(), fallback);
    }

    /**
     * Looks up a Google Fonts Material Icon glyph by its standard name,
     * defaulting to OBJECT ("view_in_ar") if not found.
     */
    public static String byName(String name) {
        return byName(name, OBJECT);
    }

    /**
     * Returns true if the Google Fonts Material Icons registry contains the given icon name.
     */
    public static boolean has(String name) {
        return name != null && ICONS_BY_NAME.containsKey(name.toLowerCase().trim());
    }

    /**
     * Total number of registered Google Material Icons.
     */
    public static int registeredIconCount() {
        return ICONS_BY_NAME.size();
    }
}
