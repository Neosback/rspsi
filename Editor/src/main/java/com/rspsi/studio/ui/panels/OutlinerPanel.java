package com.rspsi.studio.ui.panels;

import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.integration.npc.NpcSpawn;
import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.PickResult;
import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Scene outliner panel listing terrain, categories, objects, and server spawns.
 */
public final class OutlinerPanel implements StudioPanel {
    public static final String ID = "studio.outliner";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Outliner";
    }

    @Override
    public String icon() {
        return StudioIcons.OUTLINER;
    }

    @Override
    public DockRegion preferredRegion() {
        return DockRegion.RIGHT;
    }

    @Override
    public Set<DockRegion> allowedRegions() {
        return EnumSet.of(DockRegion.RIGHT, DockRegion.BOTTOM);
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void render(StudioPanelContext context) {
        EditorSession session = context.session();
        if (session == null) {
            ImGui.textDisabled("No active session.");
            return;
        }

        WorldDocument world = session.world();
        SettingsStore settings = context.settings();
        LoadedOsrsCacheSession cache = context.cache();
        int activePlane = settings.snapshot().get(RenderSettingKeys.ACTIVE_PLANE);

        int regionId = 0;
        if (context.viewport() != null) {
            int camTileX = Math.max(0, (int) (context.viewport().navigation().camera().x() / 128.0f));
            int camTileY = Math.max(0, (int) (context.viewport().navigation().camera().z() / 128.0f));
            regionId = ((camTileX >> 6) << 8) | (camTileY >> 6);
        }

        String regionTitle = "Region " + (regionId > 0 ? regionId : "") + " (" + world.width() + "x" + world.length() + ")";
        if (ImGui.treeNodeEx(regionTitle, ImGuiTreeNodeFlags.DefaultOpen)) {
            for (int plane = 0; plane < world.planes(); plane++) {
                boolean isActive = plane == activePlane;
                int flags = isActive ? ImGuiTreeNodeFlags.DefaultOpen : 0;
                String planeTitle = "Plane " + plane + (isActive ? " (Active)" : "");
                if (ImGui.treeNodeEx(planeTitle + "##p-" + plane, flags)) {
                    renderOutlinerCategory(world, plane, ObjectCategory.WALL, cache, context);
                    renderOutlinerCategory(world, plane, ObjectCategory.WALL_DECOR, cache, context);
                    renderOutlinerCategory(world, plane, ObjectCategory.GROUND, cache, context);
                    renderOutlinerCategory(world, plane, ObjectCategory.GROUND_DECOR, cache, context);
                    renderOutlinerServerContent(world, plane, context);
                    ImGui.treePop();
                }
            }
            ImGui.treePop();
        }
    }

    private void renderOutlinerServerContent(WorldDocument world, int plane, StudioPanelContext context) {
        if (context.spawns() == null) return;
        EditorSession session = context.session();
        if (session == null) return;
        int minX = session.window().originX();
        int minY = session.window().originY();
        int maxX = minX + world.width() - 1;
        int maxY = minY + world.length() - 1;
        List<NpcSpawn> planeSpawns = context.spawns().spawns(
                plane, minX, minY, maxX, maxY);
        if (planeSpawns.isEmpty()) return;
        String title = "Server NPC Spawns (" + planeSpawns.size() + ")##p" + plane + "-server-npcs";
        if (ImGui.treeNode(title)) {
            for (int i = 0; i < planeSpawns.size(); i++) {
                NpcSpawn spawn = planeSpawns.get(i);
                String label = String.format("[%02d,%02d] %s (id:%d)##spawn-%d-%d",
                        spawn.coordinate().x() & 63, spawn.coordinate().y() & 63,
                        spawn.symbolicName(), spawn.id(), plane, i);
                if (ImGui.selectable(label)) {
                    if (context.viewport() != null) {
                        context.viewport().navigationService().synchronizeFromCamera(plane);
                        context.viewport().navigationService().jumpTo(spawn.coordinate());
                    }
                }
            }
            ImGui.treePop();
        }
    }

    private void renderOutlinerCategory(WorldDocument world, int plane,
                                        ObjectCategory category,
                                        LoadedOsrsCacheSession cache,
                                        StudioPanelContext context) {
        List<WorldObject> matching = new ArrayList<>();
        for (int x = 0; x < world.width(); x++) {
            for (int y = 0; y < world.length(); y++) {
                for (WorldObject obj : world.tile(plane, x, y).snapshot().objects()) {
                    if (obj.category() == category) {
                        matching.add(obj);
                    }
                }
            }
        }
        if (matching.isEmpty()) {
            ImGui.textDisabled("  " + category.displayName() + " (0)");
            return;
        }
        String nodeTitle = category.displayName() + " (" + matching.size() + ")##p" + plane + "-" + category.name();
        if (ImGui.treeNode(nodeTitle)) {
            for (int i = 0; i < matching.size(); i++) {
                WorldObject obj = matching.get(i);
                String name = (cache != null) ? cache.bundle().definitions().object(obj.id())
                        .map(ObjectDefinitionView::name)
                        .filter(n -> !n.isBlank())
                        .orElse("Object " + obj.id()) : "Object " + obj.id();
                String itemLabel = String.format("[%02d,%02d] %s##obj-%d-%d", obj.x(), obj.y(), name, plane, i);
                if (ImGui.selectable(itemLabel)) {
                    if (context.viewport() != null) {
                        context.viewport().setSelection(new PickResult(
                                context.session().coordinates().toWorld(
                                        new LocalTile(plane, obj.x(), obj.y())),
                                plane, obj.id(), 0.0f));
                    }
                }
            }
            ImGui.treePop();
        }
    }
}
