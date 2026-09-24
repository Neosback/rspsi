package com.rspsi.editor.plugin;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class EditorTaskServiceTest {

    @Test
    void trackedAsyncTaskCompletesThroughHostExecutor() throws Exception {
        EditorTaskService tasks = new EditorTaskService();
        try (EditorExecutionService execution = new EditorExecutionService("task-test")) {
            int value = tasks.supplyAsync(
                    execution, "build", "Build preview", () -> 42)
                    .get(2, TimeUnit.SECONDS);

            assertEquals(42, value);
            EditorTaskService.TaskSnapshot snapshot = tasks.snapshots().get(0);
            assertEquals(EditorTaskService.TaskState.COMPLETE, snapshot.state());
            assertEquals(1.0, snapshot.progress());
        }
    }

    @Test
    void trackedAsyncFailureMarksTaskFailed() {
        EditorTaskService tasks = new EditorTaskService();
        try (EditorExecutionService execution = new EditorExecutionService("task-failure")) {
            assertThrows(CompletionException.class, () ->
                    tasks.runAsync(execution, "bad", "Bad task",
                                    () -> { throw new IllegalStateException("boom"); })
                            .join());

            EditorTaskService.TaskSnapshot snapshot = tasks.snapshots().get(0);
            assertEquals(EditorTaskService.TaskState.FAILED, snapshot.state());
            assertEquals("boom", snapshot.message());
        }
    }
}
