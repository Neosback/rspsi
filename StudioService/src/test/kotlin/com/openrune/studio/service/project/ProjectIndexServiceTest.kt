package com.openrune.studio.service.project

import com.openrune.studio.service.openrune.OpenRuneContentIndex
import com.openrune.studio.service.openrune.OpenRuneProjectInspection
import com.openrune.studio.service.openrune.OpenRuneSourceIndex
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectIndexServiceTest {
    @Test
    fun cachesUnchangedProjectAndBuildsOnDedicatedIndexThread() = runBlocking {
        val root = Files.createTempDirectory("project-index-cache")
        try {
            root.resolve("content").createDirectories()
            root.resolve("content/example.kt").writeText("class Example")

            val contentCalls = AtomicInteger()
            val sourceCalls = AtomicInteger()
            val sourceThread = AtomicReference<String>()

            ProjectIndexService(
                contentIndex = { path ->
                    contentCalls.incrementAndGet()
                    emptyContent(path)
                },
                sourceIndex = { path ->
                    sourceCalls.incrementAndGet()
                    sourceThread.set(Thread.currentThread().name)
                    emptySource(path)
                },
            ).use { service ->
                val project = session(root)

                val first = service.snapshot(project)
                val second = service.snapshot(project)

                assertEquals(1L, first.generation)
                assertEquals(first, second)
                assertEquals(1, contentCalls.get())
                assertEquals(1, sourceCalls.get())
                assertTrue(sourceThread.get().startsWith("openrune-studio-index"))

                root.resolve("content/example.kt").writeText("class ExampleChanged")
                val changed = service.snapshot(project)

                assertEquals(2L, changed.generation)
                assertEquals(2, contentCalls.get())
                assertEquals(2, sourceCalls.get())
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun explicitRefreshRebuildsEvenWhenFingerprintIsUnchanged() = runBlocking {
        val root = Files.createTempDirectory("project-index-refresh")
        try {
            root.resolve("content").createDirectories()
            val calls = AtomicInteger()

            ProjectIndexService(
                contentIndex = { path ->
                    calls.incrementAndGet()
                    emptyContent(path)
                },
                sourceIndex = ::emptySource,
            ).use { service ->
                val project = session(root)
                service.snapshot(project)
                val refreshed = service.refresh(project)

                assertEquals(2L, refreshed.generation)
                assertEquals(2, calls.get())
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun session(root: java.nio.file.Path): ProjectSession =
        ProjectSession(
            id = "project-1",
            root = root.toRealPath(),
            capabilities = emptyList(),
            inspection =
                OpenRuneProjectInspection(
                    root = root.toString(),
                    matched = true,
                    confidence = 100,
                    evidence = emptyList(),
                    capabilities = emptyList(),
                    locations = emptyMap(),
                ),
        )

    private fun emptyContent(path: java.nio.file.Path): OpenRuneContentIndex =
        OpenRuneContentIndex(
            root = path.toString(),
            moduleCount = 0,
            skillModuleCount = 0,
            gameValCount = 0,
            modules = emptyList(),
            gameVals = emptyList(),
            diagnostics = emptyList(),
        )

    private fun emptySource(path: java.nio.file.Path): OpenRuneSourceIndex =
        OpenRuneSourceIndex(
            root = path.toString(),
            fileCount = 0,
            factCount = 0,
            facts = emptyList(),
            diagnostics = emptyList(),
        )
}
