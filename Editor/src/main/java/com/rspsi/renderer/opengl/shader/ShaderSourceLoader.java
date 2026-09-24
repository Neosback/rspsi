package com.rspsi.renderer.opengl.shader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads GLSL resources and resolves small, explicit {@code #include "path"} directives.
 *
 * <p>Shader sources live outside renderer Java code so the vanilla, picking,
 * debug and future material pipelines can share modules without duplicating
 * scene semantics.</p>
 */
public final class ShaderSourceLoader {
    private static final Pattern INCLUDE = Pattern.compile(
            "^\\s*#include\\s+\\\"([^\\\"]+)\\\"\\s*(?://.*)?$");

    private final ClassLoader classLoader;
    private final String root;

    public ShaderSourceLoader(String root) {
        this(defaultClassLoader(), root);
    }

    ShaderSourceLoader(ClassLoader classLoader, String root) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.root = normalizeRoot(root);
    }

    public LoadedShader load(String resource) {
        String path = normalizeResource(resource);
        StringBuilder output = new StringBuilder();
        resolve(path, new ArrayDeque<>(), output);
        return new LoadedShader(path, output.toString());
    }

    private void resolve(String path, Deque<String> stack, StringBuilder output) {
        if (stack.contains(path)) {
            StringBuilder cycle = new StringBuilder();
            for (String entry : stack) {
                if (!cycle.isEmpty()) cycle.append(" -> ");
                cycle.append(entry);
            }
            if (!cycle.isEmpty()) cycle.append(" -> ");
            cycle.append(path);
            throw new IllegalStateException("Cyclic shader include: " + cycle);
        }

        stack.addLast(path);
        String source = read(path);
        String[] lines = source.split("\\R", -1);
        for (String line : lines) {
            Matcher matcher = INCLUDE.matcher(line);
            if (!matcher.matches()) {
                output.append(line).append('\n');
                continue;
            }

            String include = resolveInclude(path, matcher.group(1));
            output.append("// begin include ").append(include).append('\n');
            resolve(include, stack, output);
            output.append("// end include ").append(include).append('\n');
        }
        stack.removeLast();
    }

    private String read(String path) {
        String resourcePath = root.isEmpty() ? path : root + "/" + path;
        try (InputStream stream = classLoader.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Shader resource not found: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read shader resource: " + resourcePath, exception);
        }
    }

    private static String resolveInclude(String owner, String include) {
        if (include.startsWith("/")) {
            return normalizeResource(include.substring(1));
        }
        int slash = owner.lastIndexOf('/');
        String parent = slash < 0 ? "" : owner.substring(0, slash + 1);
        return normalizeResource(parent + include);
    }

    private static String normalizeRoot(String root) {
        Objects.requireNonNull(root, "root");
        String normalized = root.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("Shader root cannot contain '..': " + root);
        }
        return normalized;
    }

    private static String normalizeResource(String resource) {
        Objects.requireNonNull(resource, "resource");
        String normalized = resource.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);

        Deque<String> segments = new ArrayDeque<>();
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) continue;
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Shader resource escapes shader root: " + resource);
                }
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Shader resource cannot be empty");
        }
        return String.join("/", segments);
    }

    private static ClassLoader defaultClassLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : ShaderSourceLoader.class.getClassLoader();
    }

    public record LoadedShader(String name, String source) {
        public LoadedShader {
            name = Objects.requireNonNull(name, "name");
            source = Objects.requireNonNull(source, "source");
        }
    }
}
