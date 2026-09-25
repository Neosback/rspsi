package com.rspsi.studio;

import com.rspsi.editor.plugin.ContributionOwner;
import com.rspsi.editor.settings.SettingKey;
import com.rspsi.editor.settings.SettingScope;
import com.rspsi.editor.settings.SettingSpec;
import com.rspsi.editor.settings.SettingsService;
import com.rspsi.editor.settings.SettingsSnapshot;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiSliderFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Native Dear ImGui Preferences dialog.
 *
 * <p>Provides category navigation, instant search across all settings,
 * scope/owner badges, typed live editing, and single-click reset to defaults.
 * Guarantees that no core or plugin setting is ever lost track of.</p>
 */
public final class PreferencesWindow {
    private static final long SLIDER_BOUND = Integer.MAX_VALUE / 2;

    private boolean open;
    private String selectedCategory = "General";
    private final ImString searchQuery = new ImString(128);

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public void toggle() {
        this.open = !this.open;
    }

    public void render(SettingsStore store, SettingsService settingsService) {
        if (!open) return;

        StudioWidgets.windowBackdrop("preferences");
        ImGui.setNextWindowSize(780.0f, 520.0f, ImGuiCond.Appearing);
        ImBoolean pOpen = new ImBoolean(open);
        if (!ImGui.begin(StudioIcons.SETTINGS + "  Preferences##studio-preferences", pOpen, ImGuiWindowFlags.NoCollapse)) {
            open = pOpen.get();
            ImGui.end();
            return;
        }
        open = pOpen.get();

        // Search bar on top
        ImGui.setNextItemWidth(-1.0f);
        ImGui.inputTextWithHint("##pref-search", StudioIcons.SEARCH + "  Search settings by name, id, category, or description...",
                searchQuery, ImGuiInputTextFlags.None);
        ImGui.separator();

        String query = searchQuery.get().trim();
        boolean isSearching = !query.isEmpty();

        float leftWidth = 180.0f;
        float height = ImGui.getContentRegionAvailY() - ImGui.getFrameHeightWithSpacing();

        // Left sidebar: categories
        ImGui.beginChild("pref-categories", leftWidth, height, true);
        if (isSearching) {
            ImGui.textDisabled("SEARCH RESULTS");
            ImGui.separator();
        } else {
            ImGui.textDisabled("CATEGORIES");
            ImGui.separator();
            boolean allSelected = "ALL".equalsIgnoreCase(selectedCategory);
            if (ImGui.selectable("All Settings", allSelected)) {
                selectedCategory = "ALL";
            }

            for (String category : store.registry().categories()) {
                boolean selected = category.equalsIgnoreCase(selectedCategory);
                int count = store.registry().categorized().getOrDefault(category, List.of()).size();
                String label = category + " (" + count + ")";
                if (ImGui.selectable(label, selected)) {
                    selectedCategory = category;
                }
            }
        }
        ImGui.endChild();

        ImGui.sameLine();

        // Right pane: setting controls
        ImGui.beginChild("pref-settings-pane", 0.0f, height, true);
        List<SettingSpec<?>> visibleSettings;
        if (isSearching) {
            visibleSettings = store.registry().search(query);
            ImGui.textDisabled("Found " + visibleSettings.size() + " matching setting" + (visibleSettings.size() == 1 ? "" : "s"));
        } else if ("ALL".equalsIgnoreCase(selectedCategory)) {
            visibleSettings = store.registry().specifications();
        } else {
            visibleSettings = store.registry().categorized().getOrDefault(selectedCategory, List.of());
            StudioWidgets.heading(selectedCategory, visibleSettings.size() + " setting" + (visibleSettings.size() == 1 ? "" : "s"));
        }
        ImGui.separator();

        if (visibleSettings.isEmpty()) {
            ImGui.textDisabled("No settings found.");
        } else {
            SettingsSnapshot snapshot = store.snapshot();
            for (SettingSpec<?> spec : visibleSettings) {
                renderSettingRow(store, settingsService, snapshot, spec);
                ImGui.separator();
            }
        }
        ImGui.endChild();

        // Bottom footer
        if (StudioWidgets.buttonSecondary("Close##pref-close", 80.0f, 28.0f)) {
            open = false;
        }
        ImGui.sameLine();
        if (!isSearching && !"ALL".equalsIgnoreCase(selectedCategory) && StudioWidgets.buttonGhost("Reset Category to Defaults", 0.0f, 28.0f)) {
            for (SettingSpec<?> spec : visibleSettings) {
                resetToDefault(store, spec);
            }
        }

        ImGui.end();
    }

    @SuppressWarnings("unchecked")
    private static <T> void resetToDefault(SettingsStore store, SettingSpec<T> spec) {
        store.set(spec.key(), spec.defaultValue());
    }

    @SuppressWarnings("unchecked")
    private static <T> void renderSettingRow(SettingsStore store, SettingsService settingsService,
                                             SettingsSnapshot snapshot, SettingSpec<T> spec) {
        SettingKey<T> key = spec.key();
        T currentValue = snapshot.get(key);
        boolean isModified = !Objects.equals(currentValue, spec.defaultValue());

        ImGui.pushID(key.id());

        // Header: Label and badges
        ImGui.text(spec.label());
        ImGui.sameLine();
        ImGui.pushFont(StudioFonts.mono(), 0.0f);
        ImGui.textDisabled(key.id());
        ImGui.popFont();

        // Badges: Scope and Owner
        ImGui.sameLine(0.0f, 10.0f);
        renderScopeBadge(spec.scope());
        if (settingsService != null) {
            String owner = settingsService.ownerOf(key.id()).map(ContributionOwner::id).orElse("core");
            ImGui.sameLine();
            renderOwnerBadge(owner);
        }

        if (isModified) {
            ImGui.sameLine();
            if (ImGui.smallButton("Reset")) {
                store.set(key, spec.defaultValue());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setItemTooltip("Reset to default (" + spec.defaultValue() + ")");
            }
        }

        // Description
        if (!spec.description().isBlank()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.60f, 0.64f, 0.70f, 1.0f);
            ImGui.textWrapped(spec.description());
            ImGui.popStyleColor();
        }

        // Setting control
        renderControl(store, spec, currentValue);

        ImGui.popID();
    }

    @SuppressWarnings("unchecked")
    private static <T> void renderControl(SettingsStore store, SettingSpec<T> spec, T currentValue) {
        String id = "##val-" + spec.key().id();
        Class<T> type = spec.key().valueType();

        if (type == Boolean.class) {
            boolean cur = Boolean.TRUE.equals(currentValue);
            boolean updated = StudioWidgets.toggleSwitch(spec.key().id(), cur, cur ? "Enabled" : "Disabled");
            if (updated != cur) {
                store.set(spec.key(), (T) Boolean.valueOf(updated));
            }
        } else if (type == Integer.class) {
            int current = currentValue instanceof Number n ? n.intValue() : 0;
            int[] val = {current};
            Double min = spec.minimum();
            Double max = spec.maximum();
            if (min != null && max != null && min >= -SLIDER_BOUND && max <= SLIDER_BOUND) {
                if (ImGui.sliderInt(id, val, min.intValue(), max.intValue())) {
                    store.set(spec.key(), (T) Integer.valueOf(val[0]));
                }
            } else {
                if (ImGui.dragInt(id, val)) {
                    store.set(spec.key(), (T) Integer.valueOf(val[0]));
                }
            }
        } else if (type == Double.class || type == Float.class) {
            float current = currentValue instanceof Number n ? n.floatValue() : 0.0f;
            float[] val = {current};
            Double min = spec.minimum();
            Double max = spec.maximum();
            if (min != null && max != null && Float.isFinite(min.floatValue()) && Float.isFinite(max.floatValue())) {
                if (ImGui.sliderFloat(id, val, min.floatValue(), max.floatValue(), "%.2f", ImGuiSliderFlags.None)) {
                    if (type == Double.class) {
                        store.set(spec.key(), (T) Double.valueOf(val[0]));
                    } else {
                        store.set(spec.key(), (T) Float.valueOf(val[0]));
                    }
                }
            } else {
                if (ImGui.dragFloat(id, val)) {
                    if (type == Double.class) {
                        store.set(spec.key(), (T) Double.valueOf(val[0]));
                    } else {
                        store.set(spec.key(), (T) Float.valueOf(val[0]));
                    }
                }
            }
        } else if (!spec.options().isEmpty()) {
            List<T> options = spec.options();
            int index = Math.max(0, options.indexOf(currentValue));
            String[] labels = options.stream().map(String::valueOf).toArray(String[]::new);
            ImInt selected = new ImInt(index);
            if (ImGui.combo(id, selected, labels)) {
                store.set(spec.key(), options.get(selected.get()));
            }
        } else if (type.isEnum()) {
            T[] constants = type.getEnumConstants();
            int index = 0;
            for (int i = 0; i < constants.length; i++) {
                if (constants[i].equals(currentValue)) {
                    index = i;
                    break;
                }
            }
            String[] labels = new String[constants.length];
            for (int i = 0; i < constants.length; i++) {
                labels[i] = constants[i].toString();
            }
            ImInt selected = new ImInt(index);
            if (ImGui.combo(id, selected, labels)) {
                store.set(spec.key(), constants[selected.get()]);
            }
        } else {
            // Generic String / Other
            ImString val = new ImString(String.valueOf(currentValue != null ? currentValue : ""), 256);
            if (ImGui.inputText(id, val, ImGuiInputTextFlags.None)) {
                if (type == String.class) {
                    store.set(spec.key(), (T) val.get());
                }
            }
        }
    }

    private static void renderScopeBadge(SettingScope scope) {
        float r, g, b;
        switch (scope) {
            case GLOBAL -> { r = 0.2f; g = 0.5f; b = 0.8f; }
            case PROJECT -> { r = 0.2f; g = 0.7f; b = 0.4f; }
            case VIEWPORT -> { r = 0.8f; g = 0.5f; b = 0.2f; }
            case TRANSIENT -> { r = 0.6f; g = 0.3f; b = 0.7f; }
            default -> { r = 0.5f; g = 0.5f; b = 0.5f; }
        }
        StudioWidgets.badge(scope.name(), r, g, b);
    }

    private static void renderOwnerBadge(String owner) {
        float r = owner.startsWith("plugin:") ? 0.6f : 0.4f;
        float g = owner.startsWith("plugin:") ? 0.3f : 0.4f;
        float b = owner.startsWith("plugin:") ? 0.8f : 0.5f;
        StudioWidgets.badge(owner, r, g, b);
    }
}
