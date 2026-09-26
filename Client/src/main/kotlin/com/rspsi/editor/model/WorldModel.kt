package com.rspsi.editor.model

/**
 * Deprecated source-compatible bridge to the canonical [WorldDocument] name.
 *
 * New code should construct [WorldDocument] directly. This type remains only while older callers
 * complete that rename, so its constructors, inheritance, static default-plane constant, and
 * deprecation metadata are intentionally preserved.
 */
@kotlin.Deprecated("Use WorldDocument")
@java.lang.Deprecated
class WorldModel : WorldDocument {
    constructor(
        width: Int,
        length: Int,
    ) : super(width, length)

    constructor(
        width: Int,
        length: Int,
        planes: Int,
    ) : super(width, length, planes)

    companion object {
        const val DEFAULT_PLANES: Int = WorldDocument.DEFAULT_PLANES
    }
}
