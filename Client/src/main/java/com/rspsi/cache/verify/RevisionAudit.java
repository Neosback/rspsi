package com.rspsi.cache.verify;

import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.map.MapIndexEntry;
import com.rspsi.cache.map.MapIndexTable;
import com.rspsi.cache.map.OsrsRevisionProfile;
import com.rspsi.cache.store.CacheStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Audits revision assumptions before map bytes are decoded. */
public final class RevisionAudit {
    private RevisionAudit() {
    }

    public static List<VerificationCheck> audit(CacheStore store, int requestedRevision,
                                                MapIndexTable index) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(index, "index");
        if (requestedRevision <= 0) throw new IllegalArgumentException("Revision must be positive");
        OsrsRevisionProfile profile = OsrsRevisionProfile.forRevision(requestedRevision);
        List<VerificationCheck> checks = new ArrayList<>();
        checks.add(metadataCheck(store.metadata(requestedRevision).orElse(null), requestedRevision));
        checks.add(layoutCheck(index, profile));
        checks.add(new VerificationCheck("revision.codec",
                VerificationCheck.Status.PASS,
                "terrain=" + (profile.newTerrainFormat() ? "short" : "byte")
                        + ", mapGroups=" + profile.mapGroupLayout()));
        if (profile.mapGroupLayout() == OsrsRevisionProfile.MapGroupLayout.NAMED
                && !store.capabilities().namedArchives()) {
            checks.add(new VerificationCheck("revision.namedArchives", VerificationCheck.Status.WARN,
                    "profile is named but backend does not expose named archive lookup"));
        } else {
            checks.add(new VerificationCheck("revision.namedArchives", VerificationCheck.Status.PASS,
                    "backend capability is compatible with the selected profile"));
        }
        return List.copyOf(checks);
    }

    /**
     * Audits the neutral definition surface without assuming a particular
     * cache library or archive layout. Empty sets remain warnings because a
     * deliberately partial provider is valid for focused tools.
     */
    public static List<VerificationCheck> auditDefinitions(DefinitionProvider definitions) {
        Objects.requireNonNull(definitions, "definitions");
        return List.of(
                definitionCheck("revision.definitions.objects", "objects", definitions.objectIds().size()),
                definitionCheck("revision.definitions.underlays", "underlays", definitions.underlayIds().size()),
                definitionCheck("revision.definitions.overlays", "overlays", definitions.overlayIds().size()),
                definitionCheck("revision.definitions.textures", "textures", definitions.textureIds().size()),
                definitionCheck("revision.definitions.models", "models", definitions.modelIds().size()),
                definitionCheck("revision.definitions.mapScenes", "map-scene sprites", definitions.mapSceneIds().size()),
                definitionCheck("revision.definitions.sequences", "sequences", definitions.sequenceIds().size()),
                definitionCheck("revision.definitions.mapElements", "map elements", definitions.mapElementIds().size()),
                sampleCheck("revision.definitions.sequenceDecode", "sequence",
                        definitions.sequenceIds(), definitions::sequence),
                sampleCheck("revision.definitions.mapElementDecode", "map element",
                        definitions.mapElementIds(), definitions::mapElement),
                modelGeometryCheck(definitions));
    }

    private static VerificationCheck modelGeometryCheck(DefinitionProvider definitions) {
        if (definitions.modelIds().isEmpty()) {
            return new VerificationCheck("revision.definitions.modelGeometry",
                    VerificationCheck.Status.WARN, "no model IDs available for geometry sampling");
        }
        int id = definitions.modelIds().get(0);
        return definitions.modelGeometry(id).isPresent()
                ? new VerificationCheck("revision.definitions.modelGeometry",
                        VerificationCheck.Status.PASS, "model " + id + " geometry decoded")
                : new VerificationCheck("revision.definitions.modelGeometry",
                        VerificationCheck.Status.WARN, "model " + id + " metadata is available but geometry is not");
    }

    private static VerificationCheck sampleCheck(
            String id,
            String name,
            List<Integer> ids,
            java.util.function.IntFunction<java.util.Optional<?>> lookup) {
        if (ids.isEmpty()) {
            return new VerificationCheck(id, VerificationCheck.Status.WARN,
                    "no " + name + " IDs available for lazy decode sampling");
        }
        int sample = ids.get(0);
        return lookup.apply(sample).isPresent()
                ? new VerificationCheck(id, VerificationCheck.Status.PASS,
                        name + " " + sample + " decoded through neutral provider")
                : new VerificationCheck(id, VerificationCheck.Status.FAIL,
                        name + " " + sample + " was indexed but could not be decoded");
    }

    private static VerificationCheck definitionCheck(String id, String name, int count) {
        return new VerificationCheck(id,
                count > 0 ? VerificationCheck.Status.PASS : VerificationCheck.Status.WARN,
                count > 0 ? count + " " + name + " IDs exposed by neutral provider"
                        : "no " + name + " IDs exposed by neutral provider");
    }

    private static VerificationCheck metadataCheck(OsrsCacheMetadata metadata, int requestedRevision) {
        if (metadata == null) {
            return new VerificationCheck("revision.metadata", VerificationCheck.Status.WARN,
                    "backend did not provide cache identity metadata");
        }
        if (metadata.revision() != requestedRevision) {
            return new VerificationCheck("revision.metadata", VerificationCheck.Status.FAIL,
                    "requested revision " + requestedRevision + " but backend reports " + metadata.revision());
        }
        return new VerificationCheck("revision.metadata", VerificationCheck.Status.PASS,
                "revision " + metadata.revision() + ", fingerprint " + metadata.fingerprint());
    }

    private static VerificationCheck layoutCheck(MapIndexTable index, OsrsRevisionProfile profile) {
        long split = index.entries().stream().filter(RevisionAudit::isSplitEntry).count();
        long packed = index.entries().stream().filter(RevisionAudit::isPackedEntry).count();
        long incomplete = index.entries().stream().filter(entry -> !isSplitEntry(entry)
                && !isPackedEntry(entry)).count();
        if (index.size() == 0) {
            return new VerificationCheck("revision.mapLayout", VerificationCheck.Status.FAIL,
                    "no map groups available to audit");
        }
        boolean expectedPacked = profile.mapGroupLayout() == OsrsRevisionProfile.MapGroupLayout.NUMERIC;
        boolean matches = incomplete == 0
                && (expectedPacked ? packed > 0 && split == 0 : split > 0 && packed == 0);
        VerificationCheck.Status status = matches
                ? VerificationCheck.Status.PASS
                : VerificationCheck.Status.FAIL;
        return new VerificationCheck("revision.mapLayout", status,
                "expected=" + profile.mapGroupLayout() + ", split=" + split + ", packed=" + packed
                        + ", incomplete=" + incomplete);
    }

    private static boolean isSplitEntry(MapIndexEntry entry) {
        return entry.landscapeArchiveId() >= 0 && entry.objectArchiveId() >= 0
                && entry.landscapeArchiveId() != entry.objectArchiveId();
    }

    private static boolean isPackedEntry(MapIndexEntry entry) {
        return entry.landscapeArchiveId() >= 0 && entry.objectArchiveId() >= 0
                && entry.landscapeArchiveId() == entry.objectArchiveId();
    }
}
