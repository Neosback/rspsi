package com.rspsi.server;

import java.util.List;

/** Supplies declarative build/process actions without coupling Studio to a build tool. */
@FunctionalInterface
public interface ServerBuildProvider {
    List<ServerBuildTask> tasks();
}
