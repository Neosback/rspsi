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
    public static final String MODEL = "Model";
    public static final String COLOURS = "Colours & textures";
    public static final String STATE = "State (multiloc)";
    public static final String CLIPPING = "Masks & clipping";
    public static final String ANIMATION = "Animation";
    public static final String SOUND = "Ambient sound";
    public static final String MAP = "Map";
    public static final String PARAMS = "Params";
    public static final String OTHER = "Other";

    public static final List<String> GROUP_ORDER = List.of(
            GENERAL, MODEL, COLOURS, STATE, CLIPPING, ANIMATION, SOUND, MAP, PARAMS, OTHER);

    public record FieldInfo(String group, String label, String help) {
    }

    private static final Map<String, FieldInfo> FIELDS = Map.ofEntries(
            f("name", GENERAL, "Name", "Shown in menus; \"null\" means unnamed."),
            f("sizeX", GENERAL, "Width (tiles)", "Footprint along X before rotation."),
            f("sizeY", GENERAL, "Length (tiles)", "Footprint along Y before rotation."),
            f("interactive", GENERAL, "Interactive", "-1 = decided from actions; 1 = clickable."),
            f("actions", GENERAL, "Actions", "Right-click options."),
            f("category", GENERAL, "Category", "Content category id (opcode 61)."),
            f("isRotated", MODEL, "Mirrored", "Mirrors the model (opcode 62)."),
            f("objectModels", MODEL, "Model ids", "Models, paired with model types when present."),
            f("objectTypes", MODEL, "Model types", "Loc shape each model is used for."),
            f("modelSizeX", MODEL, "Scale X", "128 = 1x (opcode 65)."),
            f("modelSizeY", MODEL, "Scale Y (height)", "128 = 1x (opcode 67)."),
            f("modelSizeZ", MODEL, "Scale Z", "128 = 1x (opcode 66)."),
            f("offsetX", MODEL, "Offset X", "Model translation (opcode 70)."),
            f("offsetY", MODEL, "Offset Y (height)", "Model translation (opcode 71)."),
            f("offsetZ", MODEL, "Offset Z", "Model translation (opcode 72)."),
            f("nonFlatShading", MODEL, "Non-flat shading", "Opcode 22: lighting shading mode."),
            f("ambient", MODEL, "Ambient light", "Added to model lighting (opcode 29)."),
            f("contrast", MODEL, "Contrast", "Lighting contrast (opcode 39)."),
            f("decorDisplacement", MODEL, "Decoration offset", "Wall-decoration displacement from the wall (opcode 28)."),
            f("isHollow", MODEL, "Hollow", "Opcode 74."),
            f("rasie", MODEL, "Raise (opcode 96)", "Opcode 96 flag."),
            f("originalColours", COLOURS, "Recolour from", "HSL colours to replace (opcode 40)."),
            f("modifiedColours", COLOURS, "Recolour to", "Replacement HSL colours (opcode 40)."),
            f("originalTextureColours", COLOURS, "Retexture from", "Texture ids to replace (opcode 41)."),
            f("modifiedTextureColours", COLOURS, "Retexture to", "Replacement texture ids (opcode 41)."),
            f("multiVarBit", STATE, "State varbit", "Varbit whose value picks the visible state; -1 = none."),
            f("multiVarp", STATE, "State varp", "Varp whose value picks the visible state; -1 = none."),
            f("multiDefault", STATE, "Default state object", "Shown when the var is out of range; -1 = nothing."),
            f("transforms", STATE, "State objects", "Object shown for var value 0, 1, ...; last entry is the default."),
            f("solid", CLIPPING, "Blocks walking", "Collision type (opcodes 17/27): 0 = walkable."),
            f("impenetrable", CLIPPING, "Blocks projectiles", "Opcode 17/18."),
            f("clipType", CLIPPING, "Clip type", "Opcodes 21/81: how the model follows terrain."),
            f("clipped", CLIPPING, "Clipped", "Opcode 64: model clipping against the scene."),
            f("modelClipped", CLIPPING, "Model clipped", "Opcode 23."),
            f("clipMask", CLIPPING, "Interaction access mask",
                    "Opcode 69. The client reads and discards it (ObjectComposition); it is "
                            + "server-side route-finding data giving the sides the loc can be used from."),
            f("obstructive", CLIPPING, "Occludes", "Opcode 73: blocks the scene behind it."),
            f("supportsItems", CLIPPING, "Supports items", "Opcode 75: items can be placed on it."),
            f("animationId", ANIMATION, "Animation id", "Sequence played by the loc; -1 = none."),
            f("randomizeAnimStart", ANIMATION, "Randomize animation start", "Opcode 89."),
            f("delayAnimationUpdate", ANIMATION, "Delay animation update", "Opcode 90."),
            f("ambientSoundId", SOUND, "Sound id", "Ambient sound effect (opcode 78)."),
            f("ambientSoundIds", SOUND, "Sound ids", "Random ambient sound pool (opcode 79)."),
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
            f("mapAreaId", MAP, "Map icon (map element)", "World-map and minimap icon, e.g. a bank (opcodes 60/82)."),
            f("mapSceneID", MAP, "Map scene sprite", "Minimap scene sprite (opcode 68)."),
            f("params", PARAMS, "Params", "Opcode 249 key/value params."));

    private ObjectFieldCatalog() {
    }

    public static FieldInfo describe(String fieldName) {
        FieldInfo info = FIELDS.get(fieldName);
        return info != null ? info : new FieldInfo(OTHER, fieldName, "Decoded field without a Studio label yet.");
    }

    private static Map.Entry<String, FieldInfo> f(String field, String group, String label, String help) {
        return Map.entry(field, new FieldInfo(group, label, help));
    }
}
