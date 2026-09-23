package com.rspsi.studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.definition.ObjectDefinitionRawView;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Versioned durable provenance for verified definition publication outputs.
 *
 * <p>This store records only metadata and decoded verified snapshots. It never
 * writes cache data. Callers must revalidate the selected output cache before
 * restoring a record into a live workspace.</p>
 */
public final class StudioDefinitionPublicationStore {
    static final int CURRENT_SCHEMA = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;

    public StudioDefinitionPublicationStore(Path file) {
        this.file = Objects.requireNonNull(file, "publication store file")
                .toAbsolutePath().normalize();
    }

    public Optional<PublicationState> loadFor(
            Path sourceCache,
            OsrsCacheMetadata identity) {
        Objects.requireNonNull(sourceCache, "sourceCache");
        Objects.requireNonNull(identity, "identity");
        Path source = sourceCache.toAbsolutePath().normalize();

        try {
            return readAll(false).stream()
                    .filter(state -> state.sourceCache().equals(source))
                    .filter(state -> state.identity().equals(identity))
                    .findFirst();
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    /**
     * Atomically persists one source cache's current publication state while
     * retaining records for other source caches.
     */
    public void save(PublicationState state) {
        Objects.requireNonNull(state, "publication state");

        List<PublicationState> states;
        try {
            states = new ArrayList<>(readAll(true));
        } catch (IOException error) {
            throw new UncheckedIOException(
                    "Unable to read definition publication provenance: " + file,
                    error);
        }

        states.removeIf(existing ->
                existing.sourceCache().equals(state.sourceCache()));
        states.add(state);
        states.sort(Comparator.comparing(value ->
                value.sourceCache().toString()));

        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", CURRENT_SCHEMA);
        ArrayNode entries = root.putArray("entries");
        for (PublicationState value : states) {
            entries.add(encodeState(value));
        }

        Path parent = file.getParent();
        if (parent == null) {
            throw new IllegalArgumentException(
                    "Publication store must have a parent directory: " + file);
        }

        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(
                    parent, ".definition-publications-", ".json");
            Files.writeString(
                    temporary,
                    MAPPER.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(root) + "\n");
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    error.addSuppressed(cleanupFailure);
                }
            }
            throw new UncheckedIOException(
                    "Unable to persist definition publication provenance: " + file,
                    error);
        }
    }

    private List<PublicationState> readAll(boolean strict) throws IOException {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }

        try {
            JsonNode root = MAPPER.readTree(Files.readString(file));
            if (root == null || !root.isObject()) {
                return malformed(strict, "root is not an object");
            }
            if (root.path("schemaVersion").asInt(-1) != CURRENT_SCHEMA) {
                return malformed(strict, "unsupported schema version");
            }
            JsonNode entries = root.get("entries");
            if (entries == null || !entries.isArray()) {
                return malformed(strict, "entries is not an array");
            }

            List<PublicationState> states = new ArrayList<>();
            for (JsonNode entry : entries) {
                try {
                    states.add(decodeState(entry));
                } catch (RuntimeException malformedEntry) {
                    if (strict) {
                        throw new IOException(
                                "Malformed definition publication entry",
                                malformedEntry);
                    }
                }
            }
            return List.copyOf(states);
        } catch (IOException error) {
            if (strict) {
                throw error;
            }
            return List.of();
        } catch (RuntimeException error) {
            if (strict) {
                throw new IOException(
                        "Unable to decode definition publication provenance",
                        error);
            }
            return List.of();
        }
    }

    private static List<PublicationState> malformed(
            boolean strict,
            String message) throws IOException {
        if (strict) {
            throw new IOException(message);
        }
        return List.of();
    }

    private static ObjectNode encodeState(PublicationState state) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("sourceCache", state.sourceCache().toString());
        node.put("revision", state.identity().revision());
        if (state.identity().subRevision() == null) {
            node.putNull("subRevision");
        } else {
            node.put("subRevision", state.identity().subRevision());
        }
        node.put("fingerprint", state.identity().fingerprint());
        node.put("outputCache", state.outputCache().toString());

        ArrayNode definitions = node.putArray("definitions");
        state.publishedSnapshots().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry ->
                        definitions.add(encodeDefinition(entry.getValue())));
        return node;
    }

    private static ObjectNode encodeDefinition(ObjectDefinitionRawView definition) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", definition.id());

        ArrayNode fields = node.putArray("fields");
        for (ObjectDefinitionRawView.Field field : definition.fields()) {
            ObjectNode value = fields.addObject();
            value.put("name", field.name());
            value.put("opcode", field.opcode());
            value.put("type", field.type().name());
            value.put("value", field.value());
        }

        ArrayNode params = node.putArray("params");
        for (ObjectDefinitionRawView.Param param : definition.params()) {
            ObjectNode value = params.addObject();
            value.put("id", param.id());
            value.put("type", param.type().name());
            value.put("value", param.value());
        }
        return node;
    }

    private static PublicationState decodeState(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(
                    "Publication entry is not an object");
        }

        Path source = requiredPath(node, "sourceCache");
        int revision = requiredPositiveInt(node, "revision");
        Integer subRevision = node.hasNonNull("subRevision")
                ? node.get("subRevision").intValue()
                : null;
        String fingerprint = requiredText(node, "fingerprint");
        Path output = requiredPath(node, "outputCache");

        JsonNode definitionsNode = node.get("definitions");
        if (definitionsNode == null || !definitionsNode.isArray()) {
            throw new IllegalArgumentException(
                    "Publication entry definitions is not an array");
        }

        LinkedHashMap<Integer, ObjectDefinitionRawView> definitions =
                new LinkedHashMap<>();
        for (JsonNode definitionNode : definitionsNode) {
            ObjectDefinitionRawView definition =
                    decodeDefinition(definitionNode);
            if (definitions.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException(
                        "Duplicate published object definition "
                                + definition.id());
            }
        }
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Publication entry has no object definitions");
        }

        return new PublicationState(
                source,
                new OsrsCacheMetadata(revision, subRevision, fingerprint),
                output,
                definitions);
    }

    private static ObjectDefinitionRawView decodeDefinition(JsonNode node) {
        int id = requiredNonNegativeInt(node, "id");

        JsonNode fieldsNode = node.get("fields");
        if (fieldsNode == null || !fieldsNode.isArray()) {
            throw new IllegalArgumentException(
                    "Published object fields is not an array");
        }
        List<ObjectDefinitionRawView.Field> fields = new ArrayList<>();
        for (JsonNode fieldNode : fieldsNode) {
            fields.add(new ObjectDefinitionRawView.Field(
                    requiredText(fieldNode, "name"),
                    text(fieldNode, "opcode"),
                    valueType(fieldNode, "type"),
                    text(fieldNode, "value")));
        }

        JsonNode paramsNode = node.get("params");
        if (paramsNode == null || !paramsNode.isArray()) {
            throw new IllegalArgumentException(
                    "Published object params is not an array");
        }
        List<ObjectDefinitionRawView.Param> params = new ArrayList<>();
        for (JsonNode paramNode : paramsNode) {
            params.add(new ObjectDefinitionRawView.Param(
                    requiredNonNegativeInt(paramNode, "id"),
                    valueType(paramNode, "type"),
                    text(paramNode, "value")));
        }

        return new ObjectDefinitionRawView(id, fields, params);
    }

    private static ObjectDefinitionRawView.ValueType valueType(
            JsonNode node,
            String field) {
        return ObjectDefinitionRawView.ValueType.valueOf(
                requiredText(node, field));
    }

    private static Path requiredPath(JsonNode node, String field) {
        return Path.of(requiredText(node, field))
                .toAbsolutePath().normalize();
    }

    private static int requiredPositiveInt(JsonNode node, String field) {
        int value = node.path(field).asInt(-1);
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "Expected positive integer field " + field);
        }
        return value;
    }

    private static int requiredNonNegativeInt(JsonNode node, String field) {
        int value = node.path(field).asInt(-1);
        if (value < 0) {
            throw new IllegalArgumentException(
                    "Expected non-negative integer field " + field);
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = text(node, field).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "Expected non-blank text field " + field);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    public record PublicationState(
            Path sourceCache,
            OsrsCacheMetadata identity,
            Path outputCache,
            Map<Integer, ObjectDefinitionRawView> publishedSnapshots) {
        public PublicationState {
            sourceCache = Objects.requireNonNull(sourceCache, "sourceCache")
                    .toAbsolutePath().normalize();
            identity = Objects.requireNonNull(identity, "identity");
            outputCache = Objects.requireNonNull(outputCache, "outputCache")
                    .toAbsolutePath().normalize();
            publishedSnapshots = Map.copyOf(
                    Objects.requireNonNull(
                            publishedSnapshots,
                            "publishedSnapshots"));
            if (publishedSnapshots.isEmpty()) {
                throw new IllegalArgumentException(
                        "Publication state requires at least one definition snapshot");
            }
            for (Map.Entry<Integer, ObjectDefinitionRawView> entry
                    : publishedSnapshots.entrySet()) {
                if (entry.getKey() < 0
                        || entry.getValue() == null
                        || entry.getValue().id() != entry.getKey()) {
                    throw new IllegalArgumentException(
                            "Published snapshot does not match object id "
                                    + entry.getKey());
                }
            }
        }
    }
}
