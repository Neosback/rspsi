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

    public record FieldInfo(String group, String label, String help, Control control) {
        public FieldInfo(String group, String label, String help) {
            this(group, label, help, Control.TEXT);
        }
    }

    private static final Map<String, FieldInfo> FIELDS = Map.ofEntries(
            f("name", GENERAL, "Name", "Shown in menus; \"null\" means unnamed."),
            f("actions", GENERAL, "Right-click options", "Options 1-5 (opcodes 30-34).", Control.CUSTOM),
            f("interactive", GENERAL, "Interactive", "Opcode 19. -1 = decided from actions; 1 = clickable."),
            f("category", GENERAL, "Category", "Content category id (opcode 61)."),
            f("supportsItems", GENERAL, "Supports items", "Opcode 75: items can be placed on it."),
            f("objectModels", MODEL, "Models", "Model ids, paired with a loc shape when types are present "
                    + "(opcodes 1/5).", Control.CUSTOM),
            f("objectTypes", MODEL, "Model types", "Loc shape each model is used for.", Control.CUSTOM),
            f("originalColours", COLOURS, "Recolour", "HSL colour pairs replaced on the model (opcode 40).",
                    Control.CUSTOM),
            f("modifiedColours", COLOURS, "Recolour to", "Replacement HSL colours (opcode 40).", Control.CUSTOM),
            f("originalTextureColours", COLOURS, "Retexture", "Texture id pairs replaced on the model (opcode 41).",
                    Control.CUSTOM),
            f("modifiedTextureColours", COLOURS, "Retexture to", "Replacement texture ids (opcode 41).",
                    Control.CUSTOM),
            f("ambient", LIGHTING, "Ambient", "Opcode 29, signed byte added to model lighting.",
                    Control.SIGNED_BYTE_SLIDER),
            f("contrast", LIGHTING, "Contrast", "Opcode 39, signed byte; the client scales it by 25.",
                    Control.SIGNED_BYTE_SLIDER),
            f("nonFlatShading", LIGHTING, "Smooth shading", "Opcode 22: shade per vertex instead of per face.",
                    Control.CHECKBOX),
            f("sizeX", POSITIONING, "Width (tiles)", "Footprint along X before rotation (opcode 14)."),
            f("sizeY", POSITIONING, "Length (tiles)", "Footprint along Y before rotation (opcode 15)."),
            f("isRotated", POSITIONING, "Mirrored", "Opcode 62: mirrors the model.", Control.CHECKBOX),
            f("modelSizeX", POSITIONING, "Resize X", "128 = 1x (opcode 65)."),
            f("modelSizeY", POSITIONING, "Resize height", "128 = 1x (opcode 66)."),
            f("modelSizeZ", POSITIONING, "Resize Y", "128 = 1x (opcode 67)."),
            f("offsetX", POSITIONING, "Offset X", "Model translation (opcode 70)."),
            f("offsetY", POSITIONING, "Offset height", "Model translation (opcode 71)."),
            f("offsetZ", POSITIONING, "Offset Y", "Model translation (opcode 72)."),
            f("decorDisplacement", POSITIONING, "Decoration offset",
                    "Opcode 28: wall-decoration distance from the wall; default 16."),
            f("clipType", HILL, "Hill (contour ground)",
                    "Opcodes 21/81: -1 keeps the model rigid; otherwise the model is bent to the terrain "
                            + "(Model.contourGround)."),
            f("clipped", HILL, "Casts ground shadow",
                    "Opcode 64 turns it off. When on, the scene loader darkens the ground under the loc."),
            f("modelClipped", HILL, "Wall occluder",
                    "Opcode 23: the wall adds occluder flags that hide scenery behind it."),
            f("obstructive", HILL, "Obstructs ground", "Opcode 73."),
            f("rasie", HILL, "Raise (opcode 96)", "Opcode 96 flag."),
            f("multiVarBit", STATE, "Varbit", "Varbit whose value picks the visible state; -1 = none."),
            f("multiVarp", STATE, "Varp", "Varp whose value picks the visible state; -1 = none."),
            f("multiDefault", STATE, "Default object", "Shown when the var is out of range; -1 = nothing."),
            f("transforms", STATE, "State objects",
                    "Object shown for var value 0, 1, ...; the last entry is the default.", Control.INT_LIST),
            f("solid", CLIPPING, "Interact type", "Opcodes 17/27: 0 = walkable, 1 = blocks, 2 = default."),
            f("impenetrable", CLIPPING, "Blocks projectiles", "Opcodes 17/18.", Control.CHECKBOX),
            f("isHollow", CLIPPING, "Hollow", "Opcode 74.", Control.CHECKBOX),
            f("clipMask", CLIPPING, "Interaction access mask",
                    "Opcode 69. The client reads and discards it (ObjectComposition); it is "
                            + "server-side route-finding data giving the sides the loc can be used from."),
            f("animationId", ANIMATION, "Animation id", "Sequence played by the loc; -1 = none."),
            f("randomizeAnimStart", ANIMATION, "Randomize start", "Opcode 89.", Control.CHECKBOX),
            f("delayAnimationUpdate", ANIMATION, "Delay update", "Opcode 90.", Control.CHECKBOX),
            f("ambientSoundId", SOUND, "Sound id", "Ambient sound effect (opcode 78)."),
            f("ambientSoundIds", SOUND, "Sound pool", "Random ambient sound ids (opcode 79).", Control.INT_LIST),
            f("soundDistance", SOUND, "Range (tiles)", "Opcodes 78/79."),
            f("soundRetain", SOUND, "Retain", "Opcodes 78/79."),
            f("soundMin", SOUND, "Min delay", "Opcode 79."),
            f("soundMax", SOUND, "Max delay", "Opcode 79."),
            f("soundDistanceFadeCurve", SOUND, "Distance fade curve", "Opcode 91."),
            f("soundFadeInDuration", SOUND, "Fade-in duration", "Opcode 93."),
            f("soundFadeOutDuration", SOUND, "Fade-out duration", "Opcode 93."),
            f("soundFadeInCurve", SOUND, "Fade-in curve", "Opcode 93."),
            f("soundFadeOutCurve", SOUND, "Fade-out curve", "Opcode 93."),
            f("soundVisibility", SOUND, "Sound visibility", "Opcode 95."),
            f("mapAreaId", MAP, "Map icon (map element)", "World-map and minimap icon, e.g. a bank (opcode 82)."),
            f("mapSceneID", MAP, "Map scene sprite", "Minimap scene sprite (opcode 68)."),
            f("params", PARAMS, "Params", "Opcode 249 key/value params.", Control.CUSTOM));

    private ObjectFieldCatalog() {
    }

    public static FieldInfo describe(String fieldName) {
        FieldInfo info = FIELDS.get(fieldName);
        return info != null ? info : new FieldInfo(OTHER, fieldName, "Decoded field without a Studio label yet.");
    }

    private static Map.Entry<String, FieldInfo> f(String field, String group, String label, String help) {
        return Map.entry(field, new FieldInfo(group, label, help));
    }

    private static Map.Entry<String, FieldInfo> f(String field, String group, String label, String help,
                                                  Control control) {
        return Map.entry(field, new FieldInfo(group, label, help, control));
    }
}
