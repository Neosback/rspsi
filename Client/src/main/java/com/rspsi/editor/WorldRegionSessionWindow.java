package com.rspsi.editor;

import com.rspsi.editor.change.ChangePlan;
import com.rspsi.editor.change.ChangePlanRejectedException;
import com.rspsi.editor.change.ChangePlanValidation;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldTileAddress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Canonical authoring view over several loaded OSRS regions.
 *
 * <p>Each 64x64 region retains its own {@link EditorSession} and
 * {@code WorldDocument}; this type never materializes a second mutable merged
 * document. Absolute world coordinates are resolved to the owning region and
 * then to that session's local tile. Missing regions remain explicit holes.</p>
 */
public final class WorldRegionSessionWindow {
    private final WorldRegionWindow window;
    private final Map<Integer, EditorSession> sessions;
    private final WorldRegionSaveHandler saveHandler;
    private final WorldRegionChangeHistory changeHistory = new WorldRegionChangeHistory();

    public WorldRegionSessionWindow(WorldRegionWindow window,
                                    Map<Integer, EditorSession> sessions) {
        this(window, sessions, null);
    }

    public WorldRegionSessionWindow(WorldRegionWindow window,
                                    Map<Integer, EditorSession> sessions,
                                    WorldRegionSaveHandler saveHandler) {
        this.window = Objects.requireNonNull(window, "window");
        Objects.requireNonNull(sessions, "sessions");

        Map<Integer, EditorSession> copy = new LinkedHashMap<>();
        for (var entry : sessions.entrySet()) {
            int regionId = entry.getKey();
            EditorSession session = Objects.requireNonNull(entry.getValue(), "session");
            WorldRegion region = window.regions().get(regionId);
            if (region == null) {
                throw new IllegalArgumentException(
                        "Session provided for region outside loaded window: " + regionId);
            }
            if (session.world() != region.document()) {
                throw new IllegalArgumentException(
                        "Region session must own the canonical loaded document: " + regionId);
            }
            if (!session.window().equals(region.window())) {
                throw new IllegalArgumentException(
                        "Region session world window does not match region: " + regionId);
            }
            copy.put(regionId, session);
        }

        if (!copy.keySet().equals(window.regions().keySet())) {
            Set<Integer> missingSessions = new TreeSet<>(window.regions().keySet());
            missingSessions.removeAll(copy.keySet());
            throw new IllegalArgumentException(
                    "Every loaded region must have exactly one editor session; missing="
                            + missingSessions);
        }

        this.sessions = Map.copyOf(copy);
        this.saveHandler = saveHandler;
    }

    public WorldRegionWindow window() {
        return window;
    }

    public Map<Integer, EditorSession> sessions() {
        return sessions;
    }

    public Optional<EditorSession> session(int regionX, int regionY) {
        if (regionX < 0 || regionX > 255 || regionY < 0 || regionY > 255) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get((regionX << 8) | regionY));
    }

    public Optional<EditorSession> session(int regionId) {
        return Optional.ofNullable(sessions.get(regionId));
    }

    /**
     * Resolves an absolute OSRS tile to the canonical loaded region session
     * and that session's local 0..63 tile coordinate.
     */
    public Optional<ResolvedTile> resolve(WorldTile worldTile) {
        if (worldTile == null || worldTile.plane() < 0) return Optional.empty();
        WorldTileAddress address = WorldTileAddress.of(
                worldTile.x(), worldTile.y(), worldTile.plane());
        EditorSession session = sessions.get(address.regionId());
        if (session == null) return Optional.empty();

        Optional<LocalTile> local = session.coordinates().toLocal(worldTile);
        if (local.isEmpty()) return Optional.empty();
        return Optional.of(new ResolvedTile(address, session, local.get()));
    }

    public Set<Integer> loadedRegionIds() {
        return sessions.keySet();
    }

    public Set<Integer> missingRegionIds() {
        return window.missingRegionIds();
    }

    public Set<Integer> dirtyRegionIds() {
        Set<Integer> dirty = new TreeSet<>();
        for (var entry : sessions.entrySet()) {
            if (entry.getValue().isSessionSaveDirty()) dirty.add(entry.getKey());
        }
        return Set.copyOf(dirty);
    }

    public boolean isDirty() {
        return sessions.values().stream().anyMatch(EditorSession::isSessionSaveDirty);
    }

    public boolean canEdit(WorldTile worldTile) {
        return resolve(worldTile).map(value -> value.session().canEdit()).orElse(false);
    }

    public boolean canSave() {
        return saveHandler != null;
    }

    /** One user-visible history for transactions committed through {@link #commit(ChangePlan)}. */
    public WorldRegionChangeHistory changeHistory() {
        return changeHistory;
    }

    /**
     * Validates a plan against the current authored window without mutating any
     * region. Preview/tooling code should call this before offering Commit.
     */
    public ChangePlanValidation validate(ChangePlan plan) {
        Objects.requireNonNull(plan, "plan");
        List<ChangePlanValidation.Conflict> conflicts = new ArrayList<>();

        for (ChangePlan.TileChange change : plan.tileChanges().values()) {
            Optional<ResolvedTile> resolvedOpt = resolve(change.tile());
            if (resolvedOpt.isEmpty()) {
                conflicts.add(new ChangePlanValidation.Conflict(
                        ChangePlanValidation.ConflictCode.UNLOADED_REGION,
                        change.tile(),
                        "The owning OSRS region is not loaded in the authoring window"));
                continue;
            }

            ResolvedTile resolved = resolvedOpt.get();
            EditorSession session = resolved.session();
            if (!session.canEdit()) {
                conflicts.add(new ChangePlanValidation.Conflict(
                        ChangePlanValidation.ConflictCode.READ_ONLY_REGION,
                        change.tile(),
                        "The owning region is inspect-only and cannot be edited"));
                continue;
            }

            var current = session.world().tile(resolved.localTile()).snapshot();
            if (!current.equals(change.before())) {
                conflicts.add(new ChangePlanValidation.Conflict(
                        ChangePlanValidation.ConflictCode.STALE_SOURCE,
                        change.tile(),
                        "The authored tile changed after this plan was calculated"));
            }
        }

        return new ChangePlanValidation(conflicts);
    }

    /**
     * Validates and atomically commits one world-space change plan.
     *
     * <p>Every affected tile is resolved, checked for editability, and compared
     * with the plan's expected-before state before the first region mutates.
     * The plan is then partitioned into one composite command per region and
     * recorded as one window-level undo/redo entry.</p>
     *
     * @return false when the plan contains no effective tile changes.
     * @throws ChangePlanRejectedException when validation finds any conflict.
     */
    public boolean commit(ChangePlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (plan.isEmpty()) return false;

        ChangePlanValidation validation = validate(plan);
        if (!validation.canCommit()) {
            throw new ChangePlanRejectedException(validation);
        }

        Map<Integer, List<EditorCommand>> byRegion = new TreeMap<>();
        Map<Integer, EditorSession> touchedSessions = new TreeMap<>();

        for (ChangePlan.TileChange change : plan.tileChanges().values()) {
            ResolvedTile resolved = resolve(change.tile()).orElseThrow();
            EditorSession session = resolved.session();
            touchedSessions.put(resolved.regionId(), session);
            byRegion.computeIfAbsent(resolved.regionId(), ignored -> new ArrayList<>())
                    .add(new SetTileCommand(
                            resolved.localTile().coordinate(),
                            change.before(),
                            change.after(),
                            plan.description()));
        }

        List<WorldRegionChangeHistory.PendingRegionEdit> edits =
                new ArrayList<>(byRegion.size());
        for (var entry : byRegion.entrySet()) {
            EditorSession session = touchedSessions.get(entry.getKey());
            EditorCommand command = new CompositeEditCommand(
                    plan.description(), entry.getValue());
            edits.add(new WorldRegionChangeHistory.PendingRegionEdit(
                    entry.getKey(), session, command));
        }

        changeHistory.execute(plan.description(), edits);
        return true;
    }

    public boolean undoChangePlan() {
        return changeHistory.undo();
    }

    public boolean redoChangePlan() {
        return changeHistory.redo();
    }

    /**
     * Persists dirty loaded regions through one cache-neutral batch boundary.
     * No-op when the window is already clean.
     */
    public void save() {
        if (saveHandler == null) {
            throw new IllegalStateException("World-region window has no save handler");
        }
        if (!isDirty()) return;
        saveHandler.save(this);
    }

    public record ResolvedTile(
            WorldTileAddress address,
            EditorSession session,
            LocalTile localTile
    ) {
        public ResolvedTile {
            address = Objects.requireNonNull(address, "address");
            session = Objects.requireNonNull(session, "session");
            localTile = Objects.requireNonNull(localTile, "localTile");
            if (address.regionLocalX() != localTile.x()
                    || address.regionLocalY() != localTile.y()
                    || address.plane() != localTile.plane()) {
                throw new IllegalArgumentException(
                        "Resolved world/local coordinates disagree");
            }
        }

        public int regionId() {
            return address.regionId();
        }

        public WorldTile worldTile() {
            return new WorldTile(address.plane(), address.worldX(), address.worldY());
        }
    }
}
