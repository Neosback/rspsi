package com.openrune.studio.service.openrune

import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.ValueArgument

enum class SourceFactKind {
    CLASS_DECLARATION,
    FUNCTION_DECLARATION,
    CALL,
    SYMBOL_REFERENCE,
    PLUGIN_SCRIPT,
    SCRIPT_HANDLER,
}

data class SourceSpan(
    val path: String,
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
)

data class SourceFact(
    val kind: SourceFactKind,
    val name: String,
    val owner: String,
    val modulePath: String,
    val packageName: String,
    val arguments: List<String>,
    val source: SourceSpan,
    val confidence: Double,
)

data class OpenRuneSourceIndex(
    val root: String,
    val fileCount: Int,
    val factCount: Int,
    val facts: List<SourceFact>,
    val diagnostics: List<String>,
)

/**
 * PSI-backed structural source index for OpenRune content.
 *
 * This intentionally performs no type resolution. It extracts syntax-backed facts and exact
 * source provenance so later graph/runtime layers can enrich the same neutral facts.
 */
class OpenRuneKotlinSourceIndexer {
    fun index(requestedRoot: Path): OpenRuneSourceIndex {
        val root = requestedRoot.toAbsolutePath().normalize()
        val contentRoot = root.resolve("content")
        require(Files.isDirectory(contentRoot)) { "OpenRune content root was not found: $contentRoot" }

        val files = kotlinContentFiles(contentRoot)
        val diagnostics = mutableListOf<String>()
        val facts = mutableListOf<SourceFact>()

        val disposable = Disposer.newDisposable("openrune-studio-kotlin-psi")
        try {
            val environment =
                KotlinCoreEnvironment.createForProduction(
                    disposable,
                    CompilerConfiguration(),
                    EnvironmentConfigFiles.JVM_CONFIG_FILES,
                )
            val factory = KtPsiFactory(environment.project, false)

            for (file in files) {
                try {
                    val text = Files.readString(file)
                    val ktFile = factory.createFile(file.fileName.toString(), text)
                    val relativePath = normalizeRelative(root, file)
                    val modulePath = modulePath(relativePath)
                    val packageName = ktFile.packageFqName.asString()
                    val lineMap = LineMap(text)

                    ktFile.accept(
                        FactVisitor(
                            relativePath = relativePath,
                            modulePath = modulePath,
                            packageName = packageName,
                            lineMap = lineMap,
                            facts = facts,
                        ),
                    )
                } catch (failure: RuntimeException) {
                    diagnostics += "${normalizeRelative(root, file)}: ${failure.message ?: failure::class.java.simpleName}"
                }
            }
        } finally {
            Disposer.dispose(disposable)
        }

        return OpenRuneSourceIndex(
            root = root.toString(),
            fileCount = files.size,
            factCount = facts.size,
            facts = facts.sortedWith(compareBy({ it.source.path }, { it.source.startOffset }, { it.kind.name })),
            diagnostics = diagnostics,
        )
    }

    private fun kotlinContentFiles(contentRoot: Path): List<Path> =
        Files.walk(contentRoot).use { paths ->
            paths
                .filter { path ->
                    Files.isRegularFile(path) &&
                        path.fileName.toString().endsWith(".kt") &&
                        normalizePath(path).contains("/src/main/kotlin/") &&
                        !normalizePath(path).contains("/build/")
                }
                .sorted()
                .toList()
        }

    private fun modulePath(relativePath: String): String =
        relativePath
            .substringAfter("content/")
            .substringBefore("/src/main/kotlin/")

    private fun normalizeRelative(root: Path, path: Path): String =
        root.relativize(path).toString().replace('\\', '/')

    private fun normalizePath(path: Path): String =
        path.toAbsolutePath().normalize().toString().replace('\\', '/')

    private class FactVisitor(
        private val relativePath: String,
        private val modulePath: String,
        private val packageName: String,
        private val lineMap: LineMap,
        private val facts: MutableList<SourceFact>,
    ) : KtTreeVisitorVoid() {
        private val owners = ArrayDeque<String>()

        init {
            owners.push(packageName.ifBlank { "<root>" })
        }

        override fun visitClass(klass: KtClass) {
            val name = klass.name?.takeIf { it.isNotBlank() } ?: "<anonymous>"
            add(
                kind = SourceFactKind.CLASS_DECLARATION,
                name = name,
                arguments = emptyList(),
                elementStart = klass.textRange.startOffset,
                elementEnd = klass.textRange.endOffset,
                confidence = 1.0,
            )

            if (klass.superTypeListEntries.any { simpleType(it.text) == "PluginScript" }) {
                add(
                    kind = SourceFactKind.PLUGIN_SCRIPT,
                    name = name,
                    arguments = emptyList(),
                    elementStart = klass.textRange.startOffset,
                    elementEnd = klass.textRange.endOffset,
                    confidence = 1.0,
                )
            }

            owners.push(qualifiedOwner(name))
            super.visitClass(klass)
            owners.pop()
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            val name = function.name?.takeIf { it.isNotBlank() } ?: "<anonymous>"
            add(
                kind = SourceFactKind.FUNCTION_DECLARATION,
                name = name,
                arguments = emptyList(),
                elementStart = function.textRange.startOffset,
                elementEnd = function.textRange.endOffset,
                confidence = 1.0,
            )

            owners.push(qualifiedOwner("$name()"))
            super.visitNamedFunction(function)
            owners.pop()
        }

        override fun visitCallExpression(expression: KtCallExpression) {
            val callee = expression.calleeExpression?.text.orEmpty()
            if (callee.isNotBlank()) {
                val arguments = directStringArguments(expression.valueArguments)
                add(
                    kind = SourceFactKind.CALL,
                    name = callee,
                    arguments = arguments,
                    elementStart = expression.textRange.startOffset,
                    elementEnd = expression.textRange.endOffset,
                    confidence = 1.0,
                )

                if (isHandlerName(callee)) {
                    add(
                        kind = SourceFactKind.SCRIPT_HANDLER,
                        name = callee,
                        arguments = arguments,
                        elementStart = expression.textRange.startOffset,
                        elementEnd = expression.textRange.endOffset,
                        confidence = 0.95,
                    )
                }
            }
            super.visitCallExpression(expression)
        }

        override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
            val literal = plainString(expression.text)
            if (literal != null && isSymbol(literal)) {
                add(
                    kind = SourceFactKind.SYMBOL_REFERENCE,
                    name = literal,
                    arguments = emptyList(),
                    elementStart = expression.textRange.startOffset,
                    elementEnd = expression.textRange.endOffset,
                    confidence = 1.0,
                )
            }
            super.visitStringTemplateExpression(expression)
        }

        private fun add(
            kind: SourceFactKind,
            name: String,
            arguments: List<String>,
            elementStart: Int,
            elementEnd: Int,
            confidence: Double,
        ) {
            facts +=
                SourceFact(
                    kind = kind,
                    name = name,
                    owner = owners.peek(),
                    modulePath = modulePath,
                    packageName = packageName,
                    arguments = arguments,
                    source = lineMap.span(relativePath, elementStart, elementEnd),
                    confidence = confidence,
                )
        }

        private fun qualifiedOwner(child: String): String {
            val parent = owners.peek()
            return if (parent == null || parent == "<root>") child else "$parent.$child"
        }
    }

    private class LineMap(text: String) {
        private val starts: IntArray =
            buildList {
                add(0)
                text.forEachIndexed { index, char ->
                    if (char == '\n') add(index + 1)
                }
            }.toIntArray()

        fun span(path: String, startOffset: Int, endOffset: Int): SourceSpan {
            val start = position(startOffset)
            val end = position(maxOf(startOffset, endOffset))
            return SourceSpan(
                path = path,
                startOffset = startOffset,
                endOffset = endOffset,
                startLine = start.first,
                startColumn = start.second,
                endLine = end.first,
                endColumn = end.second,
            )
        }

        private fun position(offset: Int): Pair<Int, Int> {
            var low = 0
            var high = starts.lastIndex
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (starts[mid] <= offset) {
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            val lineIndex = maxOf(0, high)
            return (lineIndex + 1) to (offset - starts[lineIndex] + 1)
        }
    }

    private companion object {
        val SYMBOL_PREFIXES =
            setOf(
                "loc",
                "npc",
                "obj",
                "item",
                "varbit",
                "varp",
                "varc",
                "varcon",
                "interface",
                "component",
                "clientscript",
                "dbtable",
                "dbrow",
                "area",
                "seq",
                "spotanim",
                "bas",
                "category",
                "content",
                "controller",
                "currency",
                "enum",
                "font",
                "headbar",
                "hitmark",
                "mesanim",
                "midi",
                "param",
                "projanim",
                "queue",
                "stalk",
                "stat",
                "synth",
                "timer",
                "varn",
                "varobj",
                "walktrigger",
            )

        fun directStringArguments(arguments: List<ValueArgument>): List<String> =
            arguments.mapNotNull { argument ->
                val expression = argument.getArgumentExpression() as? KtStringTemplateExpression
                expression?.let { plainString(it.text) }
            }

        fun isHandlerName(callee: String): Boolean =
            callee.length > 2 && callee.startsWith("on") && callee[2].isUpperCase()

        fun isSymbol(value: String): Boolean {
            val dot = value.indexOf('.')
            if (dot <= 0 || dot == value.lastIndex) return false
            return value.substring(0, dot).lowercase() in SYMBOL_PREFIXES
        }

        fun simpleType(text: String): String {
            var value = text.trim()
            value = value.substringBefore('(')
            value = value.substringBefore('<')
            return value.substringAfterLast('.').trim()
        }

        fun plainString(text: String): String? {
            val value = text.trim()
            val body =
                when {
                    value.startsWith("\"\"\"") &&
                        value.endsWith("\"\"\"") &&
                        value.length >= 6 -> value.substring(3, value.length - 3)
                    value.startsWith('"') &&
                        value.endsWith('"') &&
                        value.length >= 2 -> value.substring(1, value.length - 1)
                    else -> return null
                }
            if ('$' in body) return null
            return body
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\")
        }
    }
}
