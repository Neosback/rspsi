package com.rspsi.editor.generation;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.plugin.PluginContext;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Functional contract for a procedural generator or Wave Function Collapse synthesizer.
 *
 * <p>Generators operate non-destructively: they accept parameters and return
 * {@link ProposedChanges} without directly modifying the authored world document.</p>
 */
@FunctionalInterface
public interface Generator {

    /**
     * Executes procedural generation and produces proposed modifications.
     *
     * @param request parameter specification for the generation run
     * @param context the universal plugin context
     * @return non-destructive proposed changes
     */
    ProposedChanges generate(GenerationRequest request, PluginContext context);

    /**
     * Parameter specification for a generation run.
     */
    record GenerationRequest(
            GenerationSchema schema,
            TileCoordinate min,
            TileCoordinate max,
            long seed,
            Map<String, Object> parameters
    ) {
        public GenerationRequest {
            Objects.requireNonNull(schema, "schema");
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
            parameters = Collections.unmodifiableMap(Map.copyOf(parameters == null ? Map.of() : parameters));
        }

        public static GenerationRequest of(GenerationSchema schema, TileCoordinate min, TileCoordinate max, long seed) {
            return new GenerationRequest(schema, min, max, seed, Map.of());
        }

        @SuppressWarnings("unchecked")
        public <T> T get(String key, T defaultValue) {
            Object val = parameters.get(key);
            return val != null ? (T) val : defaultValue;
        }
    }
}
