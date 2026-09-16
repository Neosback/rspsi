package com.rspsi.editor.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.rspsi.editor.model.WorldFragment;

import java.util.Objects;

/** Versioned JSON interchange for cache-neutral world fragments. */
public final class WorldFragmentCodec {
    public static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private WorldFragmentCodec() { }

    public static String encode(WorldFragment fragment) {
        Objects.requireNonNull(fragment, "fragment");
        return GSON.toJson(new EncodedFragment(FORMAT_VERSION, fragment));
    }

    public static WorldFragment decode(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("formatVersion")) {
                throw new IllegalArgumentException("World fragment formatVersion is missing");
            }
            int version = root.get("formatVersion").getAsInt();
            if (version != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported world fragment format: " + version);
            }
            EncodedFragment encoded = GSON.fromJson(json, EncodedFragment.class);
            if (encoded == null || encoded.fragment() == null) {
                throw new IllegalArgumentException("World fragment JSON is empty");
            }
            return encoded.fragment();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Invalid world fragment JSON", exception);
        }
    }

    private record EncodedFragment(int formatVersion, WorldFragment fragment) { }
}
