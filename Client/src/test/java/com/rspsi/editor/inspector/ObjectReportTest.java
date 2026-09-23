package com.rspsi.editor.inspector;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectReportTest {
    private static final DefinitionProvider DEFINITIONS = new DefinitionProvider() {
        @Override
        public Optional<ObjectDefinitionView> object(int id) {
            return switch (id) {
                case 10586 -> Optional.of(new ObjectDefinitionView(id, "null", 1, 1, List.of(),
                        new int[0], new int[0], -1, false, 3984, -1, new int[]{1124, 4483, -1}, -1));
                case 1124 -> Optional.of(new ObjectDefinitionView(id, "Bush", 1, 1, List.of(),
                        new int[]{1565}, new int[]{10}, -1, false));
                case 44603 -> Optional.of(new ObjectDefinitionView(id, "null", 1, 1, List.of(),
                        new int[]{2214}, new int[]{0}, -1, false));
                default -> Optional.empty();
            };
        }

        @Override
        public Optional<ObjectCollisionView> objectCollision(int id) {
            return id == 44603 ? Optional.of(new ObjectCollisionView(id, 1, 1, 1, true, false)) : Optional.empty();
        }

        @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
        @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
    };

    @Test
    void multilocReportNamesEveryStateAndItsVariable() {
        String text = ObjectReport.forDefinition(10586, DEFINITIONS).toText();

        assertTrue(text.contains("varbit 3984"), text);
        assertTrue(text.contains("0 = Bush (1124)"), text);
        assertTrue(text.contains("default = nothing"), text);
        assertTrue(text.contains("unnamed"), "the client sentinel name is explained, not shown raw");
    }

    @Test
    void invisibleWallReportExplainsWhyNothingIsDrawn() {
        ObjectReport report = ObjectReport.forPlacement(new WorldObject(44603, 0, 0, 0, 38, 36), DEFINITIONS);

        assertTrue(report.toText().contains("Wall edges: west"), report.toText());
        assertTrue(report.toText().contains("Blocks walking: yes"), report.toText());
        assertTrue(report.sections().stream().flatMap(section -> section.rows().stream())
                .anyMatch(row -> row.warning() && row.label().equals("Draws")),
                "a loc that draws nothing is flagged as a warning row");
    }
}
