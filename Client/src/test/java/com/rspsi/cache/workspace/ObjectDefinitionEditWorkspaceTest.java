package com.rspsi.cache.workspace;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionEditValue;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
        workspace.markPublished(7, published);

        assertEquals(1, workspace.modifiedCount());
        assertEquals(0, workspace.unpublishedCount());

        first.setField("name",
                ObjectDefinitionEditValue.stringValue("Newer"));
        assertEquals(1, workspace.unpublishedCount());
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
            if (current.equals(ORIGINAL)) return false;
            return published == null || !current.equals(published);
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
