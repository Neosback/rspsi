package com.rspsi.editor.simulation.state;

import com.rspsi.editor.model.TileCoordinate;

/**
 * Universal provider of runtime state, allowing interchangeable sources:
 * manual inspector editing, deterministic fake simulation, live client synchronization, or rsprox playback.
 */
public interface RuntimeStateProvider {

    RuntimeState state();

    void setVarp(int varpId, int value);

    void setVarc(int varcId, Object value);

    void setLocState(TileCoordinate coordinate, int state);

    void reset();
}
