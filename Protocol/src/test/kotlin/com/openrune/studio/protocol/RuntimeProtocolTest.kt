package com.openrune.studio.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RuntimeProtocolTest {
    @Test
    fun capabilityIdsAreUniqueAndNamespaced() {
        val ids = StudioCapabilities.all.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { '.' in it })
    }

    @Test
    fun invalidCapabilityIdsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            StudioCapability("Runtime Plugins")
        }
    }

    @Test
    fun runtimeSnapshotCanRepresentPluginEventAndCacheStateWithoutOpenRuneTypes() {
        val identity =
            RuntimeIdentity(
                protocolVersion = 1,
                runtimeId = "server-1",
                serverImplementation = "OpenRune Server",
                serverRevision = 240,
                javaVersion = "21",
                javaVendor = "Eclipse Adoptium",
                capabilities =
                    listOf(
                        StudioCapabilities.RuntimeIdentity,
                        StudioCapabilities.RuntimePlugins,
                        StudioCapabilities.RuntimeEvents,
                        StudioCapabilities.RuntimeCache,
                    ),
            )

        val snapshot =
            RuntimeSnapshot(
                generatedAtEpochMillis = 1000,
                identity = identity,
                lifecycle = RuntimeLifecycle(RuntimeLifecyclePhase.CONTENT_READY, 900),
                plugins =
                    listOf(
                        PluginIdentity(
                            id = "mining",
                            source = PluginSourceKind.BUILT_IN,
                            state = PluginState.LOADED,
                            scriptClasses =
                                listOf("org.rsmod.content.skills.mining.scripts.Mining"),
                        ),
                    ),
                scripts =
                    listOf(
                        ScriptIdentity(
                            className = "org.rsmod.content.skills.mining.scripts.Mining",
                            state = ScriptState.STARTED,
                            pluginId = "mining",
                        ),
                    ),
                eventRegistrations =
                    listOf(
                        EventRegistration(
                            eventType = "org.rsmod.api.player.events.interact.LocOpEvent",
                            kind = RuntimeEventKind.KEYED,
                            key = 52,
                            ownerPluginId = "mining",
                        ),
                    ),
                caches =
                    listOf(
                        RuntimeCacheState(
                            role = CacheRole.SERVER,
                            loaded = true,
                            revision = 240,
                            definitionCounts = mapOf("npc" to 100, "loc" to 200),
                        ),
                    ),
            )

        assertEquals(RuntimeLifecyclePhase.CONTENT_READY, snapshot.lifecycle.phase)
        assertEquals("mining", snapshot.plugins.single().id)
        assertEquals(52L, snapshot.eventRegistrations.single().key)
        assertEquals(240, snapshot.caches.single().revision)
    }

    @Test
    fun runtimeEventsCarryOpaqueNeutralAttributes() {
        val event =
            RuntimeEventEnvelope(
                sequence = 7,
                timestampEpochMillis = 2000,
                runtimeId = "server-1",
                type = RuntimeEventType.PLUGIN_CHANGED,
                subjectId = "example",
                attributes = mapOf("state" to "LOADED"),
            )

        assertEquals(7, event.sequence)
        assertEquals("LOADED", event.attributes["state"])
    }
}
