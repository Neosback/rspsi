package com.rspsi.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.List;

import com.rspsi.util.FXUtils;
import com.rspsi.util.FXDialogs;
import lombok.Getter;
import org.apache.commons.compress.utils.Lists;

import com.rspsi.controllers.LauncherController;
import com.rspsi.options.Config;
import com.rspsi.resources.ResourceLoader;
import com.rspsi.util.RetentionFileChooser;
import com.rspsi.util.Settings;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import com.rspsi.ui.workspace.StudioDashboard;
import com.rspsi.cache.workspace.CacheSessionState;
import com.rspsi.cache.workspace.CacheSessionStatus;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.cache.workspace.OsrsCacheSessionService;
import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginLoader;
import com.rspsi.editor.plugin.EditorPluginStateStore;
import com.rspsi.editor.plugin.PluginDiscovery;

@Getter
public class LauncherWindow extends Application {

	@Getter
	private static LauncherWindow singleton;

	private Stage primaryStage;
	
	private LauncherController controller;
	private List<String> oldCachePaths;
	private Parent legacySettingsContent;
	private StudioDashboard dashboard;
	private OsrsCacheSessionService cacheSessions;

	@Override
	public void start(Stage primaryStage) throws Exception {
		Settings.loadSettings();
		startDashboard(primaryStage);
	}

	private void startDashboard(Stage primaryStage) throws Exception {
		java.nio.file.Files.createDirectories(Paths.get(System.getProperty("user.home"), ".rspsi"));
		ThemeService.apply(ThemeService.Theme.PRIMER_DARK);
		singleton = this;
		this.primaryStage = primaryStage;
		String savedCache = Settings.getSetting("cacheLocation", "");
		if (savedCache == null || !new File(savedCache).isDirectory()) {
			savedCache = Settings.getSetting("lastCacheLocation", "");
		}
		if (savedCache == null || !new File(savedCache).isDirectory()) savedCache = "";
		oldCachePaths = Settings.getSetting("oldCache", Lists.newArrayList());
		cacheSessions = new OsrsCacheSessionService();

		dashboard = new StudioDashboard(
				(thisCache, region) -> launchEditor(thisCache, region),
				this::chooseCache);
		dashboard.setCachePath(savedCache);
		Scene scene = new Scene(dashboard, 1320, 860);
		var dashboardStyles = getClass().getResource("/css/workspace.css");
		if (dashboardStyles != null) scene.getStylesheets().add(dashboardStyles.toExternalForm());
		scene.setFill(Color.web("#111827"));
		primaryStage.setTitle("OpenRune Content Studio");
		primaryStage.initStyle(StageStyle.DECORATED);
		primaryStage.setScene(scene);
		primaryStage.getIcons().add(ResourceLoader.getSingleton().getLogo64());
		primaryStage.setMinWidth(980);
		primaryStage.setMinHeight(680);
		primaryStage.show();
		FXUtils.centerStage(primaryStage);
		if (!savedCache.isBlank()) {
			loadSelectedCache(Path.of(savedCache));
		}
	}

	private void loadLegacySettingsContent() throws IOException {
		if (legacySettingsContent != null) return;
		FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/loadscreen.fxml"));
		controller = new LauncherController();
		loader.setController(controller);
		legacySettingsContent = loader.load();
		String cacheLoc = Settings.getSetting("cacheLocation", Config.cacheLocation.get());
		controller.getCacheLocation().getEditor().setText(cacheLoc == null ? "" : new File(cacheLoc).getAbsolutePath() + File.separator);
		populatePlugins();
		controller.getCancelButton().setOnAction(event -> showDashboard());
		controller.getBrowseButton().setOnAction(event -> chooseCache());
		controller.getLaunchButton().setOnAction(event -> launchEditor(
				controller.getCacheLocation().getEditor().getText(), ""));
		controller.getDisablePluginButton().setOnAction(event -> disableSelectedPlugin());
		controller.getEnablePluginButton().setOnAction(event -> enableSelectedPlugin());
	}

	private void chooseCache() {
		File selected = RetentionFileChooser.showOpenFolderDialog(primaryStage, null);
		if (selected == null) return;
		String path = selected.getAbsolutePath() + File.separator;
		if (dashboard != null) {
			dashboard.setCachePath(path);
			dashboard.setCacheStatus(new CacheSessionStatus(CacheSessionState.LOADING,
				selected.toPath(), null, "Loading OpenRune cache…", null));
		}
		if (controller != null) controller.getCacheLocation().getEditor().setText(path);
		loadSelectedCache(selected.toPath());
	}

	private void loadSelectedCache(Path path) {
		if (cacheSessions == null) return;
		Path normalized = path.toAbsolutePath().normalize();
		if (dashboard != null) {
			dashboard.setCachePath(normalized.toString() + File.separator);
			dashboard.setCacheStatus(new CacheSessionStatus(CacheSessionState.LOADING,
				normalized, null, "Loading OpenRune cache…", null));
		}
		cacheSessions.load(normalized).whenComplete((loaded, failure) -> Platform.runLater(() -> {
			if (dashboard != null) dashboard.setCacheStatus(cacheSessions.status());
			if (loaded != null && failure == null) {
				String persisted = loaded.path().toString() + File.separator;
				rememberCache(persisted);
			}
		}));
	}

	/**
	 * Persists a cache selected from either the dashboard or an already-open
	 * editor. The launcher is deliberately the owner of this preference so a
	 * cache chosen later is available on the next dashboard startup too.
	 */
	public void rememberCache(String value) {
		if (value == null || value.isBlank()) return;
		String path = new File(value).getAbsolutePath() + File.separator;
		Config.cacheLocation.set(path);
		Settings.properties.put("cacheLocation", path);
		Settings.properties.put("lastCacheLocation", path);
		Settings.saveSettings();
		putOldPath(path);
		if (controller != null) controller.getCacheLocation().getEditor().setText(path);
	}

	private void showDashboard() {
		if (dashboard == null) return;
		String current = Config.cacheLocation.get();
		if (current != null && new File(current).isDirectory()) dashboard.setCachePath(current);
		primaryStage.getScene().setRoot(dashboard);
		primaryStage.setTitle("OpenRune Content Studio");
	}

	private void showLegacySettings() {
		try {
			loadLegacySettingsContent();
		} catch (IOException exception) {
			FXDialogs.showException(primaryStage, "Cannot open legacy settings",
					"The compatibility settings panel could not be loaded.", exception);
			return;
		}
		controller.getCacheLocation().getEditor().setText(dashboard.cachePath());
		populatePlugins();
		primaryStage.getScene().setRoot(legacySettingsContent);
		primaryStage.setTitle("OpenRune Content Studio Settings");
	}

	private void showStudioSettings() {
		FXDialogs.showInformation(primaryStage, "RSPSi Studio settings",
				"Workspace layout, renderer presentation, keybindings, autosave, and diagnostics are available from the Map Editor workspace.\n\n"
						+ "The dashboard keeps cache selection here so opening a workspace does not ask for it again.");
	}

	private void launchEditor(String cachePath, String region) {
		if (cacheSessions == null || cachePath == null || cachePath.isBlank()) {
			FXDialogs.showWarning(primaryStage, "Cache required",
					"Choose and load a valid OSRS cache on the dashboard before opening Map Editor.");
			return;
		}
		Path normalizedPath = Path.of(cachePath).toAbsolutePath().normalize();
		LoadedOsrsCacheSession loaded = cacheSessions.current()
				.filter(session -> session.path().equals(normalizedPath)).orElse(null);
		if (loaded == null) {
			FXDialogs.showWarning(primaryStage, "Cache is still loading",
					"Wait for the Dashboard to report the selected OpenRune cache as ready.");
			return;
		}
		String normalized = normalizedPath + File.separator;
		Config.cacheLocation.set(normalized);
		rememberCache(normalized);
		MainWindow window = new MainWindow();
		window.setStartupCacheSession(loaded);
		// Keep the first launch useful and deterministic: region 50,50 is
		// Lumbridge. The field remains editable for another region or world
		// coordinate pair before opening the workspace.
		String requestedRegion = region == null || region.isBlank() ? "50,50" : region;
		window.setStartupRegion(normalizeRegion(requestedRegion));
		Stage editorStage = new Stage();
		editorStage.setX(primaryStage.getX());
		editorStage.setY(primaryStage.getY());
		try {
			window.start(editorStage);
			primaryStage.hide();
		} catch (Exception exception) {
			FXDialogs.showException(primaryStage, "Cannot open Map Editor", "The editor could not be started.", exception);
		}
	}

	@Override
	public void stop() {
		if (cacheSessions != null) {
			cacheSessions.close();
			cacheSessions = null;
		}
	}

	/** Dashboard input is region-oriented; convert regionX,regionY to a region ID. */
	private static String normalizeRegion(String value) {
		if (value == null || value.isBlank()) return "";
		String normalized = value.replace(" ", "").trim();
		if (!normalized.contains(",")) return normalized;
		String[] parts = normalized.split(",");
		if (parts.length != 2) return normalized;
		try {
			int x = Integer.parseInt(parts[0]);
			int y = Integer.parseInt(parts[1]);
			if (x < 0 || x > 255 || y < 0 || y > 255) return normalized;
			return String.valueOf((x << 8) | y);
		} catch (NumberFormatException exception) {
			return normalized;
		}
	}

	private void disableSelectedPlugin() {
		String pluginName = controller.getEnabledPlugins().getFocusModel().getFocusedItem();
		if (pluginName == null) return;
		EditorPluginStateStore state = EditorPluginStateStore.defaultStore();
		java.util.LinkedHashSet<String> disabled = new java.util.LinkedHashSet<>(state.disabledIds());
		disabled.add(pluginName);
		state.replaceDisabled(disabled);
		populatePlugins();
	}

	private void enableSelectedPlugin() {
		String pluginName = controller.getDisabledPlugins().getFocusModel().getFocusedItem();
		if (pluginName == null) return;
		EditorPluginStateStore state = EditorPluginStateStore.defaultStore();
		java.util.LinkedHashSet<String> disabled = new java.util.LinkedHashSet<>(state.disabledIds());
		disabled.remove(pluginName);
		state.replaceDisabled(disabled);
		populatePlugins();
	}

	private void putOldPath(String path) {
		if(!oldCachePaths.contains(path)) {
			oldCachePaths.add(0, path);
			Settings.putSetting("oldCache", oldCachePaths);
			fillOldPaths();
		}
	}
	
	private void fillOldPaths() {
		if (controller == null) return;
		controller.getCacheLocation().getItems().clear();
		controller.getCacheLocation().getItems().addAll(oldCachePaths);
	}
	
	public void populatePlugins() {
		controller.getEnabledPlugins().getItems().clear();
		controller.getDisabledPlugins().getItems().clear();
		EditorPluginStateStore state = EditorPluginStateStore.defaultStore();
		PluginDiscovery discovery = EditorPluginLoader.discoverOwned(
				Path.of("plugins"), Thread.currentThread().getContextClassLoader());
		try {
			for (EditorPlugin plugin : discovery.plugins()) {
				if (state.isEnabled(plugin.id())) {
					controller.getEnabledPlugins().getItems().add(plugin.id());
				} else {
					controller.getDisabledPlugins().getItems().add(plugin.id());
				}
			}
		} finally {
			discovery.close();
		}
	}
	
	public static void main(String[] args) {
		launch(args);
	}

}
