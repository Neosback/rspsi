package com.rspsi.cache.store;

import dev.openrune.cache.tools.CacheEnvironment;
import dev.openrune.cache.tools.FreshCache;
import dev.openrune.cache.tools.tasks.CacheTask;
import dev.openrune.cache.tools.progress.SilentCacheProgress;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Opt-in developer/verification utility that prepares an OSRS reference cache
 * through OpenRune FileStore's own OpenRS2/FreshCache tooling.
 *
 * <p>This deliberately lives in the OpenRune compatibility boundary. Neutral
 * editor code must never depend on FileStore tooling directly.</p>
 */
public final class OpenRuneReferenceCacheBootstrap {
    private OpenRuneReferenceCacheBootstrap() {
    }

    public static void prepare(Path output, int revision, int subRevision) {
        Objects.requireNonNull(output, "output");
        if (revision < 0) {
            throw new IllegalArgumentException("Revision cannot be negative");
        }
        new FreshCache(
                output.toFile(),
                new ArrayList<CacheTask>(),
                revision,
                subRevision,
                CacheEnvironment.LIVE,
                SilentCacheProgress.INSTANCE
        ).initialize();
    }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                    "Usage: OpenRuneReferenceCacheBootstrap <output-dir> <revision> [sub-revision]");
        }
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        int revision = Integer.parseInt(args[1]);
        int subRevision = args.length == 3 ? Integer.parseInt(args[2]) : -1;
        prepare(output, revision, subRevision);
        System.out.println("Prepared OpenRune reference cache: " + output
                + " (revision=" + revision + ", subRevision=" + subRevision + ")");
    }
}
