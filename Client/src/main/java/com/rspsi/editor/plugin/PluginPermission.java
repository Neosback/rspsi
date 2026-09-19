package com.rspsi.editor.plugin;

/**
 * Coarse permissions declared by editor plugins.
 *
 * <p>These permissions define the capability contract before Plugin Hub
 * distribution. Plugins declare required permissions in their descriptor.</p>
 */
public enum PluginPermission {
    WORLD_READ,
    WORLD_EDIT,
    ASSET_READ,
    ASSET_CREATE,
    PROJECT_READ,
    PROJECT_WRITE,
    CACHE_BUILD,
    LIVE_CLIENT_READ,
    LIVE_CLIENT_CONTROL,
    NETWORK,
    FILESYSTEM_PROJECT,
    FILESYSTEM_EXTERNAL
}
