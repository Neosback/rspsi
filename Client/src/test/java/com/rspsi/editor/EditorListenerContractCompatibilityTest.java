package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.selection.Selection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EditorListenerContractCompatibilityTest {

    @Test
    void selectionListenerRemainsJavaFunctionalInterfaceAndAcceptsNull() throws Exception {
        assertFunctionalInterface(SelectionChangeListener.class, Selection.class);

        AtomicReference<Selection> observed = new AtomicReference<>();
        SelectionChangeListener listener = observed::set;

        assertDoesNotThrow(() -> listener.changed(null));
        assertNull(observed.get());
    }

    @Test
    void sessionChangeListenerRemainsJavaFunctionalInterfaceWithExactGenericSignature()
            throws Exception {
        assertFunctionalInterface(SessionChangeListener.class, Set.class);

        var method = SessionChangeListener.class.getDeclaredMethod("changed", Set.class);
        var genericParameter = method.getGenericParameterTypes()[0];
        assertInstanceOf(ParameterizedType.class, genericParameter);

        ParameterizedType parameterized = (ParameterizedType) genericParameter;
        assertEquals(Set.class, parameterized.getRawType());
        assertArrayEquals(
                new Object[]{TileCoordinate.class},
                parameterized.getActualTypeArguments(),
                "Java signature must remain Set<TileCoordinate>, not Set<? extends TileCoordinate>");

        AtomicReference<Set<TileCoordinate>> observed = new AtomicReference<>();
        SessionChangeListener listener = observed::set;
        assertDoesNotThrow(() -> listener.changed(null));
        assertNull(observed.get());
    }

    @Test
    void sessionStateListenerRemainsJavaFunctionalInterfaceAndAcceptsNull() throws Exception {
        assertFunctionalInterface(SessionStateListener.class, EditorSession.class);

        AtomicReference<EditorSession> observed = new AtomicReference<>();
        SessionStateListener listener = observed::set;

        assertDoesNotThrow(() -> listener.changed(null));
        assertNull(observed.get());
    }

    private static void assertFunctionalInterface(
            Class<?> type,
            Class<?> parameterType
    ) throws Exception {
        assertTrue(type.isInterface());
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(type.isAnnotationPresent(FunctionalInterface.class));

        var method = type.getDeclaredMethod("changed", parameterType);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isAbstract(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());

        long abstractMethods = java.util.Arrays.stream(type.getMethods())
                .filter(methodValue -> Modifier.isAbstract(methodValue.getModifiers()))
                .filter(methodValue -> methodValue.getDeclaringClass() != Object.class)
                .count();
        assertEquals(1, abstractMethods);
    }
}
