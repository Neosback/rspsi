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
