package com.rspsi.editor.generation;

import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.plugin.ContributionOwner;
import com.rspsi.editor.plugin.PluginContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service managing registered procedural generators, schema discovery, and generation execution.
 */
public final class GeneratorService {

    public record GeneratorRegistration(
            String id,
            GenerationSchema schema,
            String displayName,
            String description,
            Generator generator,
            ContributionOwner owner
    ) {
        public GeneratorRegistration {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(schema, "schema");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(generator, "generator");
            Objects.requireNonNull(owner, "owner");
        }
    }

    private final Map<String, GeneratorRegistration> registrations = new ConcurrentHashMap<>();
    private final List<GeneratorRegistration> registrationList = new CopyOnWriteArrayList<>();

    /**
     * Registers a procedural generator bound to a contribution owner.
     * The returned handle unregisters the generator when closed.
     */
    public AutoCloseable registerGenerator(ContributionOwner owner, String id,
                                           GenerationSchema schema, String displayName,
                                           String description, Generator generator) {
        GeneratorRegistration reg = new GeneratorRegistration(
                id, schema, displayName, description, generator, owner);
        registrations.put(id, reg);
        registrationList.add(reg);
        return () -> {
            registrations.remove(id, reg);
            registrationList.remove(reg);
        };
    }

    /** Unregisters all generators owned by the specified contribution owner. */
    public void unregisterAll(ContributionOwner owner) {
        Objects.requireNonNull(owner, "owner");
        registrationList.removeIf(reg -> {
            if (reg.owner().equals(owner)) {
                registrations.remove(reg.id(), reg);
                return true;
            }
            return false;
        });
    }

    /** Finds a generator registration by its unique identifier. */
    public Optional<GeneratorRegistration> generator(String id) {
        return Optional.ofNullable(registrations.get(id));
    }

    /** Returns an unmodifiable list of all registered generators. */
    public List<GeneratorRegistration> generators() {
        return Collections.unmodifiableList(registrationList);
    }

    /** Returns all generators matching a given schema. */
    public List<GeneratorRegistration> generatorsForSchema(GenerationSchema schema) {
        return registrationList.stream()
                .filter(reg -> reg.schema().equals(schema))
                .toList();
    }

    /**
     * Executes the specified generator non-destructively to produce a proposal.
     */
    public ProposedChanges preview(String generatorId, Generator.GenerationRequest request, PluginContext context) {
        GeneratorRegistration reg = generator(generatorId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown generator: " + generatorId));
        return reg.generator().generate(request, context);
    }

    /**
     * Executes the generator and immediately applies its changes to the session via an undoable command.
     */
    public EditorCommand commit(String generatorId, Generator.GenerationRequest request,
                                PluginContext context, String description) {
        ProposedChanges changes = preview(generatorId, request, context);
        EditorCommand command = changes.toCommand(description);
        context.session().execute(command);
        return command;
    }
}
