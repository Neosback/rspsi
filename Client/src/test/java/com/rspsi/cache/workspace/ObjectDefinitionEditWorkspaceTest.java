package com.rspsi.cache.workspace;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionEditValue;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectDefinitionEditWorkspaceTest {

    @Test
    void retainsStableTransactionsAndTracksPublishedState() {
        FakeProvider provider = new FakeProvider();
        ObjectDefinitionEditWorkspace workspace =
                new ObjectDefinitionEditWorkspace(provider);

        ObjectDefinitionEditTransaction first =
                workspace.transaction(7).orElseThrow();
        ObjectDefinitionEditTransaction again =
                workspace.transaction(7).orElseThrow();

        assertSame(first, again);
        assertEquals(1, provider.editCalls);

        first.setField("name",
                ObjectDefinitionEditValue.stringValue("Published"));
        assertEquals(1, workspace.modifiedCount());
        assertEquals(1, workspace.unpublishedCount());

        ObjectDefinitionRawView published = first.preview();
        Path output = Path.of("build", "development-cache");
        workspace.markPublished(output, 7, published);

        assertEquals(
                output.toAbsolutePath().normalize(),
                workspace.publicationTarget().orElseThrow());
        assertEquals(1, workspace.modifiedCount());
        assertEquals(0, workspace.unpublishedCount());
        assertThrows(
                IllegalArgumentException.class,
                () -> workspace.markPublished(
                        Path.of("build", "different-cache"), 7, published));

        first.setField("name",
                ObjectDefinitionEditValue.stringValue("Newer"));
        assertEquals(1, workspace.unpublishedCount());

        first.setField("name",
                ObjectDefinitionEditValue.stringValue("Original"));
        assertEquals(0, workspace.modifiedCount());
        assertEquals(1, workspace.unpublishedCount(),
                "the selected output still needs the published edit reverted");
    }


    @Test
    void restoresPublishedSnapshotLazilyAsTheCurrentPreview() {
        FakeProvider provider = new FakeProvider();
        ObjectDefinitionEditWorkspace workspace =
                new ObjectDefinitionEditWorkspace(provider);
        Path output = Path.of("build", "restored-output");
        ObjectDefinitionRawView published = rawName("Published");

        workspace.restorePublication(output, Map.of(7, published));

        assertEquals(0, provider.editCalls,
                "restoring provenance must not eagerly create edit transactions");
        ObjectDefinitionEditTransaction transaction =
                workspace.transaction(7).orElseThrow();

        assertEquals(1, provider.editCalls);
        assertEquals(published, transaction.preview());
        assertEquals(published, transaction.publishedPreview().orElseThrow());
        assertTrue(transaction.dirty(),
                "the restored published preview still differs from the source");
        assertFalse(transaction.hasUnpublishedChanges());
        assertEquals(0, workspace.unpublishedCount());
        assertEquals(Map.of(7, published), workspace.publishedSnapshots());
    }

    @Test
    void restoreDoesNotOverwriteAnEditMadeBeforeHydrationCompletes() {
        FakeProvider provider = new FakeProvider();
        ObjectDefinitionEditWorkspace workspace =
                new ObjectDefinitionEditWorkspace(provider);
        ObjectDefinitionEditTransaction transaction =
                workspace.transaction(7).orElseThrow();
        transaction.setField(
                "name", ObjectDefinitionEditValue.stringValue("User edit"));

        ObjectDefinitionRawView published = rawName("Previously published");
        workspace.restorePublication(
                Path.of("build", "restored-output"),
                Map.of(7, published));

        assertEquals("User edit", transaction.preview().fields().get(0).value());
        assertEquals(published, transaction.publishedPreview().orElseThrow());
        assertTrue(transaction.hasUnpublishedChanges());
    }

    private static ObjectDefinitionRawView rawName(String name) {
        return new ObjectDefinitionRawView(
                7,
                List.of(new ObjectDefinitionRawView.Field(
                        "name",
                        "2",
                        ObjectDefinitionRawView.ValueType.STRING,
                        name)),
                List.of());
    }

    private static final class FakeProvider implements DefinitionProvider {
        private int editCalls;

        @Override
        public Optional<ObjectDefinitionView> object(int id) {
            return Optional.empty();
        }

        @Override
        public Optional<ObjectDefinitionEditTransaction> editObject(int id) {
            editCalls++;
            return id == 7
                    ? Optional.of(new FakeTransaction())
                    : Optional.empty();
        }

        @Override
        public Optional<FloorDefinitionView> underlay(int id) {
            return Optional.empty();
        }

        @Override
        public Optional<FloorDefinitionView> overlay(int id) {
            return Optional.empty();
        }
    }

    private static final class FakeTransaction
            implements ObjectDefinitionEditTransaction {
        private static final ObjectDefinitionRawView ORIGINAL =
                new ObjectDefinitionRawView(
                        7,
                        List.of(new ObjectDefinitionRawView.Field(
                                "name",
                                "2",
                                ObjectDefinitionRawView.ValueType.STRING,
                                "Original")),
                        List.of());

        private String name = "Original";
        private ObjectDefinitionRawView published;

        @Override
        public int id() {
            return 7;
        }

        @Override
        public ObjectDefinitionRawView original() {
            return ORIGINAL;
        }

        @Override
        public ObjectDefinitionRawView preview() {
            return new ObjectDefinitionRawView(
                    7,
                    List.of(new ObjectDefinitionRawView.Field(
                            "name",
                            "2",
                            ObjectDefinitionRawView.ValueType.STRING,
                            name)),
                    List.of());
        }

        @Override
        public Set<String> dirtyFields() {
            return name.equals("Original") ? Set.of() : Set.of("name");
        }

        @Override
        public Set<Integer> dirtyParams() {
            return Set.of();
        }

        @Override
        public boolean hasUnpublishedChanges() {
            ObjectDefinitionRawView current = preview();
            return published == null
                    ? !current.equals(ORIGINAL)
                    : !current.equals(published);
        }

        @Override
        public Optional<ObjectDefinitionRawView> publishedPreview() {
            return Optional.ofNullable(published);
        }

        @Override
        public void markPublished(ObjectDefinitionRawView publishedPreview) {
            published = publishedPreview;
        }

        @Override
        public void setField(String fieldName, ObjectDefinitionEditValue value) {
            if (!fieldName.equals("name")) {
                throw new IllegalArgumentException("Unsupported field");
            }
            name = value.value();
        }

        @Override
        public void putParam(int paramId, ObjectDefinitionEditValue value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeParam(int paramId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reset() {
            name = "Original";
        }

        @Override
        public byte[] encodeValidated() {
            return name.getBytes(StandardCharsets.UTF_8);
        }
    }
}
