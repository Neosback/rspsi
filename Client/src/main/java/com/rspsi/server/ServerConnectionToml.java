package com.rspsi.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Small dependency-free reader/writer for the Studio server connection TOML subset. */
public final class ServerConnectionToml {
    private ServerConnectionToml() {
    }

    public static ServerConnection read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        String section = "";
        String root = null;
        String adapter = OpenRuneServerAdapter.ID;
        String fingerprint = "";
        Map<ServerPathKey, String> paths = new LinkedHashMap<>();
        Map<String, List<String>> commands = new LinkedHashMap<>();
        for (String original : Files.readAllLines(file)) {
            String line = stripComment(original).trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0) throw new IOException("Invalid connection TOML line: " + original);
            String key = line.substring(0, equals).trim();
            String value = unquote(line.substring(equals + 1).trim());
            switch (section) {
                case "server" -> {
                    if (key.equals("root")) root = value;
                    else if (key.equals("adapter")) adapter = value;
                    else if (key.equals("fingerprint")) fingerprint = value;
                    else throw new IOException("Unknown [server] key: " + key);
                }
                case "paths" -> paths.put(ServerPathKey.fromConfigName(key), value);
                case "commands" -> commands.put(key, tokenizeCommand(value));
                default -> throw new IOException("Unknown connection TOML section: " + section);
            }
        }
        if (root == null || root.isBlank()) throw new IOException("Connection TOML is missing server.root");
        return new ServerConnection(Path.of(root), adapter, paths, commands, fingerprint);
    }

    public static void write(Path file, ServerConnection connection) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(connection, "connection");
        StringBuilder text = new StringBuilder();
        text.append("[server]\n");
        text.append("adapter = ").append(quote(connection.adapterId())).append('\n');
        if (!connection.expectedFingerprint().isEmpty()) {
            text.append("fingerprint = ").append(quote(connection.expectedFingerprint())).append('\n');
        }
        text.append("root = ").append(quote(connection.root().toString())).append("\n\n");
        text.append("[paths]\n");
        connection.pathOverrides().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> text.append(entry.getKey().configName()).append(" = ")
                        .append(quote(entry.getValue())).append('\n'));
        text.append("\n[commands]\n");
        connection.commandOverrides().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> text.append(entry.getKey()).append(" = ")
                        .append(quote(String.join(" ", entry.getValue()))).append('\n'));
        Files.createDirectories(file.toAbsolutePath().normalize().getParent());
        Files.writeString(file, text.toString());
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) quoted = !quoted;
            if (c == '#' && !quoted) return line.substring(0, i);
        }
        return line;
    }

    private static String unquote(String value) throws IOException {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1)
                    .replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            return value;
        }
        if (value.isBlank()) throw new IOException("Empty connection TOML value");
        return value;
    }

    private static List<String> tokenizeCommand(String value) throws IOException {
        if (value.startsWith("[") && value.endsWith("]")) {
            String body = value.substring(1, value.length() - 1).trim();
            if (body.isEmpty()) return List.of();
            List<String> tokens = new ArrayList<>();
            for (String token : body.split(",")) tokens.add(unquote(token.trim()));
            return List.copyOf(tokens);
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (quoted) throw new IOException("Unclosed command quote: " + value);
        if (current.length() > 0) tokens.add(current.toString());
        return List.copyOf(tokens);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
