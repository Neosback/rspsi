package com.openrune.studio.service.openrune

import java.nio.file.Path

data class ResolvedContentSymbol(
    val symbol: String,
    val found: Boolean,
    val gameVals: List<GameValEntry>,
    val modules: List<ContentModuleEntry>,
    val handlers: List<SourceFact>,
    val references: List<SourceFact>,
    val pluginScripts: List<SourceFact>,
    val diagnostics: List<String>,
)

/**
 * Joins OpenRune GameVal/content metadata to structural Kotlin source facts.
 *
 * This is the browser-facing lookup used when a selected map/content entity already has a
 * symbolic OpenRune identity such as content.rock.
 */
class OpenRuneContentResolver(
    private val contentIndexer: OpenRuneContentIndexer = OpenRuneContentIndexer(),
    private val sourceIndexer: OpenRuneKotlinSourceIndexer = OpenRuneKotlinSourceIndexer(),
) {
    fun resolve(requestedRoot: Path, requestedSymbol: String): ResolvedContentSymbol {
        val content = contentIndexer.index(requestedRoot)
        val source = sourceIndexer.index(requestedRoot)
        return resolve(content, source, requestedSymbol)
    }

    fun resolve(
        content: OpenRuneContentIndex,
        source: OpenRuneSourceIndex,
        requestedSymbol: String,
    ): ResolvedContentSymbol {
        val symbol = requestedSymbol.trim()
        require(symbol.isNotEmpty()) { "symbol is required" }
        require('.' in symbol) { "symbol must be qualified, for example content.rock" }
        val gameVals =
            content.gameVals.filter { it.qualifiedName.equals(symbol, ignoreCase = true) }

        val handlers =
            source.facts.filter { fact ->
                fact.kind == SourceFactKind.SCRIPT_HANDLER &&
                    fact.arguments.any { it.equals(symbol, ignoreCase = true) }
            }

        val references =
            source.facts.filter { fact ->
                fact.kind == SourceFactKind.SYMBOL_REFERENCE &&
                    fact.name.equals(symbol, ignoreCase = true)
            }

        val sourcePaths =
            (handlers.asSequence() + references.asSequence())
                .map { it.source.path }
                .toSet()

        val pluginScripts =
            source.facts.filter { fact ->
                fact.kind == SourceFactKind.PLUGIN_SCRIPT && fact.source.path in sourcePaths
            }

        val modulePaths =
            buildSet {
                gameVals.mapNotNullTo(this) { it.modulePath }
                handlers.mapTo(this) { it.modulePath }
                references.mapTo(this) { it.modulePath }
                pluginScripts.mapTo(this) { it.modulePath }
            }

        val modules = content.modules.filter { it.path in modulePaths }

        return ResolvedContentSymbol(
            symbol = symbol,
            found = gameVals.isNotEmpty() || handlers.isNotEmpty() || references.isNotEmpty(),
            gameVals = gameVals,
            modules = modules,
            handlers = handlers,
            references = references,
            pluginScripts = pluginScripts,
            diagnostics = content.diagnostics + source.diagnostics,
        )
    }
}
