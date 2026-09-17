# RSPSi Manual Smoke Test

Run after changes affecting loading, editing, rendering, saving, or plugins.

1. Run `./gradlew test`.
2. Launch the editor with `./gradlew :Editor:run`.
3. Load a known 317/legacy cache and confirm the loading screen completes.
4. Open a map and verify camera movement, tile hover, plane switching, and minimap output.
5. Paint an underlay and overlay, change a flag, and modify a height.
6. Select, place, and delete an object.
7. Copy/import a tile selection and verify rotation/plane options.
8. Undo and redo each edit; confirm the visual state returns exactly.
9. Export map/object files, close, reopen, and verify the edits remain.
10. Confirm autosave recovery after a controlled restart when an autosave exists.

For the opt-in controlled workspace migration, set `controlledWorkspace` to
`true` in `~/.rspsi/settings.json` and repeat steps 2–10. Confirm that the
legacy renderer remains interactive in the centered viewport and that the
menu bar, tool rail, and asset pane remain reachable. After a map finishes
loading, confirm the History panel reports the bound session and the
Inspector panel updates when a neutral selection is made. Open the Validation
tab and confirm it reports either “World is valid” or actionable diagnostics
with a plane/tile location; make an edit and confirm the diagnostics refresh.
When an OSRS-backed asset repository is supplied, use the Assets pane to
search by display name, numeric ID, and symbolic key, switch categories, move
through results with the keyboard, and confirm the selected definition details
are visible. Confirm that an unavailable repository leaves the legacy asset
pane intact.
Remove the setting (or set it to `false`) to return to the default legacy
layout.

Record the cache revision, map/region used, operating system, and any failure in the roadmap ledger.

For the OSRS project foundation, also verify that a project directory contains
`project.json`, `autosave/`, and `edits/`; opening it against its recorded cache
is compatible, while opening it against a different revision or fingerprint
is clearly read-only. The staged output/session API is covered separately by
automated tests; the JavaFX OpenRune workflow is intentionally read-only until
the native writer gate passes.

For the opt-in OSRS project workflow:

- [ ] Enable `controlledWorkspace` and launch RSPSi.
- [ ] Choose `File > Open from > OSRS project…`.
- [ ] Select a project folder containing `project.json`.
- [ ] Select the OSRS cache recorded by that project.
- [ ] Enter a valid starting region such as `50,50`.
- [ ] Confirm the selected region appears in the controlled workspace and the
      canonical top-down scene preview, inspector, history, assets, validation,
      and status panels are populated.
- [ ] Click a terrain tile in the canonical preview and confirm the neutral
      selection and inspector update to the selected plane/tile.
- [ ] In the canonical tool rail, enter valid local start/target coordinates,
      run Route, LOS, and Reach previews, and confirm the viewport shows the
      result plus a readable success/blocked status. Select an object before
      running Reach and confirm its resolved footprint is used.
- [ ] Confirm the status row clearly marks the OpenRune project read-only.
- [ ] Close/relaunch and confirm the legacy map workflow still opens normally.
