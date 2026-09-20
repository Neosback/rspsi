package com.rspsi.editor.plugin.event;

public record SceneRenderedEvent(String fingerprint, long frameNanos) {
    public SceneRenderedEvent {
        fingerprint = fingerprint == null ? "" : fingerprint;
        if (frameNanos < 0) throw new IllegalArgumentException("frameNanos cannot be negative");
    }
}
