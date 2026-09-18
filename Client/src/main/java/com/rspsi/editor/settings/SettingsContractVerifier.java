package com.rspsi.editor.settings;

import com.rspsi.editor.render.RenderSettingKeys;

/** Small command-line verifier used by the Gradle foundation gate. */
public final class SettingsContractVerifier {
    private SettingsContractVerifier() {}

    public static void main(String[] args) {
        SettingsContractValidator.validateOrThrow(
                RenderSettingKeys.registry(), RenderSettingKeys.consumerCatalog());
        System.out.println("Settings contract: PASS");
    }
}
