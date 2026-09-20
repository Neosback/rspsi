package com.rspsi.studio.ui.hud;

import com.rspsi.editor.overlay.OverlayComponent;
import com.rspsi.editor.overlay.OverlayContribution;
import com.rspsi.editor.overlay.OverlayPosition;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImDrawList;
import imgui.ImGui;

import java.util.List;

/**
 * Dear ImGui projection of the frontend-neutral OverlayComponent tree.
 *
 * <p>Third-party plugins can build HUDs without importing ImGui. This adapter
 * deliberately owns all colors, spacing and component rendering so Studio can
 * restyle the complete plugin ecosystem consistently.</p>
 */
public final class DeclarativeOverlayRenderer {
    private static final float PADDING = 9.0f;
    private static final float LINE_HEIGHT = 18.0f;
    private static final float GAP = 4.0f;

    public void render(StudioPanelContext context,
                       EditorPluginLifecycleManager lifecycle) {
        if (context == null || context.huds() == null
                || lifecycle == null || lifecycle.host() == null) return;

        List<OverlayContribution> overlays = lifecycle.host().context()
                .services().overlays().contributions();
        for (OverlayContribution contribution : overlays) {
            OverlayComponent component;
            try {
                component = contribution.snapshot();
            } catch (RuntimeException failure) {
                lifecycle.host().context().notifications().error(
                        "Overlay failed", contribution.label() + ": " + failure.getMessage());
                continue;
            }

            context.huds().register(contribution.id(),
                    quadrant(contribution.position()), contribution.priority());
            float height = estimate(component) + PADDING * 2.0f;
            var placement = context.huds().place(
                    contribution.id(), contribution.preferredWidth(), height);
            if (placement == null) continue;

            ImDrawList draw = ImGui.getWindowDrawList();
            float x = placement.x();
            float y = placement.y();
            float width = placement.width();
            float bottom = y + placement.height();
            draw.addRectFilled(x, y, x + width, bottom, 0xE8181C24, 7.0f);
            draw.addRect(x, y, x + width, bottom, 0x704B5563, 7.0f, 0, 1.0f);
            drawComponent(draw, component, x + PADDING, y + PADDING,
                    width - PADDING * 2.0f);
        }
    }

    private static float drawComponent(ImDrawList draw, OverlayComponent component,
                                       float x, float y, float width) {
        if (component instanceof OverlayComponent.Text text) {
            draw.addText(x, y, text.muted() ? 0xFF94A3B8 : 0xFFE2E8F0, text.value());
            return y + LINE_HEIGHT;
        }
        if (component instanceof OverlayComponent.Line line) {
            draw.addText(x, y, 0xFFE2E8F0, line.left());
            float rightWidth = ImGui.calcTextSize(line.right()).x;
            draw.addText(Math.max(x, x + width - rightWidth), y, 0xFFCBD5E1, line.right());
            return y + LINE_HEIGHT;
        }
        if (component instanceof OverlayComponent.ProgressBar progress) {
            if (!progress.label().isBlank()) {
                draw.addText(x, y, 0xFFE2E8F0, progress.label());
                y += LINE_HEIGHT;
            }
            float barHeight = 8.0f;
            draw.addRectFilled(x, y, x + width, y + barHeight, 0xFF263241, 4.0f);
            draw.addRectFilled(x, y, x + width * (float) progress.progress(),
                    y + barHeight, 0xFF38BDF8, 4.0f);
            return y + barHeight + GAP;
        }
        if (component instanceof OverlayComponent.ProgressPie progress) {
            String value = progress.label().isBlank()
                    ? Math.round(progress.progress() * 100.0) + "%"
                    : progress.label() + "  " + Math.round(progress.progress() * 100.0) + "%";
            draw.addText(x, y, 0xFFE2E8F0, value);
            return y + LINE_HEIGHT;
        }
        if (component instanceof OverlayComponent.Table table) {
            if (!table.headers().isEmpty()) {
                draw.addText(x, y, 0xFF94A3B8, String.join("   |   ", table.headers()));
                y += LINE_HEIGHT;
            }
            for (List<String> row : table.rows()) {
                draw.addText(x, y, 0xFFE2E8F0, String.join("   |   ", row));
                y += LINE_HEIGHT;
            }
            return y;
        }
        if (component instanceof OverlayComponent.Split split) {
            float firstWidth = width * (float) split.ratio();
            float firstEnd = drawComponent(draw, split.first(), x, y, firstWidth - GAP);
            float secondEnd = drawComponent(draw, split.second(),
                    x + firstWidth + GAP, y, width - firstWidth - GAP);
            return Math.max(firstEnd, secondEnd);
        }
        if (component instanceof OverlayComponent.Panel panel) {
            for (OverlayComponent child : panel.children()) {
                y = drawComponent(draw, child, x, y, width) + GAP;
            }
            return y;
        }
        if (component instanceof OverlayComponent.InfoBox box) {
            float boxHeight = box.progress() >= 0.0 ? 42.0f : 32.0f;
            draw.addRectFilled(x, y, x + width, y + boxHeight,
                    box.attention() ? 0xCC3F2E19 : 0xCC202A36, 5.0f);
            draw.addText(x + 7.0f, y + 5.0f, 0xFF94A3B8, box.title());
            float valueWidth = ImGui.calcTextSize(box.value()).x;
            draw.addText(Math.max(x + 7.0f, x + width - valueWidth - 7.0f),
                    y + 5.0f, 0xFFF8FAFC, box.value());
            if (box.progress() >= 0.0) {
                float barY = y + boxHeight - 8.0f;
                draw.addRectFilled(x + 7.0f, barY, x + width - 7.0f,
                        barY + 3.0f, 0xFF334155, 2.0f);
                draw.addRectFilled(x + 7.0f, barY,
                        x + 7.0f + (width - 14.0f) * (float) box.progress(),
                        barY + 3.0f, 0xFF38BDF8, 2.0f);
            }
            return y + boxHeight;
        }
        if (component instanceof OverlayComponent.Tooltip tooltip) {
            draw.addText(x, y, 0xFFCBD5E1, tooltip.text());
            return y + LINE_HEIGHT;
        }
        return y;
    }

    private static float estimate(OverlayComponent component) {
        if (component instanceof OverlayComponent.Text
                || component instanceof OverlayComponent.Line
                || component instanceof OverlayComponent.ProgressPie
                || component instanceof OverlayComponent.Tooltip) return LINE_HEIGHT;
        if (component instanceof OverlayComponent.ProgressBar bar) {
            return (bar.label().isBlank() ? 0.0f : LINE_HEIGHT) + 8.0f + GAP;
        }
        if (component instanceof OverlayComponent.Table table) {
            return (table.headers().isEmpty() ? 0 : 1) * LINE_HEIGHT
                    + table.rows().size() * LINE_HEIGHT;
        }
        if (component instanceof OverlayComponent.Split split) {
            return Math.max(estimate(split.first()), estimate(split.second()));
        }
        if (component instanceof OverlayComponent.Panel panel) {
            if (panel.children().isEmpty()) return 0.0f;
            float total = panel.children().stream()
                    .map(DeclarativeOverlayRenderer::estimate)
                    .reduce(0.0f, Float::sum);
            return total + GAP * Math.max(0, panel.children().size() - 1);
        }
        if (component instanceof OverlayComponent.InfoBox box) {
            return box.progress() >= 0.0 ? 42.0f : 32.0f;
        }
        return LINE_HEIGHT;
    }

    private static ViewportHudManager.Quadrant quadrant(OverlayPosition position) {
        return switch (position) {
            case TOP_LEFT -> ViewportHudManager.Quadrant.TOP_LEFT;
            case TOP_CENTER -> ViewportHudManager.Quadrant.TOP_CENTER;
            case TOP_RIGHT -> ViewportHudManager.Quadrant.TOP_RIGHT;
            case CENTER_LEFT -> ViewportHudManager.Quadrant.CENTER_LEFT;
            case CENTER -> ViewportHudManager.Quadrant.CENTER;
            case CENTER_RIGHT -> ViewportHudManager.Quadrant.CENTER_RIGHT;
            case BOTTOM_LEFT -> ViewportHudManager.Quadrant.BOTTOM_LEFT;
            case BOTTOM_CENTER -> ViewportHudManager.Quadrant.BOTTOM_CENTER;
            case BOTTOM_RIGHT -> ViewportHudManager.Quadrant.BOTTOM_RIGHT;
        };
    }
}
