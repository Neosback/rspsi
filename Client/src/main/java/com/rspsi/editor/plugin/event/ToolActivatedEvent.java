package com.rspsi.editor.plugin.event;

public record ToolActivatedEvent(String previousToolId, String activeToolId) {
    public ToolActivatedEvent {
        previousToolId = previousToolId == null ? "" : previousToolId;
        activeToolId = activeToolId == null ? "" : activeToolId;
    }
}
