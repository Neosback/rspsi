package com.rspsi.editor.plugin.runtime;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small dependency-free semantic version used by the external plugin runtime. */
public record SemanticVersion(int major, int minor, int patch, String preRelease)
        implements Comparable<SemanticVersion> {
    private static final Pattern PATTERN = Pattern.compile(
            "^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

    public SemanticVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version numbers cannot be negative");
        }
        preRelease = preRelease == null ? "" : preRelease.trim();
    }

    public static SemanticVersion parse(String value) {
        String normalized = Objects.requireNonNull(value, "version").trim();
        Matcher matcher = PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid semantic version: " + value);
        }
        return new SemanticVersion(
                Integer.parseInt(matcher.group(1)),
                matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2)),
                matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3)),
                matcher.group(4) == null ? "" : matcher.group(4));
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int result = Integer.compare(major, other.major);
        if (result != 0) return result;
        result = Integer.compare(minor, other.minor);
        if (result != 0) return result;
        result = Integer.compare(patch, other.patch);
        if (result != 0) return result;
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0;
        if (preRelease.isEmpty()) return 1;
        if (other.preRelease.isEmpty()) return -1;
        return comparePreRelease(preRelease, other.preRelease);
    }

    private static int comparePreRelease(String first, String second) {
        String[] a = first.split("\\.");
        String[] b = second.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            if (i >= a.length) return -1;
            if (i >= b.length) return 1;
            boolean an = a[i].matches("\\d+");
            boolean bn = b[i].matches("\\d+");
            int result;
            if (an && bn) result = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
            else if (an != bn) result = an ? -1 : 1;
            else result = a[i].compareTo(b[i]);
            if (result != 0) return result;
        }
        return 0;
    }

    @Override
    public String toString() {
        String base = major + "." + minor + "." + patch;
        return preRelease.isEmpty() ? base : base + "-" + preRelease;
    }
}
