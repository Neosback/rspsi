package com.rspsi.editor.corpus;

import java.util.List;

/** Extensible feature family used by similarity, training-set and analysis tools. */
public interface RegionFeatureExtractor {
    String id();

    /**
     * Returns one or more blocks. Blocks remain independently weighted and
     * explained in similarity results.
     */
    List<FeatureBlock> extract(RegionFeatureContext context);
}
