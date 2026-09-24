package com.rspsi.plugins.server.openrune.kotlin;

import com.rspsi.editor.integration.semantic.SemanticFactKind;
import com.rspsi.editor.integration.semantic.SemanticSourceFact;
import com.rspsi.editor.integration.semantic.SemanticSourceIndex;
import com.rspsi.editor.integration.semantic.SourceSpan;
import com.rspsi.server.ServerProjectInspection;
import com.rspsi.server.gradle.GradleProjectModel;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry;
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid;
import org.jetbrains.kotlin.psi.KtValueArgument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * PSI-backed structural semantic index for an opened OpenRune checkout.
 *
 * <p>This first layer intentionally does not claim cross-module type resolution. It extracts
 * syntax-backed facts and exact source provenance from the Gradle-discovered production Kotlin
 * roots. K2/FIR resolution can later enrich these facts without changing the neutral API.</p>
 */
public final class OpenRuneKotlinSemanticIndexer {
    private static final Set<String> SYMBOL_PREFIXES = Set.of(
            "loc", "npc", "obj", "item", "varbit", "varp", "varc", "varcon",
            "interface", "component", "clientscript", "dbtable", "dbrow", "area",
            "seq", "spotanim", "stat", "content", "synth");

    public SemanticSourceIndex index(ServerProjectInspection inspection) {
        Objects.requireNonNull(inspection, "inspection");
        GradleProjectModel model = inspection.gradleModel().orElse(null);
        if (model == null) {
            return new SemanticSourceIndex(
                    List.of(), List.of(),
                    List.of("Kotlin semantic index unavailable: Gradle project model is not loaded"));
        }

        List<SourceRoot> roots = productionRoots(model);
        if (roots.isEmpty()) {
            return new SemanticSourceIndex(
                    List.of(), List.of(),
                    List.of("Kotlin semantic index found no production source roots"));
        }

        List<SemanticSourceFact> facts = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();

        Disposable disposable = Disposer.newDisposable("rspsi-openrune-kotlin-psi");
        try {
            CompilerConfiguration configuration = new CompilerConfiguration();
            KotlinCoreEnvironment environment = KotlinCoreEnvironment.createForProduction(
                    disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES);
            KtPsiFactory psiFactory = new KtPsiFactory(environment.getProject(), false);

            for (SourceRoot root : roots) {
                for (Path file : kotlinFiles(root.path())) {
                    try {
                        String text = Files.readString(file);
                        var ktFile = psiFactory.createFile(file.getFileName().toString(), text);
                        LineMap lines = new LineMap(text);
                        Map<String, String> baseAttributes = Map.of(
                                "projectPath", root.projectPath(),
                                "sourceSet", root.sourceSet(),
                                "package", ktFile.getPackageFqName().asString());
                        ktFile.accept(new FactVisitor(
                                file.toAbsolutePath().normalize(),
                                lines,
                                baseAttributes,
                                facts));
                        files.add(file.toAbsolutePath().normalize());
                    } catch (IOException | RuntimeException error) {
                        diagnostics.add("Kotlin PSI index failed for " + file + ": " + message(error));
                    }
                }
            }
        } finally {
            Disposer.dispose(disposable);
        }

        diagnostics.add("Kotlin PSI indexed " + files.size() + " file(s) and "
                + facts.size() + " semantic fact(s)");
        return new SemanticSourceIndex(facts, files, diagnostics);
    }

    private static List<SourceRoot> productionRoots(GradleProjectModel model) {
        List<SourceRoot> roots = new ArrayList<>();
        Set<Path> seen = new LinkedHashSet<>();
        for (GradleProjectModel.ProjectInfo project : model.projects()) {
            for (GradleProjectModel.SourceSetInfo sourceSet : project.sourceSets()) {
                if (!sourceSet.name().equals("main")) continue;
                for (Path root : sourceSet.sourceDirectories()) {
                    Path normalized = root.toAbsolutePath().normalize();
                    if (seen.add(normalized)) {
                        roots.add(new SourceRoot(project.path(), sourceSet.name(), normalized));
                    }
                }
            }
        }
        return List.copyOf(roots);
    }

    private static List<Path> kotlinFiles(Path root) {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".kt"))
                    .filter(path -> !containsGeneratedDirectory(root, path))
                    .sorted()
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static boolean containsGeneratedDirectory(Path root, Path path) {
        Path relative;
        try {
            relative = root.toAbsolutePath().normalize()
                    .relativize(path.toAbsolutePath().normalize());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        for (Path part : relative) {
            String value = part.toString();
            if (value.equals("build") || value.equals(".gradle") || value.equals(".git")
                    || value.equals("out") || value.equals("target")) {
                return true;
            }
        }
        return false;
    }

    private static final class FactVisitor extends KtTreeVisitorVoid {
        private final Path file;
        private final LineMap lines;
        private final Map<String, String> baseAttributes;
        private final List<SemanticSourceFact> facts;
        private final Deque<String> owners = new ArrayDeque<>();

        private FactVisitor(
                Path file,
                LineMap lines,
                Map<String, String> baseAttributes,
                List<SemanticSourceFact> facts) {
            this.file = file;
            this.lines = lines;
            this.baseAttributes = baseAttributes;
            this.facts = facts;
            String pkg = baseAttributes.getOrDefault("package", "");
            owners.push(pkg.isBlank() ? "<root>" : pkg);
        }

        @Override
        public void visitClass(KtClass klass) {
            String name = value(klass.getName(), "<anonymous>");
            Map<String, String> attributes = attributes();
            List<String> superTypes = klass.getSuperTypeListEntries().stream()
                    .map(entry -> entry.getText())
                    .toList();
            if (!superTypes.isEmpty()) attributes.put("superTypes", String.join(" | ", superTypes));
            add(SemanticFactKind.CLASS_DECLARATION, name, owner(), List.of(), attributes, klass, 1.0f);

            boolean pluginScript = superTypes.stream()
                    .anyMatch(type -> simpleType(type).equals("PluginScript"));
            boolean questScript = superTypes.stream()
                    .anyMatch(type -> simpleType(type).equals("QuestScript"));

            if (pluginScript) {
                add(SemanticFactKind.PLUGIN_SCRIPT, name, owner(), List.of(),
                        attributes, klass, 1.0f);
            }
            if (questScript) {
                add(SemanticFactKind.QUEST_SCRIPT, name, owner(), List.of(),
                        attributes, klass, 1.0f);
                klass.getSuperTypeListEntries().stream()
                        .filter(KtSuperTypeCallEntry.class::isInstance)
                        .map(KtSuperTypeCallEntry.class::cast)
                        .filter(entry -> simpleType(entry.getCalleeExpression().getText())
                                .equals("QuestScript"))
                        .findFirst()
                        .ifPresent(entry -> {
                            List<String> args = directStringArguments(entry.getValueArguments());
                            Map<String, String> questAttributes = attributes();
                            if (!args.isEmpty()) questAttributes.put("questKey", args.get(0));
                            if (args.size() > 1) questAttributes.put("questVar", args.get(1));
                            add(SemanticFactKind.QUEST_DEFINITION, name, owner(), args,
                                    questAttributes, entry, 1.0f);
                        });
            }

            owners.push(qualifiedOwner(name));
            super.visitClass(klass);
            owners.pop();
        }

        @Override
        public void visitNamedFunction(KtNamedFunction function) {
            String name = value(function.getName(), "<anonymous>");
            Map<String, String> attributes = attributes();
            if (function.getReceiverTypeReference() != null) {
                attributes.put("receiver", function.getReceiverTypeReference().getText());
            }
            add(SemanticFactKind.FUNCTION_DECLARATION, name, owner(), List.of(),
                    attributes, function, 1.0f);

            owners.push(qualifiedOwner(name + "()"));
            super.visitNamedFunction(function);
            owners.pop();
        }

        @Override
        public void visitProperty(KtProperty property) {
            String name = value(property.getName(), "<anonymous>");
            owners.push(qualifiedOwner(name));
            super.visitProperty(property);
            owners.pop();
        }

        @Override
        public void visitCallExpression(KtCallExpression expression) {
            String callee = expression.getCalleeExpression() == null
                    ? "" : expression.getCalleeExpression().getText();
            if (!callee.isBlank()) {
                List<String> stringArgs = directStringArguments(expression.getValueArguments());
                Map<String, String> attributes = attributes();
                attributes.put("callee", callee);
                add(SemanticFactKind.CALL, callee, owner(), stringArgs,
                        attributes, expression, 1.0f);

                if (isHandlerName(callee)) {
                    add(SemanticFactKind.SCRIPT_HANDLER, callee, owner(), stringArgs,
                            attributes, expression, 0.95f);
                }

                if (isVarBinding(callee, stringArgs)) {
                    add(SemanticFactKind.VAR_BINDING, callee, owner(), stringArgs,
                            attributes, expression, 0.95f);
                }
            }
            super.visitCallExpression(expression);
        }

        @Override
        public void visitStringTemplateExpression(KtStringTemplateExpression expression) {
            String literal = plainString(expression.getText());
            if (literal != null && isSymbol(literal)) {
                Map<String, String> attributes = attributes();
                int dot = literal.indexOf('.');
                attributes.put("namespace", dot > 0 ? literal.substring(0, dot) : "");
                add(SemanticFactKind.SYMBOL_REFERENCE, literal, owner(), List.of(),
                        attributes, expression, 1.0f);
            }
            super.visitStringTemplateExpression(expression);
        }

        private void add(
                SemanticFactKind kind,
                String name,
                String owner,
                List<String> arguments,
                Map<String, String> attributes,
                org.jetbrains.kotlin.psi.KtElement element,
                float confidence) {
            var range = element.getTextRange();
            facts.add(new SemanticSourceFact(
                    kind,
                    name,
                    owner,
                    arguments,
                    Map.copyOf(attributes),
                    lines.span(file, range.getStartOffset(), range.getEndOffset()),
                    confidence));
        }

        private Map<String, String> attributes() {
            return new LinkedHashMap<>(baseAttributes);
        }

        private String owner() {
            return owners.peek();
        }

        private String qualifiedOwner(String child) {
            String parent = owner();
            return parent == null || parent.equals("<root>") ? child : parent + "." + child;
        }
    }

    private static List<String> directStringArguments(List<? extends KtValueArgument> arguments) {
        List<String> result = new ArrayList<>();
        for (KtValueArgument argument : arguments) {
            if (argument.getArgumentExpression() instanceof KtStringTemplateExpression expression) {
                String value = plainString(expression.getText());
                if (value != null) result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private static boolean isHandlerName(String callee) {
        return callee.length() > 2
                && callee.startsWith("on")
                && Character.isUpperCase(callee.charAt(2));
    }

    private static boolean isVarBinding(String callee, List<String> arguments) {
        String lower = callee.toLowerCase(Locale.ROOT);
        if (!(lower.contains("varbit") || lower.contains("varp") || lower.contains("varcon"))) {
            return false;
        }
        return arguments.stream().anyMatch(argument ->
                argument.startsWith("varbit.")
                        || argument.startsWith("varp.")
                        || argument.startsWith("varc.")
                        || argument.startsWith("varcon."));
    }

    private static boolean isSymbol(String value) {
        int dot = value.indexOf('.');
        if (dot <= 0 || dot == value.length() - 1) return false;
        return SYMBOL_PREFIXES.contains(value.substring(0, dot).toLowerCase(Locale.ROOT));
    }

    private static String simpleType(String text) {
        String value = text == null ? "" : text.trim();
        int paren = value.indexOf('(');
        if (paren >= 0) value = value.substring(0, paren);
        int generic = value.indexOf('<');
        if (generic >= 0) value = value.substring(0, generic);
        int dot = value.lastIndexOf('.');
        return (dot >= 0 ? value.substring(dot + 1) : value).trim();
    }

    private static String plainString(String text) {
        if (text == null) return null;
        String value = text.trim();
        String body;
        if (value.startsWith("\"\"\"") && value.endsWith("\"\"\"") && value.length() >= 6) {
            body = value.substring(3, value.length() - 3);
        } else if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            body = value.substring(1, value.length() - 1);
        } else {
            return null;
        }
        if (body.contains("$")) return null;
        return body
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    private record SourceRoot(String projectPath, String sourceSet, Path path) {
    }

    private static final class LineMap {
        private final int[] starts;

        private LineMap(String text) {
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') offsets.add(i + 1);
            }
            starts = offsets.stream().mapToInt(Integer::intValue).toArray();
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
            int low = 0;
            int high = starts.length - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (starts[mid] <= offset) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            int lineIndex = Math.max(0, high);
            return new Position(lineIndex + 1, offset - starts[lineIndex] + 1);
        }
    }

    private record Position(int line, int column) {
    }
}
