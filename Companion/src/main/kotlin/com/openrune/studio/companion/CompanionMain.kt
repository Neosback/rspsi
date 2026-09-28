package com.openrune.studio.companion

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

private const val DEFAULT_PORT = 8765

fun main() {
    val port = System.getenv("OPENRUNE_STUDIO_PORT")
        ?.toIntOrNull()
        ?.takeIf { it in 1..65535 }
        ?: DEFAULT_PORT

    embeddedServer(
        factory = Netty,
        host = "127.0.0.1",
        port = port,
        module = { companionModule() },
    ).start(wait = true)
}
