package com.rspsi.editor.transform;

import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.WorldFragment;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldFragmentTransformerCompatibilityTest {

    @Test
    void resultRemainsRecordWithDefensiveImmutableDiagnostics() {
        WorldFragment fragment = new WorldFragment(
                new TileBounds(0, 0, 0, 0),
                List.of(),
                List.of());
        var diagnostic = new WorldFragmentTransformResult.Diagnostic(
                WorldFragmentTransformResult.DiagnosticCode.OBJECT_MODEL_MIRROR_NOT_NATIVE,
                "warning");
        var source = new ArrayList<>(List.of(diagnostic));

        WorldFragmentTransformResult result =
                new WorldFragmentTransformResult(fragment, source);

        assertTrue(WorldFragmentTransformResult.class.isRecord());
        assertSame(fragment, result.fragment());
        assertEquals(List.of(diagnostic), result.diagnostics());

        source.clear();
        assertEquals(List.of(diagnostic), result.diagnostics());
        assertThrows(UnsupportedOperationException.class,
                () -> result.diagnostics().clear());
    }

    @Test
    void resultPreservesExplicitNullFailures() {
        WorldFragment fragment = new WorldFragment(
                new TileBounds(0, 0, 0, 0),
                List.of(),
                List.of());

        NullPointerException fragmentFailure = assertThrows(
                NullPointerException.class,
                () -> new WorldFragmentTransformResult(null, List.of()));
        assertEquals("fragment", fragmentFailure.getMessage());

        NullPointerException diagnosticsFailure = assertThrows(
                NullPointerException.class,
                () -> new WorldFragmentTransformResult(fragment, null));
        assertEquals("diagnostics", diagnosticsFailure.getMessage());
    }

    @Test
    void diagnosticRemainsRecordAndPreservesValidation() {
        assertTrue(WorldFragmentTransformResult.Diagnostic.class.isRecord());

        NullPointerException codeFailure = assertThrows(
                NullPointerException.class,
                () -> new WorldFragmentTransformResult.Diagnostic(null, "warning"));
        assertEquals("code", codeFailure.getMessage());

        NullPointerException messageFailure = assertThrows(
                NullPointerException.class,
                () -> new WorldFragmentTransformResult.Diagnostic(
                        WorldFragmentTransformResult.DiagnosticCode.OBJECT_MODEL_MIRROR_NOT_NATIVE,
                        null));
        assertEquals("message", messageFailure.getMessage());

        IllegalArgumentException blankFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new WorldFragmentTransformResult.Diagnostic(
                        WorldFragmentTransformResult.DiagnosticCode.OBJECT_MODEL_MIRROR_NOT_NATIVE,
                        "   "));
        assertEquals("Transform diagnostic cannot be blank", blankFailure.getMessage());
    }

    @Test
    void transformerKeepsStaticSurfaceAndPackagePrivateCornerHelper() throws Exception {
        assertTrue(Modifier.isPublic(WorldFragmentTransformer.class.getModifiers()));
        assertTrue(Modifier.isFinal(WorldFragmentTransformer.class.getModifiers()));

        var constructor = WorldFragmentTransformer.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));

        var transform = WorldFragmentTransformer.class.getDeclaredMethod(
                "transform",
                WorldFragment.class,
                WorldFragmentTransform.class,
                ObjectFootprintResolver.class);
        assertTrue(Modifier.isPublic(transform.getModifiers()));
        assertTrue(Modifier.isStatic(transform.getModifiers()));

        var corners = WorldFragmentTransformer.class.getDeclaredMethod(
                "transformCorners",
                int.class,
                int.class,
                int.class,
                int.class,
                WorldFragmentTransform.class);
        int modifiers = corners.getModifiers();
        assertTrue(Modifier.isStatic(modifiers));
        assertFalse(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isProtected(modifiers));
        assertFalse(Modifier.isPrivate(modifiers));
    }

    @Test
    void cornerTransformStillUsesSwSeNeNwOrdering() {
        assertArrayEquals(
                new int[]{2, 3, 4, 1},
                WorldFragmentTransformer.transformCorners(
                        1, 2, 3, 4,
                        WorldFragmentTransform.rotate(1)));
    }

    @Test
    void transformPreservesPublicNullFailureMessages() {
        WorldFragment fragment = new WorldFragment(
                new TileBounds(0, 0, 0, 0),
                List.of(),
                List.of());
        WorldFragmentTransform transform = WorldFragmentTransform.identity();
        ObjectFootprintResolver footprints = object -> java.util.Optional.empty();

        NullPointerException fragmentFailure = assertThrows(
                NullPointerException.class,
                () -> WorldFragmentTransformer.transform(null, transform, footprints));
        assertEquals("fragment", fragmentFailure.getMessage());

        NullPointerException transformFailure = assertThrows(
                NullPointerException.class,
                () -> WorldFragmentTransformer.transform(fragment, null, footprints));
        assertEquals("transform", transformFailure.getMessage());

        NullPointerException footprintsFailure = assertThrows(
                NullPointerException.class,
                () -> WorldFragmentTransformer.transform(fragment, transform, null));
        assertEquals("footprints", footprintsFailure.getMessage());
    }
}
