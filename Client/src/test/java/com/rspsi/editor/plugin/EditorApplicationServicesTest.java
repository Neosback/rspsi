package com.rspsi.editor.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EditorApplicationServicesTest {
    @Test
    void taskServicePublishesACompletedSnapshot() {
        EditorTaskService tasks = new EditorTaskService();
        tasks.begin("cache.load", "Load cache");
        tasks.update("cache.load", 0.5, "Reading indexes");
        tasks.complete("cache.load", "Ready");

        EditorTaskService.TaskSnapshot snapshot = tasks.snapshots().get(0);
        assertEquals(EditorTaskService.TaskState.COMPLETE, snapshot.state());
        assertEquals(1.0, snapshot.progress());
        assertEquals("Ready", snapshot.message());
        assertThrows(IllegalArgumentException.class,
                () -> tasks.update("missing", 0.1, "No task"));
    }

    @Test
    void notificationServiceKeepsOnlyTheMostRecentHundredMessages() {
        EditorNotificationService notifications = new EditorNotificationService();
        for (int index = 0; index < 101; index++) {
            notifications.info("Message " + index, "Body " + index);
        }

        assertEquals(100, notifications.recent().size());
        assertEquals("Message 1", notifications.recent().get(0).title());
        assertEquals("Message 100", notifications.recent().get(99).title());
    }
}
