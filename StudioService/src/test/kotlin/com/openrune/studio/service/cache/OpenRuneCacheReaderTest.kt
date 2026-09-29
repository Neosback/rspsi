package com.openrune.studio.service.cache

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenRuneCacheReaderTest {
    @Test
    fun rejectsMissingCacheDirectoryBeforeInvokingFileStore() {
        val missing = Files.createTempDirectory("openrune-cache-reader").resolve("missing")
        val failure = assertFailsWith<IllegalArgumentException> {
            OpenRuneCacheReader().inspect(missing)
        }

        assertTrue(failure.message.orEmpty().contains("not a directory"))
    }
}
