package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class InstanceGeneratedHeightProviderInteropTest {

    @Test
    void remainsJavaFunctionalInterface() throws Exception {
        assertTrue(InstanceGeneratedHeightProvider.class.isInterface());
        assertTrue(InstanceGeneratedHeightProvider.class
                .isAnnotationPresent(FunctionalInterface.class));
        assertTrue(Modifier.isAbstract(
                InstanceGeneratedHeightProvider.class
                        .getMethod("heightAt", int.class, int.class)
                        .getModifiers()));

        InstanceGeneratedHeightProvider provider = (x, y) -> x * 31 + y;
        assertEquals(3200 * 31 + 3210, provider.heightAt(3200, 3210));
    }

    @Test
    void requiredRemainsTrueJavaStaticFactory() throws Exception {
        assertTrue(Modifier.isStatic(
                InstanceGeneratedHeightProvider.class
                        .getMethod("required")
                        .getModifiers()));

        InstanceGeneratedHeightProvider provider =
                InstanceGeneratedHeightProvider.required();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> provider.heightAt(3200, 3210));

        assertEquals(
                "Instance terrain contains generated heights; provide an instance height provider",
                failure.getMessage());
    }

    @Test
    void requiredFactoryPreservesFailureBehaviorAcrossCalls() {
        InstanceGeneratedHeightProvider first =
                InstanceGeneratedHeightProvider.required();
        InstanceGeneratedHeightProvider second =
                InstanceGeneratedHeightProvider.required();

        assertThrows(IllegalStateException.class, () -> first.heightAt(0, 0));
        assertThrows(IllegalStateException.class, () -> second.heightAt(0, 0));
    }
}
