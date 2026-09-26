package com.rspsi.editor.model

/**
 * Supplies opcode-0 terrain heights when a source tile is replayed into an instance.
 *
 * This remains a single-abstract-method contract so existing Java and Kotlin lambda call sites
 * keep working without adapters. Height generation stays cache-independent at this boundary.
 */
@java.lang.FunctionalInterface
fun interface InstanceGeneratedHeightProvider {
    fun heightAt(
        sourceWorldX: Int,
        sourceWorldY: Int,
    ): Int

    companion object {
        /**
         * Returns the compatibility provider used when generated heights are not configured.
         *
         * It fails only when invoked, preserving the previous lazy error behavior.
         */
        @JvmStatic
        fun required(): InstanceGeneratedHeightProvider =
            InstanceGeneratedHeightProvider { _, _ ->
                throw IllegalStateException(
                    "Instance terrain contains generated heights; provide an instance height provider",
                )
            }
    }
}
