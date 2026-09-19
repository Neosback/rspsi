package com.rspsi.editor.integration.reference;

import com.rspsi.editor.symbols.SymbolNamespace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Universal Studio service coordinating source code cross-references for game assets.
 */
public final class ReferenceService {
    private final List<ReferenceProvider> providers = new CopyOnWriteArrayList<>();

    public void registerProvider(ReferenceProvider provider) {
        Objects.requireNonNull(provider, "provider");
        providers.add(provider);
    }

    public void unregisterProvider(String providerId) {
        providers.removeIf(p -> p.id().equals(providerId));
    }

    public List<ContentReference> referencesFor(SymbolNamespace namespace, int id, String symbolicName) {
        Objects.requireNonNull(namespace, "namespace");
        List<ContentReference> result = new ArrayList<>();
        for (ReferenceProvider provider : providers) {
            result.addAll(provider.referencesFor(namespace, id, symbolicName));
        }
        return Collections.unmodifiableList(result);
    }

    public int totalReferenceCount() {
        return providers.stream().mapToInt(ReferenceProvider::totalReferenceCount).sum();
    }
}
