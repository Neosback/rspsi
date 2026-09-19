package com.rspsi.editor.knowledge.inference;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.knowledge.KnowledgeFact;
import com.rspsi.editor.knowledge.KnowledgeSource;
import com.rspsi.editor.knowledge.SemanticTag;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectSemanticClassifierTest {
    private final Map<Integer, ObjectDefinitionView> definitionsMap = new HashMap<>();
    private ObjectSemanticClassifier classifier;

    @BeforeEach
    void setUp() {
        definitionsMap.clear();
        DefinitionProvider provider = new DefinitionProvider() {
            @Override
            public Optional<ObjectDefinitionView> object(int id) {
                return Optional.ofNullable(definitionsMap.get(id));
            }

            @Override
            public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }

            @Override
            public Optional<FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
        };
        classifier = new ObjectSemanticClassifier(provider);
    }

    @Test
    void classifiesTreeWithWoodcuttingAction() {
        definitionsMap.put(100, new ObjectDefinitionView(100, "Oak tree", 1, 1, List.of("Chop down"), new int[]{1000}));
        WorldObject treeObj = new WorldObject(100, 10, 0, 0, 10, 10);

        List<KnowledgeFact<SemanticTag>> facts = classifier.classify(treeObj);
        assertFalse(facts.isEmpty());

        KnowledgeFact<SemanticTag> treeFact = facts.stream()
                .filter(f -> f.value().equals(SemanticTag.TREE))
                .findFirst()
                .orElseThrow();

        assertEquals(KnowledgeSource.INFERRED, treeFact.source());
        assertTrue(treeFact.confidence() >= 0.8f);
        assertFalse(treeFact.evidence().isEmpty());
    }

    @Test
    void classifiesMiningRock() {
        definitionsMap.put(200, new ObjectDefinitionView(200, "Iron rocks", 1, 1, List.of("Mine"), new int[]{2000}));
        WorldObject rockObj = new WorldObject(200, 10, 0, 0, 15, 15);

        List<KnowledgeFact<SemanticTag>> facts = classifier.classify(rockObj);
        assertTrue(facts.stream().anyMatch(f -> f.value().equals(SemanticTag.ROCK)));
    }

    @Test
    void classifiesDoorAndGate() {
        definitionsMap.put(300, new ObjectDefinitionView(300, "Wooden door", 1, 1, List.of("Open"), new int[]{3000}));
        WorldObject doorObj = new WorldObject(300, 0, 0, 0, 20, 20);

        List<KnowledgeFact<SemanticTag>> facts = classifier.classify(doorObj);
        assertTrue(facts.stream().anyMatch(f -> f.value().equals(SemanticTag.DOOR)));
    }

    @Test
    void classifiesAltar() {
        definitionsMap.put(400, new ObjectDefinitionView(400, "Chaos altar", 2, 2, List.of("Pray-at"), new int[]{4000}));
        WorldObject altarObj = new WorldObject(400, 10, 0, 0, 25, 25);

        List<KnowledgeFact<SemanticTag>> facts = classifier.classify(altarObj);
        assertTrue(facts.stream().anyMatch(f -> f.value().equals(SemanticTag.ALTAR)));
    }

    @Test
    void classifiesBankBooth() {
        definitionsMap.put(500, new ObjectDefinitionView(500, "Bank booth", 1, 1, List.of("Bank"), new int[]{5000}));
        WorldObject bankObj = new WorldObject(500, 10, 0, 0, 30, 30);

        List<KnowledgeFact<SemanticTag>> facts = classifier.classify(bankObj);
        assertTrue(facts.stream().anyMatch(f -> f.value().equals(SemanticTag.BANK)));
    }

    @Test
    void nonMatchingObjectReturnsEmpty() {
        definitionsMap.put(999, new ObjectDefinitionView(999, "Mysterious Orb", 1, 1, List.of("Examine"), new int[]{9000}));
        WorldObject genericObj = new WorldObject(999, 10, 0, 0, 5, 5);

        List<KnowledgeFact<SemanticTag>> facts = classifier.classify(genericObj);
        assertTrue(facts.isEmpty());
    }
}
