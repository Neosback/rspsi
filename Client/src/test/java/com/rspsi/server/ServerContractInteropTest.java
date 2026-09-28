package com.rspsi.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Locks the Java-facing shape of server contracts as their implementations move to Kotlin.
 */
class ServerContractInteropTest {
    @Test
    void pathKeyKeepsSerializedNameAndStaticLookup() {
        assertEquals("live_cache", ServerPathKey.LIVE_CACHE.configName());
        assertEquals(ServerPathKey.LIVE_CACHE, ServerPathKey.fromConfigName(" LIVE_CACHE "));
        assertThrows(IllegalArgumentException.class,
                () -> ServerPathKey.fromConfigName("not_a_server_path"));
    }

    @Test
    void buildProviderRemainsAJavaSam() {
        ServerBuildProvider provider = () -> List.of();
        assertTrue(provider.tasks().isEmpty());
    }

    @Test
    void migratedEnumsKeepTheirExistingConstants() {
        assertEquals(ServerCapability.BUILD_CACHE, ServerCapability.valueOf("BUILD_CACHE"));
        assertEquals(ServerContentKind.SERVER_SCRIPT, ServerContentKind.valueOf("SERVER_SCRIPT"));
        assertEquals(ServerIntegrationStatus.SUPPORTED_WITH_OVERRIDES,
                ServerIntegrationStatus.valueOf("SUPPORTED_WITH_OVERRIDES"));
    }
}
