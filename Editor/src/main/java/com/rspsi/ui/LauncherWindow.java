package com.rspsi.ui;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import com.rspsi.util.FXUtils;
import com.rspsi.util.FXDialogs;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;

import com.google.common.io.Files;
import com.rspsi.controllers.LauncherController;
import com.rspsi.controls.WindowControls;
import com.rspsi.options.Config;
import com.rspsi.resources.ResourceLoader;
import com.rspsi.util.ChangeListenerUtil;
import com.rspsi.util.RetentionFileChooser;
import com.rspsi.util.Settings;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import com.rspsi.ui.workspace.StudioDashboard;

@Slf4j
@Getter
public class LauncherWindow extends Application {

	@Getter
	private static LauncherWindow singleton;

	private Stage primaryStage;
	
	private LauncherController controller;
	private List<String> oldCachePaths;
	private Parent legacySettingsContent;
	private StudioDashboard dashboard;

	@Override
	public void start(Stage primaryStage) throws Exception {
		Settings.loadSettings();
		startDashboard(primaryStage);
	}

	private void startDashboard(Stage primaryStage) throws Exception {
		java.nio.file.Files.createDirectories(Paths.get(System.getProperty("user.home"), ".rspsi"));
		singleton = this;
		this.primaryStage = primaryStage;
		String savedCache = Settings.getSetting("cacheLocation", "");
		if (savedCache == null || !new File(savedCache).isDirectory()) {
			savedCache = Settings.getSetting("lastCacheLocation", "");
		}
		if (savedCache == null || !new File(savedCache).isDirectory()) savedCache = "";
		oldCachePaths = Settings.getSetting("oldCache", Lists.newArrayList());

		dashboard = new StudioDashboard(
				(thisCache, region) -> launchEditor(thisCache, region),
				this::showLegacySettings,
				this::showStudioSettings,
				this::chooseCache);
		dashboard.setCachePath(savedCache);
		Scene scene = new Scene(dashboard, 1320, 860);
		primaryStage.setTitle("OpenRune Content Studio");
		primaryStage.initStyle(StageStyle.DECORATED);
		primaryStage.setScene(scene);
		primaryStage.getIcons().add(ResourceLoader.getSingleton().getLogo64());
		primaryStage.setMinWidth(980);
		primaryStage.setMinHeight(680);
		primaryStage.show();
		FXUtils.centerStage(primaryStage);
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
		if (dashboard != null) dashboard.setCachePath(path);
		if (controller != null) controller.getCacheLocation().getEditor().setText(path);
		rememberCache(path);
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
		if (dashboard != null) dashboard.setCachePath(path);
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
		boolean cacheAvailable = cachePath != null && !cachePath.isBlank()
				&& new File(cachePath).isDirectory();
		String normalized = cacheAvailable
				? new File(cachePath).getAbsolutePath() + File.separator : "";
		Config.cacheLocation.set(normalized);
		if (cacheAvailable) {
			rememberCache(normalized);
		}
		MainWindow window = new MainWindow();
		// A debug region only has meaning with a cache. Without one, preserve the
		// empty Map Editor state instead of sending an unresolvable request into
		// the legacy client loop.
		window.setStartupRegion(cacheAvailable ? normalizeRegion(region) : "");
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
		movePlugin(pluginName, "active", "inactive");
	}

	private void enableSelectedPlugin() {
		String pluginName = controller.getDisabledPlugins().getFocusModel().getFocusedItem();
		if (pluginName == null) return;
		File activeFolder = new File(PLUGINS_PATH + "active");
		File inactiveFolder = new File(PLUGINS_PATH + "inactive");
		activeFolder.mkdirs();
		inactiveFolder.mkdirs();
		File[] active = activeFolder.listFiles((dir, name) -> name.endsWith(".jar"));
		if (active != null) for (File file : active) movePlugin(file.getName(), "active", "inactive");
		movePlugin(pluginName, "inactive", "active");
	}

	private void movePlugin(String pluginName, String from, String to) {
		File source = new File(PLUGINS_PATH + from + File.separator + pluginName + (pluginName.endsWith(".jar") ? "" : ".jar"));
		File target = new File(PLUGINS_PATH + to + File.separator + source.getName());
		target.getParentFile().mkdirs();
		try {
			Files.move(source, target);
			populatePlugins();
		} catch (IOException exception) {
			log.warn("Could not move plugin {}", source, exception);
		}
	}

	private void startLegacy(Stage primaryStage) throws Exception {

		java.nio.file.Files.createDirectories(Paths.get(System.getProperty("user.home"), ".rspsi"));
		File logFile = new File(Paths.get(System.getProperty("user.home"), ".rspsi").toFile(), "log.txt");

			System.setOut(new PrintStream(logFile));

		singleton = this;
		this.primaryStage = primaryStage;
		FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/loadscreen.fxml"));
		controller = new LauncherController();
		loader.setController(controller);
		Parent content = loader.load();
		Scene scene = new Scene(content);

		scene.setFill(Color.TRANSPARENT);
		
		primaryStage.setTitle("RSPSi Map Editor Launcher");
		primaryStage.initStyle(StageStyle.TRANSPARENT);
		primaryStage.setScene(scene);
		primaryStage.getIcons().add(ResourceLoader.getSingleton().getLogo64());

		primaryStage.show();
		primaryStage.sizeToScene();
		FXUtils.centerStage(primaryStage);
		primaryStage.centerOnScreen();
		
		Settings.loadSettings();
		
		String cacheLoc = Settings.getSetting("cacheLocation", Config.cacheLocation.get());
	
		oldCachePaths = Settings.getSetting("oldCache", Lists.newArrayList());
		fillOldPaths();
		
		controller.getCacheLocation().getEditor().setText(new File(cacheLoc).getAbsolutePath() + File.separator);
		
		ChangeListenerUtil.addListener(() -> {
			primaryStage.sizeToScene();
		}, controller.getPluginTitlePane().expandedProperty());
		
		
		controller.getDisablePluginButton().setOnAction(evt -> {
			String pluginName = controller.getEnabledPlugins().getFocusModel().getFocusedItem();
			if(pluginName != null) {
				File oldPluginFile = new File(PLUGINS_PATH + "active" + File.separator + pluginName + ".jar");
				File newPluginFile = new File(PLUGINS_PATH + "inactive" + File.separator + pluginName + ".jar");
				try {
					Files.copy(oldPluginFile, newPluginFile);
					oldPluginFile.delete();
					populatePlugins();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		
		controller.getCancelButton().setOnAction(evt -> primaryStage.hide());
		
		controller.getEnablePluginButton().setOnAction(evt -> {
			String pluginName = controller.getDisabledPlugins().getFocusModel().getFocusedItem();
			if(pluginName != null) {

				File inactiveFolder = new File(PLUGINS_PATH + "inactive" + File.separator);
				File activeFolder = new File(PLUGINS_PATH + "active" + File.separator);
				
				File oldPluginFile = new File(inactiveFolder,  pluginName + ".jar");
				File newPluginFile = new File(activeFolder, pluginName + ".jar");
				try {
					if(!activeFolder.exists()) {
						activeFolder.mkdirs();
					}
					if(newPluginFile.exists()){
						newPluginFile.delete();
					}
					for(File active : activeFolder.listFiles()){
						Files.move(active, new File(inactiveFolder, active.getName()));
					}
					inactiveFolder.mkdirs();
					activeFolder.mkdirs();
					Files.copy(oldPluginFile, newPluginFile);
					oldPluginFile.delete();
					populatePlugins();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		
		controller.getBrowseButton().setOnAction(evt -> {
			File f = RetentionFileChooser.showOpenFolderDialog(primaryStage, null);
			if(f != null) {
				String oldPath = controller.getCacheLocation().getEditor().getText();
				String newPath = f.getAbsolutePath() + File.separator;

				putOldPath(oldPath);
				putOldPath(newPath);
				
				controller.getCacheLocation().getEditor().setText(newPath);
				
			}
		});
		
		controller.getLaunchButton().setOnAction(evt -> {
			Config.cacheLocation.set(controller.getCacheLocation().getEditor().getText());
			Settings.properties.put("cacheLocation", Config.cacheLocation.get());
			Settings.properties.put("lastCacheLocation", cacheLoc);
			primaryStage.hide();
			MainWindow window = new MainWindow();
			Stage otherStage = new Stage();
			otherStage.setX(primaryStage.getX());
			otherStage.setY(primaryStage.getY());
			window.start(otherStage);
		});

		
		populatePlugins();
		WindowControls controls = WindowControls.addWindowControlsFixed(primaryStage, controller.getTopBar(), controller.getControlBox());
		primaryStage.sizeToScene();

	}
	
	private void putOldPath(String path) {
		if(!oldCachePaths.contains(path)) {
			oldCachePaths.add(0, path);
			Settings.putSetting("oldCache", oldCachePaths);
			fillOldPaths();
		}
	}
	
	private void fillOldPaths() {
		controller.getCacheLocation().getItems().clear();
		controller.getCacheLocation().getItems().addAll(oldCachePaths);
	}
	
	public void populatePlugins() {
		controller.getEnabledPlugins().getItems().clear();
		controller.getDisabledPlugins().getItems().clear();
		
		controller.getEnabledPlugins().getItems().addAll(getPlugins("active"));
		controller.getDisabledPlugins().getItems().addAll(getPlugins("inactive"));
	}
	
	private static final String PLUGINS_PATH = "plugins" + File.separator;
	
	private static List<String> getPlugins(String folderName){
		List<String> list = Lists.newArrayList();
		File folder = new File(PLUGINS_PATH + folderName);
		if(folder.exists()) {
			for(File f : folder.listFiles()) {
				list.add(f.getName().replaceAll(".jar", "").trim());
			}
		}
		list.sort(Comparator.naturalOrder());
		return list;
	}
	
	public static void main(String[] args) {
		launch(args);
	}

}
