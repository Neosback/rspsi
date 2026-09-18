package com.rspsi.editor.plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Bounded, frontend-neutral notification queue for plugin feedback. */
public final class EditorNotificationService {
    private static final int MAX_NOTIFICATIONS = 100;
    private final Deque<Notification> notifications = new ArrayDeque<>();

    public synchronized void publish(Level level, String title, String message) {
        notifications.addLast(new Notification(level, title, message));
        while (notifications.size() > MAX_NOTIFICATIONS) notifications.removeFirst();
    }

    public void info(String title, String message) {
        publish(Level.INFO, title, message);
    }

    public void warning(String title, String message) {
        publish(Level.WARNING, title, message);
    }

    public void error(String title, String message) {
        publish(Level.ERROR, title, message);
    }

    public synchronized List<Notification> recent() {
        return List.copyOf(new ArrayList<>(notifications));
    }

    public synchronized void clear() {
        notifications.clear();
    }

    public enum Level { INFO, WARNING, ERROR }

    public record Notification(Level level, String title, String message) {
        public Notification {
            level = Objects.requireNonNull(level, "notification level");
            title = text(title, "notification title");
            message = text(message, "notification message");
        }

        private static String text(String value, String name) {
            String normalized = Objects.requireNonNull(value, name).trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
            return normalized;
        }
    }
}
