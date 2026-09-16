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

Record the cache revision, map/region used, operating system, and any failure in the roadmap ledger.
