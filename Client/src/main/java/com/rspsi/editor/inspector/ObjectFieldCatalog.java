package com.rspsi.editor.inspector;

import java.util.List;
import java.util.Map;

/**
 * Human-readable grouping and labels for decoded object-definition fields
 * (OpenRune {@code ObjectType} names, as exposed by
 * {@code ObjectDefinitionRawView}), for object editors.
 *
 * <p>Groups follow Displee's object editor; help text states what the client
 * does with the value where the pinned deob shows it
 * ({@code runescape-client/ObjectComposition}).</p>
 */
public final class ObjectFieldCatalog {
    public static final String GENERAL = "General";
    public static final String MODEL = "Model ids and types";
    public static final String COLOURS = "Colours & textures";
    public static final String LIGHTING = "Lighting";
    public static final String POSITIONING = "Size & positioning";
    public static final String HILL = "Hill & shadow";
    public static final String STATE = "Transformation (multiloc)";
    public static final String CLIPPING = "Masks & clipping";
    public static final String ANIMATION = "Animation";
    public static final String SOUND = "Ambient sound";
    public static final String MAP = "Map";
    public static final String PARAMS = "Params";
    public static final String OTHER = "Other";

    public static final List<String> GROUP_ORDER = List.of(GENERAL, MODEL, COLOURS, LIGHTING, POSITIONING,
            HILL, STATE, CLIPPING, ANIMATION, SOUND, MAP, PARAMS, OTHER);

    /** How an editor should present a field's value. */
    public enum Control {
        TEXT,
        CHECKBOX,
        /** Signed byte shown as a slider (-128..127). */
        SIGNED_BYTE_SLIDER,
        /** Integer list edited row by row. */
        INT_LIST,
        /** Rendered by a dedicated editor (actions, paired lists, params). */
        CUSTOM
    }

    /** How the client/engine consumes this definition field. */
    public enum Usage {
        RENDER("Render", "Render", 0xFF38BDF8, "Consumed directly by the 3D scene renderer and shader pipeline"),
        COLLISION("Collision", "Clip", 0xFF4ADE80, "Consumed by collision maps, pathfinding, and projectile blocking"),
        MAP("Map", "Map", 0xFFFACC15, "Consumed by minimap or world map element generation"),
        AUDIO("Audio", "Audio", 0xFFC084FC, "Consumed by ambient sound effects and audio distance attenuation"),
        GAMEPLAY("Gameplay", "Action", 0xFFFB923C, "Consumed by player interaction, context menus, and states"),
        UNUSED("Unused / Server", "Unused", 0xFF94A3B8, "Ignored by the client rendering engine; server-side or legacy metadata");

        private final String label;
        private final String shortLabel;
        private final int color;
        private final String description;

        Usage(String label, String shortLabel, int color, String description) {
            this.label = label;
            this.shortLabel = shortLabel;
            this.color = color;
            this.description = description;
        }

        public String label() { return label; }
        public String shortLabel() { return shortLabel; }
        public int color() { return color; }
        public String description() { return description; }
    }

    public record FieldInfo(String group, String label, String help, Control control, Usage usage) {
        public FieldInfo(String group, String label, String help) {
            this(group, label, help, Control.TEXT, Usage.UNUSED);
        }

        public FieldInfo(String group, String label, String help, Usage usage) {
            this(group, label, help, Control.TEXT, usage);
        }

        public FieldInfo(String group, String label, String help, Control control) {
            this(group, label, help, control, Usage.UNUSED);
        }
    }

    private static final Map<String, FieldInfo> FIELDS = Map.ofEntries(
            f("name", GENERAL, "Name", "Shown in menus and search filters; \"null\" means unnamed.", Usage.GAMEPLAY),
            f("actions", GENERAL, "Right-click options", "Options 1-5 (opcodes 30-34).", Control.CUSTOM, Usage.GAMEPLAY),
            f("interactive", GENERAL, "Interactive", "Opcode 19. -1 = decided from actions; 1 = clickable.", Usage.GAMEPLAY),
            f("category", GENERAL, "Category", "Content category id (opcode 61); unused by 3D renderer.", Usage.UNUSED),
            f("supportsItems", GENERAL, "Supports items", "Opcode 75: items can be placed on it.", Usage.GAMEPLAY),
            f("objectModels", MODEL, "Models", "Model ids, paired with a loc shape when types are present "
                    + "(opcodes 1/5).", Control.CUSTOM, Usage.RENDER),
            f("objectTypes", MODEL, "Model types", "Loc shape each model is used for.", Control.CUSTOM, Usage.RENDER),
            f("originalColours", COLOURS, "Recolour", "HSL colour pairs replaced on the model (opcode 40).",
                    Control.CUSTOM, Usage.RENDER),
            f("modifiedColours", COLOURS, "Recolour to", "Replacement HSL colours (opcode 40).", Control.CUSTOM, Usage.RENDER),
            f("originalTextureColours", COLOURS, "Retexture", "Texture id pairs replaced on the model (opcode 41).",
                    Control.CUSTOM, Usage.RENDER),
            f("modifiedTextureColours", COLOURS, "Retexture to", "Replacement texture ids (opcode 41).",
                    Control.CUSTOM, Usage.RENDER),
            f("ambient", LIGHTING, "Ambient", "Opcode 29, signed byte added to model base lighting (64 + ambient).",
                    Control.SIGNED_BYTE_SLIDER, Usage.RENDER),
            f("contrast", LIGHTING, "Contrast", "Opcode 39, signed byte; scaled by 25 for directional light intensity.",
                    Control.SIGNED_BYTE_SLIDER, Usage.RENDER),
            f("nonFlatShading", LIGHTING, "Smooth shading", "Opcode 22: enables Gouraud vertex normal shading and cross-tile normal merging.",
                    Control.CHECKBOX, Usage.RENDER),
            f("sizeX", POSITIONING, "Width (tiles)", "Footprint along X before rotation (opcode 14).", Usage.COLLISION),
            f("sizeY", POSITIONING, "Length (tiles)", "Footprint along Y before rotation (opcode 15).", Usage.COLLISION),
            f("isRotated", POSITIONING, "Mirrored", "Opcode 62: mirrors the model geometry across Z axis and reverses winding.", Control.CHECKBOX, Usage.RENDER),
            f("modelSizeX", POSITIONING, "Resize width (X)", "Model scale along X; 128 = 1x (opcode 65).", Usage.RENDER),
            f("modelSizeZ", POSITIONING, "Resize height (Y)", "Model vertical scale; 128 = 1x (opcode 66).", Usage.RENDER),
            f("modelSizeY", POSITIONING, "Resize depth (Z)", "Model horizontal depth scale; 128 = 1x (opcode 67).", Usage.RENDER),
            f("offsetX", POSITIONING, "Offset width (X)", "Model translation along X (opcode 70).", Usage.RENDER),
            f("offsetZ", POSITIONING, "Offset height (Y)", "Model vertical translation (opcode 71).", Usage.RENDER),
            f("offsetY", POSITIONING, "Offset depth (Z)", "Model translation along depth Z (opcode 72).", Usage.RENDER),
            f("decorDisplacement", POSITIONING, "Decoration offset",
                    "Opcode 28: wall-decoration distance from the wall; default 16.", Usage.RENDER),
            f("clipType", HILL, "Hill (contour ground)",
                    "Opcodes 21/81: -1 keeps the model rigid; otherwise the model is bent to the terrain "
                            + "(Model.contourGround).", Usage.RENDER),
            f("clipped", HILL, "Casts ground shadow",
                    "Opcode 64 turns it off. When on, the scene loader darkens the ground under the loc.", Usage.RENDER),
            f("modelClipped", HILL, "Wall occluder",
                    "Opcode 23: the wall adds occluder flags that hide scenery behind it.", Usage.RENDER),
            f("obstructive", HILL, "Obstructs ground", "Opcode 73; legacy software rasterizer low-wall culling hint.", Usage.UNUSED),
            f("rasie", HILL, "Raise (opcode 96)", "Opcode 96 flag; unused by client renderer.", Usage.UNUSED),
            f("multiVarBit", STATE, "Varbit", "Varbit whose value picks the visible state; -1 = none.", Usage.GAMEPLAY),
            f("multiVarp", STATE, "Varp", "Varp whose value picks the visible state; -1 = none.", Usage.GAMEPLAY),
            f("multiDefault", STATE, "Default object", "Shown when the var is out of range; -1 = nothing.", Usage.GAMEPLAY),
            f("transforms", STATE, "State objects",
                    "Object shown for var value 0, 1, ...; the last entry is the default.", Control.INT_LIST, Usage.GAMEPLAY),
            f("solid", CLIPPING, "Interact type", "Opcodes 17/27: 0 = walkable, 1 = blocks, 2 = default.", Usage.COLLISION),
            f("impenetrable", CLIPPING, "Blocks projectiles", "Opcodes 17/18.", Control.CHECKBOX, Usage.COLLISION),
            f("isHollow", CLIPPING, "Hollow", "Opcode 74; legacy roof culling flag.", Control.CHECKBOX, Usage.UNUSED),
            f("clipMask", CLIPPING, "Interaction access mask",
                    "Opcode 69. Server-side route-finding data giving the sides the loc can be used from; discarded by client renderer.", Usage.UNUSED),
            f("animationId", ANIMATION, "Animation id", "Sequence played by the loc; -1 = none.", Usage.RENDER),
            f("randomizeAnimStart", ANIMATION, "Randomize start", "Opcode 89.", Control.CHECKBOX, Usage.RENDER),
            f("delayAnimationUpdate", ANIMATION, "Delay update", "Opcode 90.", Control.CHECKBOX, Usage.RENDER),
            f("ambientSoundId", SOUND, "Sound id", "Ambient sound effect (opcode 78).", Usage.AUDIO),
            f("ambientSoundIds", SOUND, "Sound pool", "Random ambient sound ids (opcode 79).", Control.INT_LIST, Usage.AUDIO),
            f("soundDistance", SOUND, "Range (tiles)", "Opcodes 78/79.", Usage.AUDIO),
            f("soundRetain", SOUND, "Retain", "Opcodes 78/79.", Usage.AUDIO),
            f("soundMin", SOUND, "Min delay", "Opcode 79.", Usage.AUDIO),
            f("soundMax", SOUND, "Max delay", "Opcode 79.", Usage.AUDIO),
            f("soundDistanceFadeCurve", SOUND, "Distance fade curve", "Opcode 91.", Usage.AUDIO),
            f("soundFadeInDuration", SOUND, "Fade-in duration", "Opcode 93.", Usage.AUDIO),
            f("soundFadeOutDuration", SOUND, "Fade-out duration", "Opcode 93.", Usage.AUDIO),
            f("soundFadeInCurve", SOUND, "Fade-in curve", "Opcode 93.", Usage.AUDIO),
            f("soundFadeOutCurve", SOUND, "Fade-out curve", "Opcode 93.", Usage.AUDIO),
            f("soundVisibility", SOUND, "Sound visibility", "Opcode 95.", Usage.AUDIO),
            f("mapAreaId", MAP, "Map icon (map element)", "World-map and minimap icon, e.g. a bank (opcode 82).", Usage.MAP),
            f("mapSceneID", MAP, "Map scene sprite", "Minimap scene sprite (opcode 68).", Usage.MAP),
            f("params", PARAMS, "Params", "Opcode 249 key/value params.", Control.CUSTOM, Usage.UNUSED));

    private ObjectFieldCatalog() {
    }

    public static FieldInfo describe(String fieldName) {
        FieldInfo info = FIELDS.get(fieldName);
        return info != null ? info : new FieldInfo(OTHER, fieldName, "Decoded field without a Studio label yet.", Control.TEXT, Usage.UNUSED);
    }

    private static Map.Entry<String, FieldInfo> f(String field, String group, String label, String help) {
        return Map.entry(field, new FieldInfo(group, label, help, Control.TEXT, Usage.UNUSED));
    }

    private static Map.Entry<String, FieldInfo> f(String field, String group, String label, String help,
                                                  Usage usage) {
        return Map.entry(field, new FieldInfo(group, label, help, Control.TEXT, usage));
    }

    private static Map.Entry<String, FieldInfo> f(String field, String group, String label, String help,
                                                  Control control, Usage usage) {
        return Map.entry(field, new FieldInfo(group, label, help, control, usage));
    }
}
