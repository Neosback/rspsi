package com.rspsi.editor.settings;

import com.rspsi.editor.plugin.ContributionOwner;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsServiceTest {
    private static SettingSpec<Integer> pathWidthSpec() {
        SettingKey<Integer> key = new SettingKey<>("plugin.smartroads.path-width", Integer.class);
        return SettingSpec.integer(key, 3, 1, 12, SettingScope.VIEWPORT,
                "Path width", "Width in tiles", Set.of(SettingInvalidation.REDRAW));
    }

    @Test
    void registeredSettingIsUsableAndOwnershipIsQueryable() {
        SettingsService service = new SettingsService(new SettingsStore(new SettingsRegistry()));
        ContributionOwner owner = ContributionOwner.plugin("org.example.smartroads");
        SettingSpec<Integer> spec = pathWidthSpec();

        SettingHandle handle = service.register(owner, spec);

        assertEquals(3, service.get(spec.key()));
        service.set(spec.key(), 7);
        assertEquals(7, service.get(spec.key()));
        assertEquals(owner, service.ownerOf(spec.key().id()).orElseThrow());
        assertTrue(service.settingsOwnedBy(owner).contains(spec));

        handle.close();
    }

    @Test
    void closingTheHandleUnregistersAndClearsStoredOverrides() {
        SettingsService service = new SettingsService(new SettingsStore(new SettingsRegistry()));
        SettingSpec<Integer> spec = pathWidthSpec();
        SettingHandle handle = service.register(ContributionOwner.plugin("org.example.smartroads"), spec);
        service.set(spec.key(), 9);

        handle.close();

        assertThrows(IllegalArgumentException.class, () -> service.get(spec.key()));
        assertTrue(service.ownerOf(spec.key().id()).isEmpty());

        // The id must be reusable, and it must not resurrect the old override value.
        SettingHandle secondHandle = service.register(ContributionOwner.plugin("org.example.smartroads"), spec);
        assertEquals(3, service.get(spec.key()));
        secondHandle.close();
    }

    @Test
    void closingTheHandleTwiceIsANoOp() {
        SettingsService service = new SettingsService(new SettingsStore(new SettingsRegistry()));
        SettingSpec<Integer> spec = pathWidthSpec();
        SettingHandle handle = service.register(ContributionOwner.plugin("org.example.smartroads"), spec);

        handle.close();
        handle.close();

        assertFalse(service.ownerOf(spec.key().id()).isPresent());
    }

    @Test
    void duplicateIdIsRejectedJustLikeTheUnderlyingRegistry() {
        SettingsService service = new SettingsService(new SettingsStore(new SettingsRegistry()));
        SettingSpec<Integer> spec = pathWidthSpec();
        SettingHandle handle = service.register(ContributionOwner.plugin("a"), spec);

        assertThrows(IllegalArgumentException.class,
                () -> service.register(ContributionOwner.plugin("b"), spec));

        handle.close();
    }
}
