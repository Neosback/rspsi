package com.rspsi.studio.map

import com.rspsi.editor.render.RenderConfig

/**
 * The compiled render configuration plus a revision that advances only when the GPU plan's
 * geometry must change.
 *
 * The settings store also holds tool and HUD values, and planes, bridges and presentation
 * (brightness, fog, MSAA) are applied per frame by the viewport, so most setting changes
 * keep the revision and never rebuild or re-upload the plan.
 */
@JvmRecord
data class RenderConfigState(val config: RenderConfig, val revision: Long) {
    /** The state after settings compile to [next]: a new revision only if plan geometry changes. */
    fun withConfig(next: RenderConfig): RenderConfigState = when {
        next.forPlan() != config.forPlan() -> RenderConfigState(next, revision + 1)
        next != config -> RenderConfigState(next, revision)
        else -> this
    }
}
