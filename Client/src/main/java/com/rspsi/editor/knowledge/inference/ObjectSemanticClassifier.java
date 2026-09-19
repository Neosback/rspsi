package com.rspsi.editor.knowledge.inference;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.knowledge.Evidence;
import com.rspsi.editor.knowledge.KnowledgeFact;
import com.rspsi.editor.knowledge.SemanticTag;
import com.rspsi.editor.knowledge.cache.ObjectKnowledge;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Layer 4 Inference Classifier: Evaluates multi-signal object attributes to infer semantic tags.
 *
 * <p>Signals combine object name heuristics, available player interactions, category,
 * footprint dimensions, and collision properties to generate confidence-scored tags with
 * explicit evidence lists.</p>
 */
public final class ObjectSemanticClassifier {
    private final DefinitionProvider definitions;

    public ObjectSemanticClassifier(DefinitionProvider definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    /**
     * Classifies a world object and returns all inferred semantic facts.
     */
    public List<KnowledgeFact<SemanticTag>> classify(WorldObject object) {
        Objects.requireNonNull(object, "object");
        Optional<ObjectDefinitionView> defOpt = definitions.object(object.id());
        if (defOpt.isEmpty()) return List.of();
        ObjectDefinitionView def = defOpt.get();

        String name = def.name() == null ? "" : def.name().toLowerCase(Locale.ROOT);
        List<String> actions = def.interactions().stream()
                .filter(Objects::nonNull)
                .map(a -> a.toLowerCase(Locale.ROOT))
                .toList();

        List<KnowledgeFact<SemanticTag>> facts = new ArrayList<>();

        // 1. Tree classification
        classifyTree(name, actions, def).ifPresent(facts::add);

        // 2. Rock / Mine classification
        classifyRock(name, actions, def).ifPresent(facts::add);

        // 3. Door classification
        classifyDoor(name, actions, def, object).ifPresent(facts::add);

        // 4. Container / Storage classification
        classifyContainer(name, actions).ifPresent(facts::add);

        // 5. Light Source classification
        classifyLightSource(name, actions).ifPresent(facts::add);

        // 6. Seating classification
        classifySeat(name, actions).ifPresent(facts::add);

        // 7. Bank classification
        classifyBank(name, actions).ifPresent(facts::add);

        // 8. Altar classification
        classifyAltar(name, actions).ifPresent(facts::add);

        return List.copyOf(facts);
    }

    private Optional<KnowledgeFact<SemanticTag>> classifyTree(String name, List<String> actions, ObjectDefinitionView def) {
        List<Evidence> evidence = new ArrayList<>();
        float confidence = 0.0f;

        if (name.contains("tree") || name.contains("oak") || name.contains("willow")
                || name.contains("yew") || name.contains("magic tree") || name.contains("maple")) {
            evidence.add(new Evidence("Name matches tree pattern: '" + name + "'", 0.5f));
            confidence += 0.5f;
        }
        if (actions.stream().anyMatch(a -> a.contains("chop") || a.contains("cut"))) {
            evidence.add(new Evidence("Has woodcutting interaction action", 0.45f));
            confidence += 0.45f;
        }

        if (confidence >= 0.5f) {
            return Optional.of(KnowledgeFact.inferred(SemanticTag.TREE, Math.min(1.0f, confidence), evidence));
        }
        return Optional.empty();
    }

    private Optional<KnowledgeFact<SemanticTag>> classifyRock(String name, List<String> actions, ObjectDefinitionView def) {
        List<Evidence> evidence = new ArrayList<>();
        float confidence = 0.0f;

        if (name.contains("rock") || name.contains("ore") || name.contains("mineral")
                || name.contains("vein") || name.contains("deposit")) {
            evidence.add(new Evidence("Name matches mining rock pattern: '" + name + "'", 0.5f));
            confidence += 0.5f;
        }
        if (actions.stream().anyMatch(a -> a.contains("mine") || a.contains("prospect"))) {
            evidence.add(new Evidence("Has mining interaction action", 0.45f));
            confidence += 0.45f;
        }

        if (confidence >= 0.5f) {
            return Optional.of(KnowledgeFact.inferred(SemanticTag.ROCK, Math.min(1.0f, confidence), evidence));
        }
        return Optional.empty();
    }

    private Optional<KnowledgeFact<SemanticTag>> classifyDoor(String name, List<String> actions, ObjectDefinitionView def, WorldObject obj) {
        List<Evidence> evidence = new ArrayList<>();
        float confidence = 0.0f;

        if (name.contains("door") || name.contains("gate") || name.contains("portal") || name.contains("trapdoor")) {
            evidence.add(new Evidence("Name matches portal/door pattern: '" + name + "'", 0.5f));
            confidence += 0.5f;
        }
        if (actions.stream().anyMatch(a -> a.contains("open") || a.contains("close") || a.contains("unlock"))) {
            evidence.add(new Evidence("Has open/close interaction action", 0.42f));
            confidence += 0.42f;
        }

        if (confidence >= 0.5f) {
            return Optional.of(KnowledgeFact.inferred(SemanticTag.DOOR, Math.min(1.0f, confidence), evidence));
        }
        return Optional.empty();
    }

    private Optional<KnowledgeFact<SemanticTag>> classifyContainer(String name, List<String> actions) {
        List<Evidence> evidence = new ArrayList<>();
        float confidence = 0.0f;

        if (name.contains("chest") || name.contains("crate") || name.contains("barrel")
                || name.contains("box") || name.contains("sack") || name.contains("cupboard")) {
            evidence.add(new Evidence("Name matches container pattern: '" + name + "'", 0.55f));
            confidence += 0.55f;
        }
        if (actions.stream().anyMatch(a -> a.contains("search") || a.contains("open"))) {
            evidence.add(new Evidence("Has search or open container action", 0.35f));
            confidence += 0.35f;
        }

        if (confidence >= 0.5f) {
            return Optional.of(KnowledgeFact.inferred(SemanticTag.CONTAINER, Math.min(1.0f, confidence), evidence));
        }
        return Optional.empty();
    }

    private Optional<KnowledgeFact<SemanticTag>> classifyLightSource(String name, List<String> actions) {
        List<Evidence> evidence = new ArrayList<>();
        float confidence = 0.0f;

        if (name.contains("torch") || name.contains("lamp") || name.contains("candle")
                || name.contains("lantern") || name.contains("brazier") || name.contains("campfire")
                || name.contains("fire")) {
            evidence.add(new Evidence("Name matches light source pattern: '" + name + "'", 0.6f));
            confidence += 0.6f;
        }
        if (actions.stream().anyMatch(a -> a.contains("light") || a.contains("extinguish"))) {
            evidence.add(new Evidence("Has lighting action", 0.35f));
            confidence += 0.35f;
        }

        if (confidence >= 0.5f) {
            return Optional.of(KnowledgeFact.inferred(SemanticTag.LIGHT_SOURCE, Math.min(1.0f, confidence), evidence));
        }
        return Optional.empty();
    }

    private Optional<KnowledgeFact<SemanticTag>> classifySeat(String name, List<String> actions) {
        List<Evidence> evidence = new ArrayList<>();
        float confidence = 0.0f;

        if (name.contains("bench") || name.contains("chair") || name.contains("stool")
                || name.contains("throne") || name.contains("pew")) {
            evidence.add(new Evidence("Name matches seating pattern: '" + name + "'", 0.65f));
            confidence += 0.65f;
        }
        if (actions.stream().anyMatch(a -> a.contains("sit"))) {
            evidence.add(new Evidence("Has sit interaction action", 0.30f));
            confidence += 0.30f;
        }

        if (confidence >= 0.5f) {
            return Optional.of(KnowledgeFact.inferred(SemanticTag.SEAT, Math.min(1.0f, confidence), evidence));
        }
        return Optional.empty();
    }

    private Optional<KnowledgeFact<SemanticTag>> classifyBank(String name, List<String> actions) {
        List<Evidence> evidence = new ArrayList<>();
        float confidence = 0.0f;

        if (name.contains("bank") || name.contains("booth") || name.contains("counter")) {
            evidence.add(new Evidence("Name matches banking pattern: '" + name + "'", 0.55f));
            confidence += 0.55f;
        }
        if (actions.stream().anyMatch(a -> a.contains("bank") || a.contains("collect"))) {
            evidence.add(new Evidence("Has bank interaction action", 0.42f));
            confidence += 0.42f;
        }

        if (confidence >= 0.5f) {
            return Optional.of(KnowledgeFact.inferred(SemanticTag.BANK, Math.min(1.0f, confidence), evidence));
        }
        return Optional.empty();
    }

    private Optional<KnowledgeFact<SemanticTag>> classifyAltar(String name, List<String> actions) {
        List<Evidence> evidence = new ArrayList<>();
        float confidence = 0.0f;

        if (name.contains("altar") || name.contains("shrine")) {
            evidence.add(new Evidence("Name matches altar pattern: '" + name + "'", 0.6f));
            confidence += 0.6f;
        }
        if (actions.stream().anyMatch(a -> a.contains("pray") || a.contains("worship") || a.contains("recharge"))) {
            evidence.add(new Evidence("Has prayer interaction action", 0.38f));
            confidence += 0.38f;
        }

        if (confidence >= 0.5f) {
            return Optional.of(KnowledgeFact.inferred(SemanticTag.ALTAR, Math.min(1.0f, confidence), evidence));
        }
        return Optional.empty();
    }
}
