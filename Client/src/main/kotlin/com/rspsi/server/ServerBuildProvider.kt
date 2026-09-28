package com.rspsi.server

/** Supplies declarative build/process actions without coupling Studio to a build tool. */
fun interface ServerBuildProvider {
    fun tasks(): List<ServerBuildTask>
}
