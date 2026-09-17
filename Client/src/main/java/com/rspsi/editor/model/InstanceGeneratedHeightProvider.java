package com.rspsi.editor.model;

/** Supplies opcode-0 heights when a source terrain tile is replayed in an instance. */
@FunctionalInterface
public interface InstanceGeneratedHeightProvider {
    int heightAt(int sourceWorldX, int sourceWorldY);

    static InstanceGeneratedHeightProvider required() {
        return (x, y) -> {
            throw new IllegalStateException(
                    "Instance terrain contains generated heights; provide an instance height provider");
        };
    }
}
