package com.rspsi.editor.corpus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explainable similarity index. Each feature block is cosine-scored
 * independently and combined with caller-selected weights.
 */
public final class RegionSimilarityIndex {
    private final Map<Integer, RegionFingerprint> fingerprints = new LinkedHashMap<>();

    public synchronized void put(RegionFingerprint fingerprint) {
        fingerprints.put(Objects.requireNonNull(fingerprint, "fingerprint").regionId(), fingerprint);
    }

    public synchronized void putAll(Iterable<RegionFingerprint> values) {
        for (RegionFingerprint value : values) put(value);
    }

    public synchronized RegionFingerprint get(int regionId) {
        return fingerprints.get(regionId);
    }

    public synchronized int size() {
        return fingerprints.size();
    }

    public synchronized List<SimilarityMatch> similar(int regionId, int limit,
                                                      Map<String, Double> blockWeights) {
        RegionFingerprint target = fingerprints.get(regionId);
        if (target == null) throw new IllegalArgumentException("Unknown region: " + regionId);
        if (limit <= 0) return List.of();
        Map<String, Double> weights = blockWeights == null ? Map.of() : blockWeights;

        List<SimilarityMatch> matches = new ArrayList<>();
        for (RegionFingerprint candidate : fingerprints.values()) {
            if (candidate.regionId() == regionId) continue;
            Map<String, Double> scores = new LinkedHashMap<>();
            double weighted = 0.0;
            double totalWeight = 0.0;
            for (Map.Entry<String, FeatureBlock> entry : target.blocks().entrySet()) {
                FeatureBlock other = candidate.blocks().get(entry.getKey());
                if (other == null) continue;
                double score = cosine(entry.getValue(), other);
                scores.put(entry.getKey(), score);
                double weight = Math.max(0.0, weights.getOrDefault(entry.getKey(), 1.0));
                weighted += score * weight;
                totalWeight += weight;
            }
            double total = totalWeight == 0.0 ? 0.0 : weighted / totalWeight;
            matches.add(new SimilarityMatch(candidate.regionId(), candidate.regionX(),
                    candidate.regionY(), total, scores));
        }
        return matches.stream()
                .sorted(Comparator.comparingDouble(SimilarityMatch::score).reversed()
                        .thenComparingInt(SimilarityMatch::regionId))
                .limit(limit).toList();
    }

    public synchronized List<RegionFingerprint> fingerprints() {
        return List.copyOf(fingerprints.values());
    }

    private static double cosine(FeatureBlock first, FeatureBlock second) {
        if (first.values().isEmpty() || second.values().isEmpty()) return 0.0;
        double dot = 0.0;
        for (Map.Entry<String, Double> entry : first.values().entrySet()) {
            dot += entry.getValue() * second.values().getOrDefault(entry.getKey(), 0.0);
        }
        double denominator = first.magnitude() * second.magnitude();
        return denominator == 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, dot / denominator));
    }

    public record SimilarityMatch(
            int regionId,
            int regionX,
            int regionY,
            double score,
            Map<String, Double> blockScores) {
        public SimilarityMatch {
            blockScores = Map.copyOf(blockScores);
        }

        public double blockScore(String id) {
            return blockScores.getOrDefault(id, 0.0);
        }
    }
}
