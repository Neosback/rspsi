package com.rspsi.editor.settings;

/** Small command-line verifier used by the Gradle foundation gate. */
public final class SettingsContractVerifier {
    private SettingsContractVerifier() {}

    public static void main(String[] args) {
        SettingsContractValidator.validateOrThrow(
                EditorSettingKeys.registry(), EditorSettingKeys.consumerCatalog());
        System.out.println("Settings contract: PASS");
    }
}
