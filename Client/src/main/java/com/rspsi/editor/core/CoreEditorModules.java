package com.rspsi.editor.core;

import com.rspsi.editor.core.module.CoreDiagnosticsModule;
import com.rspsi.editor.core.module.CoreObjectModule;
import com.rspsi.editor.core.module.CorePathModule;
import com.rspsi.editor.core.module.CoreSelectionModule;
import com.rspsi.editor.core.module.CoreTerrainModule;
import com.rspsi.editor.core.module.CoreTilePainterModule;
import com.rspsi.editor.core.module.CoreUiModule;
import com.rspsi.editor.plugin.EditorPluginContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Single composition manifest for all always-on editor features.
 *
 * <p>If a feature is part of OpenRune Studio itself, it belongs here instead
 * of in plugin discovery, ServiceLoader, StudioApplication one-offs, or a
 * second registry. External JARs remain EditorPlugins and install after these
 * modules into the same registry.</p>
 */
public final class CoreEditorModules {
    private static final List<Supplier<? extends CoreEditorModule>> MODULE_FACTORIES = List.of(
            CoreTerrainModule::new,
            CoreTilePainterModule::new,
            CorePathModule::new,
            CoreObjectModule::new,
            CoreSelectionModule::new,
            CoreDiagnosticsModule::new,
            CoreUiModule::new
    );

    private CoreEditorModules() {
    }

    /**
     * Returns fresh module instances for one runtime host. Core modules must
     * never become cross-session singletons accidentally.
     */
    public static List<CoreEditorModule> all() {
        return MODULE_FACTORIES.stream()
                .map(Supplier::get)
                .map(CoreEditorModule.class::cast)
                .toList();
    }

    public static List<String> ids() {
        return all().stream()
                .sorted(Comparator.comparingInt(CoreEditorModule::order)
                        .thenComparing(CoreEditorModule::id))
                .map(CoreEditorModule::id)
                .toList();
    }

    /** Legacy host identities retained so existing external dependency manifests keep working. */
    public static Map<String, String> hostVersions() {
        Map<String, String> versions = new LinkedHashMap<>();
        for (CoreEditorModule module : all()) {
            if (versions.putIfAbsent(module.id(), module.version()) != null) {
                throw new IllegalStateException("Duplicate core editor module id: " + module.id());
            }
        }
        return Map.copyOf(versions);
    }

    public static void installAll(EditorPluginContext context) {
        install(all(), context);
    }

    public static void install(
            Iterable<? extends CoreEditorModule> modules,
            EditorPluginContext context) {
        Objects.requireNonNull(modules, "modules");
        Objects.requireNonNull(context, "context");

        List<CoreEditorModule> ordered = new ArrayList<>();
        for (CoreEditorModule module : modules) {
            ordered.add(Objects.requireNonNull(module, "core module"));
        }
        ordered.sort(Comparator.comparingInt(CoreEditorModule::order)
                .thenComparing(CoreEditorModule::id));

        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (CoreEditorModule module : ordered) {
            if (!ids.add(module.id())) {
                throw new IllegalArgumentException("Duplicate core editor module: " + module.id());
            }
            module.install(context);
        }
    }
}
