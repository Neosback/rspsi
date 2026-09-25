package com.rspsi.server.openrune;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRuneObjectOverlayIndexerTest {
    @Test
    void indexesBundledLumbridgeObjectOverlayWithFieldProvenance() {
        Path server = bundledServerRoot();
        Path file = server.resolve(
                "content/areas/city/lumbridge/pack/src/main/resources/pack/configs/lumbridge_locs.toml");
        assertTrue(Files.isRegularFile(file), "bundled Lumbridge loc config is missing");

        var symbols = new OpenRuneSymbolProvider(server);
        var index = new OpenRuneObjectOverlayIndexer().indexFiles(List.of(file), symbols);
        var overlay = index.bySymbol("loc.farming_shed_poordoor").orElseThrow();

        assertEquals("loc.farming_shed_poordoor", overlay.objectSymbol());
        assertEquals("loc.farming_shed_poordoor", overlay.inheritSymbol().orElseThrow());
        assertEquals("content.closed_single_door", overlay.contentGroupSymbol().orElseThrow());
        assertEquals("loc.fai_barbarian_poordooropen",
                overlay.params().get("param.next_loc_stage"));
        assertTrue(overlay.writableSource());

        assertEquals(file.toAbsolutePath().normalize(), overlay.blockSource().file());
        assertEquals(1, overlay.blockSource().startLine());
        assertEquals(2, overlay.fieldSource("id").orElseThrow().startLine());
        assertEquals(4, overlay.fieldSource("contentGroup").orElseThrow().startLine());
        assertEquals(7,
                overlay.fieldSource("param:param.next_loc_stage").orElseThrow().startLine());
    }

    private static Path bundledServerRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path direct = cwd.resolve("OpenRune-Server-main");
        if (Files.isDirectory(direct)) return direct;

        Path parent = cwd.getParent();
        if (parent != null) {
            Path sibling = parent.resolve("OpenRune-Server-main");
            if (Files.isDirectory(sibling)) return sibling;
        }
        throw new IllegalStateException("OpenRune-Server-main is not available from " + cwd);
    }
}
