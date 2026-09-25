package com.rspsi.server.openrune;

import com.rspsi.editor.integration.semantic.ServerObjectSemanticIndex;
import com.rspsi.editor.integration.semantic.ServerObjectSemanticOverlay;
import com.rspsi.editor.integration.semantic.SourceSpan;
import com.rspsi.editor.symbols.SymbolNamespace;
import com.rspsi.editor.symbols.SymbolProvider;
import com.rspsi.server.ServerProjectInspection;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Structured reader for OpenRune source-controlled {@code [[object]]} server-config overlays.
 *
 * <p>The server cache's {@code ObjectServerType.contentGroup} is produced from these overlays by
 * OpenRune's PackServerConfig/ObjectServerCodec path. Studio reads the authored source here rather
 * than guessing server semantics from LIVE/client object definitions.</p>
 */
public final class OpenRuneObjectOverlayIndexer {

    public ServerObjectSemanticIndex index(
            ServerProjectInspection inspection,
            SymbolProvider symbols) {
        Objects.requireNonNull(inspection, "inspection");
        Objects.requireNonNull(symbols, "symbols");

        Set<Path> files = new LinkedHashSet<>();
        inspection.content().forEach(entry -> {
            String name = entry.path().getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".toml")) files.add(entry.path());
        });

        return indexFiles(files, symbols);
    }

    /** Indexes an explicit TOML set; used by bundled-source acceptance and focused tooling. */
    public ServerObjectSemanticIndex indexFiles(
            Iterable<Path> files,
            SymbolProvider symbols) {
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(symbols, "symbols");

        LinkedHashSet<Path> unique = new LinkedHashSet<>();
        for (Path file : files) {
            if (file != null) unique.add(file.toAbsolutePath().normalize());
        }

        List<ServerObjectSemanticOverlay> overlays = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        for (Path file : unique) {
            indexFile(file, symbols, overlays, diagnostics);
        }

        diagnostics.add("OpenRune object overlays indexed " + overlays.size()
                + " authored object block(s) from " + unique.size() + " TOML file(s)");
        return new ServerObjectSemanticIndex(overlays, diagnostics);
    }

    private static void indexFile(
            Path file,
            SymbolProvider symbols,
            List<ServerObjectSemanticOverlay> overlays,
            List<String> diagnostics) {
        final String text;
        try {
            text = Files.readString(file);
        } catch (IOException error) {
            diagnostics.add("Object overlay read failed for " + file + ": " + message(error));
            return;
        }
        if (!text.contains("[[object]]")) return;

        TextLines lines = new TextLines(text);
        for (Block block : objectBlocks(lines)) {
            parseBlock(file, text, lines, block, symbols, overlays, diagnostics);
        }
    }

    private static void parseBlock(
            Path file,
            String document,
            TextLines lines,
            Block block,
            SymbolProvider symbols,
            List<ServerObjectSemanticOverlay> overlays,
            List<String> diagnostics) {
        String source = document.substring(block.startOffset(), block.endOffset());
        final TomlParseResult parsed;
        try {
            parsed = Toml.parse(source);
        } catch (RuntimeException error) {
            diagnostics.add("Object overlay parse failed for " + file + ":"
                    + (block.startLine() + 1) + ": " + message(error));
            return;
        }

        if (parsed.hasErrors()) {
            parsed.errors().forEach(error -> {
                int relative = error.position() == null ? 1 : error.position().line();
                int line = block.startLine() + Math.max(1, relative);
                diagnostics.add("Object overlay TOML error at " + file + ":" + line
                        + ": " + error.getMessage());
            });
            return;
        }

        TomlArray objects = parsed.getArray("object");
        if (objects == null || objects.size() == 0 || !(objects.get(0) instanceof TomlTable object)) {
            return;
        }

        String id = clean(object.getString("id"));
        if (id == null || !id.toLowerCase(Locale.ROOT).startsWith("loc.")) {
            diagnostics.add("Object overlay missing loc id at " + file + ":"
                    + (block.startLine() + 1));
            return;
        }

        String inherit = clean(object.getString("inherit"));
        String contentGroup = clean(object.getString("contentGroup"));
        Map<String, String> params = parseParams(lines, block);

        int numericId = symbols.resolve(SymbolNamespace.LOC, SymbolNamespace.LOC.unqualify(id))
                .map(symbol -> symbol.id())
                .orElse(-1);

        Map<String, SourceSpan> fields = new LinkedHashMap<>();
        fieldSpan(file, lines, block, "id", false)
                .ifPresent(span -> fields.put("id", span));
        fieldSpan(file, lines, block, "inherit", false)
                .ifPresent(span -> fields.put("inherit", span));
        fieldSpan(file, lines, block, "contentGroup", false)
                .ifPresent(span -> fields.put("contentGroup", span));
        for (String param : params.keySet()) {
            fieldSpan(file, lines, block, param, true)
                    .ifPresent(span -> fields.put("param:" + param, span));
        }

        overlays.add(new ServerObjectSemanticOverlay(
                id,
                numericId,
                Optional.ofNullable(inherit),
                Optional.ofNullable(contentGroup),
                params,
                lines.span(file, block.startOffset(), block.endOffset()),
                fields,
                true));
    }

    private static Map<String, String> parseParams(TextLines lines, Block block) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        boolean inParams = false;
        for (int lineIndex = block.startLine(); lineIndex < block.endLineExclusive(); lineIndex++) {
            String raw = lines.line(lineIndex);
            String trimmed = raw.trim();
            if (trimmed.equals("[object.params]")) {
                inParams = true;
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                if (inParams) break;
                continue;
            }
            if (!inParams || trimmed.isBlank() || trimmed.startsWith("#")) continue;

            int equals = raw.indexOf('=');
            if (equals < 0) continue;
            String key = unquote(raw.substring(0, equals).trim());
            if (key.isBlank()) continue;
            String rhs = raw.substring(equals + 1).trim();
            Object value = parseTomlValue(rhs);
            if (value != null) values.put(key, String.valueOf(value));
        }
        return Map.copyOf(values);
    }

    private static Object parseTomlValue(String rhs) {
        if (rhs == null || rhs.isBlank()) return null;
        TomlParseResult parsed = Toml.parse("value = " + rhs);
        if (parsed.hasErrors()) return rhs;
        return parsed.get("value");
    }

    private static Optional<SourceSpan> fieldSpan(
            Path file,
            TextLines lines,
            Block block,
            String field,
            boolean quotedKey) {
        boolean inParams = false;
        for (int lineIndex = block.startLine(); lineIndex < block.endLineExclusive(); lineIndex++) {
            String raw = lines.line(lineIndex);
            String trimmed = raw.trim();
            if (trimmed.equals("[object.params]")) {
                inParams = true;
                continue;
            }
            if (trimmed.startsWith("[") && !trimmed.equals("[object.params]")
                    && lineIndex != block.startLine()) {
                inParams = false;
            }
            if (quotedKey != inParams) continue;

            int equals = raw.indexOf('=');
            if (equals < 0) continue;
            String key = raw.substring(0, equals).trim();
            if (quotedKey) key = unquote(key);
            if (!key.equals(field)) continue;

            int first = 0;
            while (first < raw.length() && Character.isWhitespace(raw.charAt(first))) first++;
            return Optional.of(lines.lineSpan(file, lineIndex, first));
        }
        return Optional.empty();
    }

    private static List<Block> objectBlocks(TextLines lines) {
        List<Block> blocks = new ArrayList<>();
        int line = 0;
        while (line < lines.count()) {
            if (!lines.line(line).trim().equals("[[object]]")) {
                line++;
                continue;
            }

            int startLine = line;
            int endLine = line + 1;
            while (endLine < lines.count()) {
                String trimmed = lines.line(endLine).trim();
                if (trimmed.startsWith("[[") && trimmed.endsWith("]]")) break;
                endLine++;
            }
            blocks.add(new Block(
                    startLine,
                    endLine,
                    lines.startOffset(startLine),
                    endLine < lines.count() ? lines.startOffset(endLine) : lines.length()));
            line = endLine;
        }
        return List.copyOf(blocks);
    }

    private static String unquote(String value) {
        String text = value.trim();
        if (text.length() >= 2
                && ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    private record Block(
            int startLine,
            int endLineExclusive,
            int startOffset,
            int endOffset) {
    }

    private static final class TextLines {
        private final String text;
        private final int[] starts;

        private TextLines(String text) {
            this.text = Objects.requireNonNull(text, "text");
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') offsets.add(i + 1);
            }
            starts = offsets.stream().mapToInt(Integer::intValue).toArray();
        }

        private int count() {
            return starts.length;
        }

        private int length() {
            return text.length();
        }

        private int startOffset(int line) {
            return starts[line];
        }

        private String line(int line) {
            int start = starts[line];
            int end = line + 1 < starts.length ? starts[line + 1] : text.length();
            while (end > start && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) {
                end--;
            }
            return text.substring(start, end);
        }

        private SourceSpan lineSpan(Path file, int line, int columnOffset) {
            String value = line(line);
            int start = starts[line] + Math.min(columnOffset, value.length());
            int end = starts[line] + value.length();
            return new SourceSpan(
                    file,
                    start,
                    end,
                    line + 1,
                    Math.min(columnOffset, value.length()) + 1,
                    line + 1,
                    value.length() + 1);
        }

        private SourceSpan span(Path file, int startOffset, int endOffset) {
            Position start = position(startOffset);
            Position end = position(Math.max(startOffset, endOffset));
            return new SourceSpan(
                    file,
                    startOffset,
                    endOffset,
                    start.line(),
                    start.column(),
                    end.line(),
                    end.column());
        }

        private Position position(int offset) {
            int bounded = Math.max(0, Math.min(offset, text.length()));
            int low = 0;
            int high = starts.length - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (starts[mid] <= bounded) low = mid + 1;
                else high = mid - 1;
            }
            int lineIndex = Math.max(0, high);
            return new Position(lineIndex + 1, bounded - starts[lineIndex] + 1);
        }
    }

    private record Position(int line, int column) {
    }
}
