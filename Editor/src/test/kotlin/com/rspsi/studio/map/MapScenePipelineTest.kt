package com.rspsi.studio.map

import com.rspsi.editor.model.WorldDocument
import com.rspsi.editor.model.WorldRegion
import com.rspsi.editor.render.RenderConfigCompiler
import com.rspsi.editor.render.RenderSettingKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class MapScenePipelineTest {
    private fun compile(vararg edits: Pair<com.rspsi.editor.settings.SettingKey<*>, Any>) =
        RenderConfigCompiler().compile(
            edits.fold(RenderSettingKeys.registry().defaults()) { values, (key, value) ->
                @Suppress("UNCHECKED_CAST")
                values.with(key as com.rspsi.editor.settings.SettingKey<Any>, value)
            },
        )

    @Test
    fun onlyPlanShapingSettingsAdvanceTheRevision() {
        val base = RenderConfigState(compile(), 7L)

        // Plane choice and presentation are draw-time filters/uniforms: same revision.
        val plane = base.withConfig(compile(RenderSettingKeys.CURRENT_HEIGHT to 2))
        assertEquals(7L, plane.revision)
        val brighter = base.withConfig(compile(RenderSettingKeys.BRIGHTNESS to 0.9))
        assertEquals(7L, brighter.revision)
        assertEquals(0.9, brighter.config.brightness())

        // Hiding walls removes plan geometry: new revision.
        assertEquals(8L, base.withConfig(compile(RenderSettingKeys.WALLS_VISIBLE to false)).revision)

        // Nothing changed: the same state.
        assertSame(base, base.withConfig(compile()))
    }

    @Test
    fun contextWindowSpansTheActiveRegionAndEveryLoadedNeighbour() {
        val center = WorldRegion(50, 50, WorldDocument(64, 64, 4))
        val west = WorldRegion(49, 50, WorldDocument(64, 64, 4))
        val northEast = WorldRegion(51, 51, WorldDocument(64, 64, 4))

        val window = MapScenePipeline.contextWindow(
            center, mapOf(west.regionId() to west, northEast.regionId() to northEast))

        assertEquals(49, window.minRegionX())
        assertEquals(50, window.minRegionY())
        assertEquals(3, window.regionWidth())
        assertEquals(2, window.regionHeight())
        assertSame(center, window.regions()[center.regionId()])
        assertEquals(3, window.regions().size)
    }
}
