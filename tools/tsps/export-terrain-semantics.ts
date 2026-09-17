/**
 * Export a TSPS terrain-semantic fixture for RSPSi verification.
 *
 * This is a reference-tool integration, not a product dependency. Run it
 * with TSPS_CLIENT_ROOT pointing at a checked-out TSPS client whose cache
 * configuration already selects the desired reference cache:
 *
 *   TSPS_CLIENT_ROOT=/path/to/TSPS/client \
 *   npx tsx tools/tsps/export-terrain-semantics.ts 50 50 /tmp/fixture/terrain-semantics.json
 *
 * The companion locations file is written beside the terrain file.
 */
import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";

type Module = Record<string, any>;

async function importTsps(clientRoot: string, relativePath: string): Promise<Module> {
    return import(pathToFileURL(path.join(clientRoot, relativePath)).href);
}

async function main(): Promise<void> {
    const clientRoot = process.env.TSPS_CLIENT_ROOT;
    if (!clientRoot) {
        throw new Error("Set TSPS_CLIENT_ROOT to a checked-out TSPS/client directory");
    }
    const regionX = Number(process.argv[2] ?? 50);
    const regionY = Number(process.argv[3] ?? 50);
    const output = process.argv[4] ?? "/tmp/rspsi-tsps-parity-fixture/terrain-semantics.json";

    const cache = await importTsps(clientRoot, "scripts/cache/load-util.ts");
    const { CacheSystem } = await importTsps(clientRoot, "rs/cache/CacheSystem.ts");
    const { getCacheLoaderFactory } = await importTsps(
        clientRoot,
        "rs/cache/loader/CacheLoaderFactory.ts",
    );
    const { LocModelLoader } = await importTsps(clientRoot, "rs/config/loctype/LocModelLoader.ts");
    const { LocLoadType, SceneBuilder } = await importTsps(
        clientRoot,
        "rs/scene/SceneBuilder.ts",
    );

    const cacheInfo = cache.loadCacheList(cache.loadCacheInfos()).latest;
    const loaded = cache.loadCache(cacheInfo);
    const cacheSystem = CacheSystem.fromFiles(cacheInfo, loaded.files);
    const factory = getCacheLoaderFactory(cacheInfo, cacheSystem);
    const loc = factory.getLocTypeLoader();
    const locModels = new LocModelLoader(
        loc,
        factory.getModelLoader(),
        factory.getTextureLoader(),
        factory.getSeqTypeLoader(),
        factory.getSeqFrameLoader(),
        factory.getSkeletalSeqLoader(),
    );
    const builder = new SceneBuilder(
        cacheInfo,
        factory.getMapFileLoader(),
        factory.getUnderlayTypeLoader(),
        factory.getOverlayTypeLoader(),
        loc,
        locModels,
        loaded.xteas,
    );
    const scene = builder.buildScene(
        regionX * 64,
        regionY * 64,
        64,
        64,
        false,
        LocLoadType.NO_MODELS,
    );

    const heights: number[] = [];
    const underlays: number[] = [];
    const overlays: number[] = [];
    const shapes: number[] = [];
    const rotations: number[] = [];
    const flags: number[] = [];
    for (let plane = 0; plane < 4; plane++) {
        for (let x = 0; x < 64; x++) {
            for (let y = 0; y < 64; y++) {
                heights.push(scene.tileHeights[plane][x][y]);
                underlays.push(scene.tileUnderlays[plane][x][y]);
                overlays.push(scene.tileOverlays[plane][x][y]);
                shapes.push(scene.tileShapes[plane][x][y]);
                rotations.push(scene.tileRotations[plane][x][y]);
                flags.push(scene.tileRenderFlags[plane][x][y]);
            }
        }
    }

    fs.mkdirSync(path.dirname(output), { recursive: true });
    fs.writeFileSync(output, `${JSON.stringify({
        formatVersion: 1,
        width: 64,
        length: 64,
        planes: 4,
        heights,
        underlays,
        overlays,
        shapes,
        rotations,
        flags,
    }, null, 2)}\n`);
    // Location parity uses TSPS's cache-byte semantics directly. Scene tile
    // containers intentionally reject some edge/crowded placements, which is
    // useful for rendering but would make them a lossy map decoder oracle.
    const { ByteBuffer } = await importTsps(clientRoot, "rs/io/ByteBuffer.ts");
    const locationData = factory.getMapFileLoader().getLocData(regionX, regionY, loaded.xteas);
    if (!locationData) throw new Error(`No location data for ${regionX},${regionY}`);
    const buffer = new ByteBuffer(locationData);
    const objects: Record<string, number>[] = [];
    let id = -1;
    while (true) {
        const idDelta = buffer.readSmart3();
        if (idDelta === 0) break;
        id += idDelta;
        let packedPosition = 0;
        while (true) {
            const positionDelta = buffer.readUnsignedSmart();
            if (positionDelta === 0) break;
            packedPosition += positionDelta - 1;
            const attributes = buffer.readUnsignedByte();
            objects.push({
                id,
                type: attributes >>> 2,
                rotation: attributes & 0x3,
                plane: (packedPosition >>> 12) & 0x3,
                x: (packedPosition >>> 6) & 0x3f,
                y: packedPosition & 0x3f,
            });
        }
    }
    const locationPath = path.join(path.dirname(output), "locations.json");
    fs.writeFileSync(locationPath, `${JSON.stringify({
        formatVersion: 1,
        objects: objects.sort((a, b) =>
            a.id - b.id || a.plane - b.plane || a.x - b.x || a.y - b.y
                || a.type - b.type || a.rotation - b.rotation),
    }, null, 2)}\n`);
    console.log(`wrote ${output}`);
    console.log(`wrote ${locationPath}`);
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
