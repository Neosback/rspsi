package com.rspsi.editor.paste;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.WorldRegionSessionWindow;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorldFragmentPastePlannerCompatibilityTest {
    private static final int REGION_X = 50;
    private static final int REGION_Y = 50;
    private static final int REGION_ID = (REGION_X << 8) | REGION_Y;

    @Test
    void plannerKeepsUtilityClassStaticAbi() throws Exception {
        assertTrue(Modifier.isPublic(WorldFragmentPastePlanner.class.getModifiers()));
        assertTrue(Modifier.isFinal(WorldFragmentPastePlanner.class.getModifiers()));

        var constructor = WorldFragmentPastePlanner.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));

        var producer = WorldFragmentPastePlanner.class.getDeclaredField("PRODUCER_ID");
        int producerModifiers = producer.getModifiers();
        assertTrue(Modifier.isPublic(producerModifiers));
        assertTrue(Modifier.isStatic(producerModifiers));
        assertTrue(Modifier.isFinal(producerModifiers));
        assertEquals("studio.fragment.paste", producer.get(null));

        var defaultPlan = WorldFragmentPastePlanner.class.getDeclaredMethod(
                "plan",
                WorldRegionSessionWindow.class,
                WorldFragment.class,
                int.class,
                int.class,
                WorldFragmentPastePolicy.class);
        assertTrue(Modifier.isPublic(defaultPlan.getModifiers()));
        assertTrue(Modifier.isStatic(defaultPlan.getModifiers()));

        var describedPlan = WorldFragmentPastePlanner.class.getDeclaredMethod(
                "plan",
                WorldRegionSessionWindow.class,
                WorldFragment.class,
                int.class,
                int.class,
                WorldFragmentPastePolicy.class,
                String.class);
        assertTrue(Modifier.isPublic(describedPlan.getModifiers()));
        assertTrue(Modifier.isStatic(describedPlan.getModifiers()));
    }

    @Test
    void plannerPreservesValidationOrderAndNullMessages() {
        Fixture fixture = fixture();
        WorldFragment fragment = emptyFragment();
        WorldFragmentPastePolicy policy = WorldFragmentPastePolicy.replaceAll();

        NullPointerException windowFailure = assertThrows(
                NullPointerException.class,
                () -> WorldFragmentPastePlanner.plan(
                        null, null, -1, -1, null, null));
        assertEquals("window", windowFailure.getMessage());

        NullPointerException fragmentFailure = assertThrows(
                NullPointerException.class,
                () -> WorldFragmentPastePlanner.plan(
                        fixture.window(), null, -1, -1, null, null));
        assertEquals("fragment", fragmentFailure.getMessage());

        NullPointerException policyFailure = assertThrows(
                NullPointerException.class,
                () -> WorldFragmentPastePlanner.plan(
                        fixture.window(), fragment, -1, -1, null, null));
        assertEquals("policy", policyFailure.getMessage());

        IllegalArgumentException targetFailure = assertThrows(
                IllegalArgumentException.class,
                () -> WorldFragmentPastePlanner.plan(
                        fixture.window(), fragment, -1, 0, policy, null));
        assertEquals(
                "Paste target coordinates cannot be negative",
                targetFailure.getMessage());

        NullPointerException descriptionFailure = assertThrows(
                NullPointerException.class,
                () -> WorldFragmentPastePlanner.plan(
                        fixture.window(), fragment, 0, 0, policy, null));
        assertEquals("description", descriptionFailure.getMessage());
    }

    @Test
    void plannerPreservesJavaStringTrimSemanticsForDescriptions() {
        Fixture fixture = fixture();
        WorldFragment fragment = emptyFragment();
        WorldFragmentPastePolicy policy = WorldFragmentPastePolicy.replaceAll();

        var trimmed = WorldFragmentPastePlanner.plan(
                fixture.window(),
                fragment,
                0,
                0,
                policy,
                "  custom paste  ");
        assertEquals("custom paste", trimmed.candidatePlan().description());

        IllegalArgumentException blankFailure = assertThrows(
                IllegalArgumentException.class,
                () -> WorldFragmentPastePlanner.plan(
                        fixture.window(),
                        fragment,
                        0,
                        0,
                        policy,
                        "   "));
        assertEquals("Paste description cannot be blank", blankFailure.getMessage());

        // java.lang.String.trim() does not remove NBSP. Keep that historical behavior rather
        // than changing validation through Kotlin's broader whitespace trimming.
        String nonBreakingSpace = "\u00A0";
        var preserved = WorldFragmentPastePlanner.plan(
                fixture.window(),
                fragment,
                0,
                0,
                policy,
                nonBreakingSpace);
        assertEquals(nonBreakingSpace, preserved.candidatePlan().description());
    }

    private static WorldFragment emptyFragment() {
        return new WorldFragment(
                new TileBounds(0, 0, 0, 0),
                List.of(),
                List.of());
    }

    private static Fixture fixture() {
        WorldDocument world = new WorldDocument(64, 64, 1);
        WorldRegion region = new WorldRegion(REGION_X, REGION_Y, world);
        WorldRegionWindow regions = new WorldRegionWindow(
                REGION_X,
                REGION_Y,
                1,
                1,
                Map.of(REGION_ID, region));
        WorldRegionSessionWindow window = new WorldRegionSessionWindow(
                regions,
                Map.of(REGION_ID, new EditorSession(world, region.window())));
        return new Fixture(window);
    }

    private record Fixture(WorldRegionSessionWindow window) {
    }
}
