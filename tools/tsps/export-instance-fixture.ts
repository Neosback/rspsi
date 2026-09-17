/**
 * Export a deterministic TSPS instance-scene fixture from a checked-out cache.
 *
 * This creates a valid 4x13x13 OSRS template grid using chunks from one source
 * region. It is intentionally a reference-tool fixture, not a product
 * dependency; the Java verifier compares the packed templates, terrain fields,
 * and transformed object anchors against RSPSi's neutral instance builder.
 *
 *   TSPS_CLIENT_ROOT=/path/to/TSPS/client \
 *   npx tsx tools/tsps/export-instance-fixture.ts 16 33 /tmp/instance.json
 */
import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";

type Module = Record<string, any>;

async function importTsps(clientRoot: string, relativePath: string): Promise<Module> {
    return import(pathToFileURL(path.join(clientRoot, relativePath)).href);
}

function packTemplateChunk(plane: number, chunkX: number, chunkY: number, rotation: number): number {
    return ((plane & 3) << 24)
        | ((chunkX & 0x3ff) << 14)
        | ((chunkY & 0x7ff) << 3)
        | ((rotation & 3) << 1);
}

function tagId(tag: bigint): number {
    return Number(tag >> 17n);
}

function addObject(objects: Map<string, Record<string, number>>, plane: number,
                  x: number, y: number, tag: bigint, flags: number): void {
    if (!tag) return;
    const object = {
        id: tagId(tag),
        type: flags & 0x1f,
        rotation: (flags >>> 6) & 3,
        plane,
        x,
        y,
    };
    const key = `${object.id}:${object.type}:${object.rotation}:${plane}:${x}:${y}`;
    objects.set(key, object);
}

function exportObjects(scene: any): Record<string, number>[] {
    const objects = new Map<string, Record<string, number>>();
    for (let plane = 0; plane < 4; plane++) {
        for (let x = 0; x < scene.sizeX; x++) {
            for (let y = 0; y < scene.sizeY; y++) {
                const tile = scene.tiles[plane][x][y];
                if (!tile) continue;
                if (tile.wall) addObject(objects, plane, x, y, tile.wall.tag, tile.wall.flags);
                if (tile.wallDecoration) {
                    addObject(objects, plane, x, y,
                        tile.wallDecoration.tag, tile.wallDecoration.flags);
                }
                if (tile.floorDecoration) {
                    addObject(objects, plane, x, y,
                        tile.floorDecoration.tag, tile.floorDecoration.flags);
                }
                for (const loc of tile.locs) {
                    if (loc.startX === x && loc.startY === y) {
                        addObject(objects, plane, x, y, loc.tag, loc.flags);
                    }
                }
            }
        }
    }
    return [...objects.values()].sort((a, b) =>
        a.id - b.id || a.plane - b.plane || a.x - b.x || a.y - b.y
        || a.type - b.type || a.rotation - b.rotation);
}

async function main(): Promise<void> {
    const clientRoot = process.env.TSPS_CLIENT_ROOT;
    if (!clientRoot) throw new Error("Set TSPS_CLIENT_ROOT to a checked-out TSPS/client directory");
    const sourceRegionX = Number(process.argv[2] ?? 16);
    const sourceRegionY = Number(process.argv[3] ?? 33);
    const output = process.argv[4] ?? "/tmp/rspsi-tsps-instance/instance.json";

    const cache = await importTsps(clientRoot, "scripts/cache/load-util.ts");
    const { CacheSystem } = await importTsps(clientRoot, "rs/cache/CacheSystem.ts");
    const { getCacheLoaderFactory } = await importTsps(
        clientRoot, "rs/cache/loader/CacheLoaderFactory.ts");
    const { LocModelLoader } = await importTsps(
        clientRoot, "rs/config/loctype/LocModelLoader.ts");
    const { LocLoadType, SceneBuilder } = await importTsps(
        clientRoot, "rs/scene/SceneBuilder.ts");

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

    const templates: number[][][] = [];
    for (let plane = 0; plane < 4; plane++) {
        templates[plane] = [];
        for (let chunkX = 0; chunkX < 13; chunkX++) {
            templates[plane][chunkX] = [];
            for (let chunkY = 0; chunkY < 13; chunkY++) {
                const sourceChunkX = sourceRegionX * 8 + (chunkX % 8);
                const sourceChunkY = sourceRegionY * 8 + (chunkY % 8);
                templates[plane][chunkX][chunkY] = packTemplateChunk(
                    (plane + chunkX) & 3,
                    sourceChunkX,
                    sourceChunkY,
                    (plane + chunkX + chunkY) & 3,
                );
            }
        }
    }

    const scene = builder.buildInstanceScene(
        templates, 0, 0, 1, 1, false, LocLoadType.NO_MODELS);
    const tileCount = 4 * 104 * 104;
    const heights: number[] = [];
    const underlays: number[] = [];
    const overlays: number[] = [];
    const shapes: number[] = [];
    const rotations: number[] = [];
    const flags: number[] = [];
    for (let plane = 0; plane < 4; plane++) {
        for (let x = 0; x < 104; x++) {
            for (let y = 0; y < 104; y++) {
                heights.push(scene.tileHeights[plane][x][y]);
                underlays.push(scene.tileUnderlays[plane][x][y]);
                overlays.push(scene.tileOverlays[plane][x][y]);
                shapes.push(scene.tileShapes[plane][x][y]);
                rotations.push(scene.tileRotations[plane][x][y]);
                flags.push(scene.tileRenderFlags[plane][x][y]);
            }
        }
    }
    if (heights.length !== tileCount) throw new Error("Unexpected instance terrain size");

    fs.mkdirSync(path.dirname(output), { recursive: true });
    fs.writeFileSync(output, `${JSON.stringify({
        formatVersion: 1,
        width: 104,
        length: 104,
        planes: 4,
        sourceRegionX,
        sourceRegionY,
        templates,
        heights,
        underlays,
        overlays,
        shapes,
        rotations,
        flags,
        objects: exportObjects(scene),
    }, null, 2)}\n`);
    fs.writeFileSync(path.join(path.dirname(output), "instance.properties"), [
        `source.region.x=${sourceRegionX}`,
        `source.region.y=${sourceRegionY}`,
        `revision=${loaded.info.revision}`,
        "scene.width=104",
        "scene.length=104",
        "scene.planes=4",
        "",
    ].join("\n"));
    console.log(`wrote ${output}`);
    console.log(`instance objects: ${exportObjects(scene).length}`);
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
