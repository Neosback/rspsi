package com.rspsi.ui;

import com.rspsi.controls.ConvertLandscapeTool;
import com.rspsi.dialogs.RenderDistanceDialog;
import com.rspsi.options.KeyboardState;
import com.rspsi.util.*;
import javafx.beans.value.ChangeListener;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.displee.util.GZIPUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.major.map.RenderFlags;

import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.jagex.Client;
import com.jagex.cache.def.Floor;
import com.jagex.cache.def.ObjectDefinition;
import com.jagex.cache.loader.floor.FloorDefinitionLoader;
import com.jagex.cache.loader.object.ObjectDefinitionLoader;
import com.jagex.cache.loader.textures.TextureLoader;
import com.jagex.chunk.Chunk;
import com.jagex.draw.textures.Texture;
import com.jagex.entity.model.Mesh;
import com.jagex.entity.model.MeshLoader;
import com.jagex.map.SceneGraph;
import com.jagex.map.object.DefaultWorldObject;
import com.jagex.util.BitFlag;
import com.jagex.util.ColourUtils;
import com.jagex.util.MultiMapEncoder;
import com.jagex.util.ObjectKey;
import com.rspsi.controllers.MainController;
import com.rspsi.controls.RemappingTool;
import com.rspsi.controls.SwatchControl;
import com.rspsi.datasets.ObjectDataset;
import com.rspsi.dialogs.TileCopyDialog;
import com.rspsi.dialogs.TileDeleteDialog;
import com.rspsi.dialogs.TileExportDialog;
import com.rspsi.game.CanvasPane;
import com.rspsi.game.listeners.GameKeyListener;
import com.rspsi.game.listeners.GameMouseListener;
import com.rspsi.game.map.MapView;
import com.rspsi.game.save.AutoSaveJob;
import com.rspsi.game.save.TileChange;
import com.rspsi.core.misc.StatusUpdate;
import com.rspsi.core.misc.ToolType;
import com.rspsi.options.Config;
import com.rspsi.options.Options;
import com.rspsi.plugins.ui.ApplicationPluginLoader;
import com.rspsi.resources.ResourceLoader;
import com.rspsi.swatches.BaseSwatch;
import com.rspsi.swatches.OverlaySwatch;
import com.rspsi.swatches.UnderlaySwatch;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.DeleteObjectCommand;
import com.rspsi.editor.PasteFragmentCommand;
import com.rspsi.editor.io.SessionAutosaveCoordinator;
import com.rspsi.editor.io.SessionAutosaveStore;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.ObjectSetSelection;
import com.rspsi.editor.selection.Selection;
import com.rspsi.cache.workspace.OsrsStudioProject;
import com.rspsi.cache.definition.LegacyDefinitionProvider;
import com.rspsi.editor.model.WorldWindow;
import com.rspsi.legacy.LegacyMapDocumentBridge;
import com.rspsi.project.ProjectLayout;
import com.rspsi.project.ProjectMetadata;
import com.rspsi.ui.workspace.ControlledWorkspaceBridge;
import com.rspsi.ui.workspace.ControlledWorkspaceShell;
import com.rspsi.ui.workspace.ControlledViewportPanel;
import com.rspsi.ui.workspace.SessionHistoryPanel;
import com.rspsi.ui.workspace.SessionInspectorPanel;
import com.rspsi.ui.workspace.ValidationPanel;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class MainWindow extends Application {

	private static MainWindow singleton;

	static {

		//Faster tooltips
		try {
			Tooltip obj = new Tooltip();
			Class<?> clazz = obj.getClass().getDeclaredClasses()[0];
			Constructor<?> constructor = clazz.getDeclaredConstructor(Duration.class, Duration.class, Duration.class,
					boolean.class);
			constructor.setAccessible(true);
			Object tooltipBehavior = constructor.newInstance(new Duration(50), // open
					new Duration(5000), // visible
					new Duration(200), // close
					false);
			Field fieldBehavior = obj.getClass().getDeclaredField("BEHAVIOR");
			fieldBehavior.setAccessible(true);
			fieldBehavior.set(obj, tooltipBehavior);
		} catch (Exception e) {
			// Logger.error(e);
		}
	}


	private Client clientInstance;

	private ControlledWorkspaceShell controlledWorkspaceShell;
	private EditorSession controlledSession;
	private LegacyMapDocumentBridge controlledDocumentBridge;
	private OsrsStudioProject osrsStudioProject;
	private SessionAutosaveCoordinator osrsAutosave;
	private ScheduledFuture<?> osrsAutosaveTask;
	private ProjectLayout osrsProjectLayout;
	private boolean osrsProjectActive;
	private final Runnable controlledMapReadyListener = this::bindControlledWorkspaceSession;

	private Scene scene;

	private Stage stage;

	private MainController controller;

	@Setter
	public SwatchControl objectSwatch, overlaySwatch, underlaySwatch;

	private ObjectPreviewWindow objectPreviewWindow;
	private PickCoordinatesWindow pickCoords;
	private PickHashWindow pickHash;
	private MultiRegionMapWindow fullMapView;
	private SelectFilesWindow selectFiles;
	private SelectPackWindow selectPack;
	private RemappingTool remappingTool;
	private ConvertLandscapeTool convertLandscapeTool;

	private Mesh errorMesh;

	public void fillSwatches() {

		for (int idx = 0; idx < FloorDefinitionLoader.getUnderlayCount(); idx++) {
			Floor floor = FloorDefinitionLoader.getUnderlay(idx);
			if(floor == null)
				continue;
			Group g = new Group();
			String label = "";
			label = "[" + idx + "] rgb(" + ColourUtils.getRed(floor.getRgb()) + "," + ColourUtils.getGreen(floor.getRgb()) + ","
					+ ColourUtils.getBlue(floor.getRgb()) + ")";
			Rectangle rect = new Rectangle();
			rect.setWidth(32);
			rect.setHeight(32);
			Color c = ColourUtils.getColor(floor.getRgb());
			// c = c.deriveColor(floor.getWeightedHue(), floor.getSaturation() / 256.0,
			// floor.getLuminance() / 256.0, 1.0);
			rect.setFill(c);
			rect.setStroke(Color.BLACK);
			rect.setStrokeWidth(1);
			g.getChildren().add(rect);

			BaseSwatch data = new UnderlaySwatch(g, label, idx);
			underlaySwatch.addSwatch(data);
		}
		for (int idx = 0; idx < FloorDefinitionLoader.getOverlayCount(); idx++) {
			Floor floor = FloorDefinitionLoader.getOverlay(idx);

			if(floor == null)
				continue;
			Group g = new Group();
			String label = "";
			if (floor.getTexture() == -1 || floor.getTexture() > TextureLoader.instance.count()) {
				continue;
			} else {
				label = "[" + idx + "] texture(" + floor.getTexture() + ")";
				Texture texture = TextureLoader.getTexture(floor.getTexture());
				if(texture == null)
					continue;
				ImageView imgView = new ImageView(texture.getAsFXImage());
				imgView.setPreserveRatio(true);
				imgView.setSmooth(true);
				imgView.setFitHeight(32);
				imgView.setFitWidth(32);
				g.getChildren().add(imgView);
			}
			BaseSwatch data = new OverlaySwatch(g, label, idx);
			overlaySwatch.addSwatch(data);
		}
		for (int idx = 0; idx < FloorDefinitionLoader.getOverlayCount(); idx++) {

			Floor floor = FloorDefinitionLoader.getOverlay(idx);

			if(floor == null)
				continue;
			Group g = new Group();
			String label = "";
			if (floor.getTexture() == -1 || floor.getTexture() >= TextureLoader.instance.count()) {
				label = "[" + idx + "] rgb(" + ColourUtils.getRed(floor.getRgb()) + "," + ColourUtils.getGreen(floor.getRgb()) + "," + ColourUtils.getBlue(floor.getRgb()) + ")";
				Rectangle rect = new Rectangle();
				rect.setWidth(32);
				rect.setHeight(32);
				Color c = ColourUtils.getColor(floor.getRgb());
				rect.setFill(c);
				rect.setStroke(Color.BLACK);
				rect.setStrokeWidth(1);
				g.getChildren().add(rect);
			} else {
				continue;
			}
			BaseSwatch data = new OverlaySwatch(g, label, idx);
			overlaySwatch.addSwatch(data);
		}

	}

	@Override
	public void start(Stage primaryStage) {
		try {
			singleton = this;
			stage = primaryStage;
			Platform.setImplicitExit(true);
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_test4.fxml"));
			controller = new MainController();
			loader.setController(controller);
			Parent content = loader.load();
			if (Settings.getSetting("controlledWorkspace", false)) {
				controlledWorkspaceShell = (ControlledWorkspaceShell)
						ControlledWorkspaceBridge.adapt(content, controller);
				content = controlledWorkspaceShell;
				log.info("Controlled workspace enabled; legacy renderer remains embedded as the viewport");
			}
			double windowWidth = (Double) Settings.properties.getOrDefault("window_width",1240.0);
			double windowHeight = (Double) Settings.properties.getOrDefault("window_height",800.0);
			scene = new Scene(content,windowWidth,windowHeight);

			scene.setFill(Color.TRANSPARENT);

			primaryStage.setTitle("RSPSi Map Editor 1.16.1");
			primaryStage.initStyle(StageStyle.TRANSPARENT);
			primaryStage.setScene(scene);
			primaryStage.getIcons().addAll(ResourceLoader.getSingleton().getIcons());

			ChangeListener<Number> stageSizeListener = (observable, oldValue, newValue) -> {
				if((boolean) Settings.properties.getOrDefault("remember_size",true) == true) {
					Settings.properties.put("window_width", primaryStage.getWidth());
					Settings.properties.put("window_height", primaryStage.getHeight());
					Settings.saveSettings();
				}
			};

			ChangeListener<Number> stageLocationListener = (observable, oldValue, newValue) -> {
				if((boolean) Settings.properties.getOrDefault("remember_location",false) == true) {
					Settings.properties.put("windowLocationWidth", primaryStage.getX());
					Settings.properties.put("windowLocationHeight", primaryStage.getY());
					Settings.saveSettings();
				}
			};

			double windowLocationWidth = (Double) Settings.properties.getOrDefault("windowLocationWidth",0.0);
			double windowLocationHeight = (Double) Settings.properties.getOrDefault("windowLocationHeight",0.0);

			if(windowLocationWidth == 0.0 && windowLocationHeight == 0.0) {
				primaryStage.centerOnScreen();
			} else {
				int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
				int screenHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
				if (windowLocationWidth <= screenWidth && windowLocationHeight <= screenHeight) {
					primaryStage.setX(windowLocationWidth);
					primaryStage.setY(windowLocationHeight);
				} else {
					primaryStage.centerOnScreen();
				}
			}

			primaryStage.widthProperty().addListener(stageSizeListener);
			primaryStage.heightProperty().addListener(stageSizeListener);
			primaryStage.xProperty().addListener(stageLocationListener);
			primaryStage.yProperty().addListener(stageLocationListener);

			primaryStage.show();

			FXUtils.centerStage(primaryStage);

			controller.onLoad(this);

			boolean shutdownCorrectly = Settings.getSetting("shutdown", false);
			Settings.clearSetting("shutdown");

			int renderDistance = Settings.getSetting("renderDistance", Options.renderDistance.get());

			Options.renderDistance.set(renderDistance);

			boolean loadAutosave = false;
			File autosavePath = Paths.get(System.getProperty("user.home"), ".rspsi", "autosave").toFile();

			String lastCacheLoc = Settings.getSetting("lastCacheLocation", "");
			if(!shutdownCorrectly) {
				System.out.println("CRASH DETECTED!");

				if(autosavePath.exists() && autosavePath.list().length > 0) {

					//Just incase there was a crash mid autosave
					File packFile = new File(autosavePath, "autosave.pack");

					File objectFileBackup =  new File(autosavePath, "autosave.pack.bk");

					//restore backups
					if(objectFileBackup.exists()) {
						Files.copy(objectFileBackup.toPath(), packFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
					}


					String response = FXDialogs.showConfirm(primaryStage,"Application did not shut down correctly!",
							"We have detected that your last shutdown did not complete correctly.\nWould you like to load the last autosave?",
							"Yes", "No");
					if(response.equalsIgnoreCase("yes")) {
						loadAutosave = true;
					}
				}
			}

			MapView mapView = new MapView();
			
			TileExportDialog export = new TileExportDialog();
			export.start(new Stage());

			TileDeleteDialog deleteWindow = new TileDeleteDialog();
			deleteWindow.start(new Stage());

			TileCopyDialog copyWindow = new TileCopyDialog();
			copyWindow.start(new Stage());

			pickCoords = new PickCoordinatesWindow();
			pickCoords.start(new Stage());

			pickHash = new PickHashWindow();
			pickHash.start(new Stage());

			selectFiles = new SelectFilesWindow();
			selectFiles.start(new Stage());

			selectPack = new SelectPackWindow();
			selectPack.start(new Stage());

			ContactMeWindow contactMe = new ContactMeWindow();
			contactMe.start(new Stage());

			controller.getContactMeBtn().setOnAction(evt -> {
				contactMe.showAndWait();
			});

			controller.getShowMapIndexEditor().setOnAction(evt -> {
				mapView.setVisible(true);
				mapView.initTiles();
			});



			ChangeListenerUtil.addRangeListener(Options.rotation, 0, 3, true);

			ChangeListenerUtil.addListener(() -> {
				clientInstance.getCurrentChunk().mapRegion.updateTiles();
				Client.updateChunkTiles();
				SceneGraph.minimapUpdate = true;
			}, Options.disableBlending, Options.showOverlay, Options.showObjects);

			controller.getReloadSwatchesBtn().setOnAction(evt -> {
				overlaySwatch.clear();
				underlaySwatch.clear();
				fillSwatches();
			});
			controller.getReloadModelsBtn().setOnAction(evt -> MeshLoader.getSingleton().clearAll());
			controller.getCopySelectedTilesBtn().setOnAction(evt -> {
				if(Options.currentTool.get() == ToolType.SELECT_OBJECT) {
					SceneGraph.onCycleEnd.add(() -> {
						Client.getSingleton().sceneGraph.copyObjects();
					});

					controller.getPasteTilesBtn().setDisable(false);
				} else {
					copyWindow.show();
					controller.getPasteTilesBtn().setDisable(false);
				}
			});

			controller.getPasteTilesBtn().setOnAction(evt -> {

				SceneGraph scene = clientInstance.sceneGraph;
				scene.resetTiles();
				Options.currentTool.set(ToolType.IMPORT_SELECTION);

			});

			SceneGraph.undoList.addListener((ListChangeListener<TileChange>) listener -> {
				updateHistoryMenuState();
			});

			SceneGraph.redoList.addListener((ListChangeListener<TileChange>) listener -> {
				updateHistoryMenuState();
			});

			controller.getUndoMenuItem().setOnAction(evt -> handleUndo());
			controller.getRedoMenuItem().setOnAction(evt -> handleRedo());
			updateHistoryMenuState();

			controller.getDeleteSelectedTilesBtn().setOnAction(evt -> TileDeleteDialog.instance.show());

			RenderDistanceDialog renderDistanceDialog = new RenderDistanceDialog();
			renderDistanceDialog.start(new Stage());
			controller.getChangeViewDist().setOnAction(evt -> {
				renderDistanceDialog.show();
			});

			controller.getAddObjectToSwatchBtn().setOnAction(evt ->{
				
				if(!clientInstance.sceneGraph.selectedObjects.isEmpty()) {
					for(DefaultWorldObject selectedObject : clientInstance.sceneGraph.selectedObjects) {
						ObjectKey key = selectedObject.getKey();
						int id = key.getId();
						int type = key.getType();
						ObjectDefinition def = ObjectDefinitionLoader.lookup(id);

						ObjectDataset set = new ObjectDataset(id, type, def.getName());
						ObjectPreviewWindow.instance.loadToSwatches(set);
					}
				}
			});
			objectPreviewWindow = new ObjectPreviewWindow(objectSwatch);
			objectPreviewWindow.start(new Stage());
			
			//ModelPreviewWindow modelPrev = new ModelPreviewWindow();
			//modelPrev.start(new Stage());

			fullMapView = new MultiRegionMapWindow();
			fullMapView.start(new Stage());
			
			
			remappingTool = new RemappingTool();
			remappingTool.start(new Stage());

			controller.getShowRemapperBtn().setOnAction(evt -> {
				remappingTool.show();
				if(remappingTool.valid()) {
					remappingTool.doRemap();
				}
			});


			convertLandscapeTool = new ConvertLandscapeTool();
			convertLandscapeTool.start(new Stage());


			controller.getConvertLandscapeBtn().setOnAction(evt -> {
				convertLandscapeTool.show();
				if(convertLandscapeTool.valid()) {
					convertLandscapeTool.doRencode();
				}
			});

			controller.getShowFullMap().setOnAction(evt -> { 
				fullMapView.show();
				
				SceneGraph.minimapUpdate = true;
			});

			controller.getExportTilesBtn().setOnAction(evt -> export.show());

			controller.getShowObjectViewBtn().setOnAction(evt -> objectPreviewWindow.stage.show());


			clientInstance = Client.initialize(controller.getGamePane().widthProperty().intValue(),
					controller.getGamePane().heightProperty().intValue());
			if (controlledWorkspaceShell != null) {
				clientInstance.addMapReadyListener(controlledMapReadyListener);
			}

			clientInstance.loadCache(Paths.get(Config.cacheLocation.get()));

			CanvasPane gamePane = new CanvasPane(clientInstance.getGameCanvas());

			GameKeyListener gameKeyListener = new GameKeyListener(clientInstance);
			clientInstance.getGameCanvas().addEventHandler(MouseEvent.ANY, new GameMouseListener(clientInstance));
			clientInstance.getGameCanvas().addEventHandler(ScrollEvent.ANY, new GameMouseListener(clientInstance));
			clientInstance.getGameCanvas().addEventHandler(KeyEvent.ANY, gameKeyListener);

			clientInstance.fullMapVisible.bind(fullMapView.visibleProperty());

			primaryStage.addEventHandler(KeyEvent.ANY, gameKeyListener);
			primaryStage.focusedProperty().addListener((observable, oldValue, newValue) -> {
				if(!newValue){
					log.info("Lost focus!");
					KeyboardState.reset();
					SceneGraph.setMouseIsDown(false);
					Arrays.fill(clientInstance.keyStatuses, 0);
					//clientInstance.visible = false;
				} else {
					log.info("Gained focus!");
					//clientInstance.visible = true;
				}
			});


			SceneGraph.setMouseIsDown(true);
			SceneGraph.setMouseIsDown(false);

			clientInstance.visible = true;

			controller.getGamePane().getChildren().add(0, gamePane);
			controller.getMapPane().getChildren().add(new CanvasPane(clientInstance.mapCanvas));
			clientInstance.errorDisplayed.addListener((observable, oldValue, newValue) -> controller.getReturnToLauncher().setVisible(newValue.booleanValue()));
			controller.getReturnToLauncher().setOnAction(evt -> {
				LauncherWindow.getSingleton().getPrimaryStage().show();
				LauncherWindow.getSingleton().populatePlugins();
				singleton = null;
				if (clientInstance != null) {
					try {
						clientInstance.exit();
					} catch(Exception ex) {
						ex.printStackTrace();

					}
				}
				primaryStage.close();
			});
			ContextMenu menu = new ContextMenu();
			menu.autoHideProperty().set(true);
			MenuItem item = new MenuItem("Save to file");
			item.setOnAction(evt -> {
				File f = RetentionFileChooser.showSaveDialog(FilterMode.PNG);
				if(f != null) {
					try {
						System.out.println(f.getAbsolutePath());
						clientInstance.saveMinimapImage(f);
					} catch (Exception e) {
						e.printStackTrace();
						FXDialogs.showError(primaryStage, "Error while loading saving image", "There was a failure while attempting to save\nthe minimap to the selected file.");

					}
				}
			});
			menu.getItems().addAll(item);
			controller.getMapPane().setOnContextMenuRequested(evt -> {
				menu.show(getStage(), evt.getScreenX(), evt.getScreenY());
			});

			controller.getFixHeightsBtn().setOnAction(evt -> {
				String result = FXDialogs.showConfirm(primaryStage, "Are you sure?", "This fix will set all heights on plane 1 and above based on "
						+ "the tile height at z = 0. This may cause a few issues for some tiles you will have to fix yourself. \n\nWould you like to continue?", 
						"Yes", "No");
				if(result.equalsIgnoreCase("Yes")) {
					for(int plane = 1;plane<4;plane++) {
						for(int absX = 0;absX<clientInstance.sceneGraph.width;absX++) {
							for(int absY = 0;absY<clientInstance.sceneGraph.length;absY++) {
								clientInstance.mapRegion.tileHeights[plane][absX][absY] = clientInstance.mapRegion.tileHeights[plane - 1][absX][absY] - 240;
							}
						}
					}
					//chunk.mapRegion.tileHeights = newHeights;


					clientInstance.sceneGraph.updateHeights(0, 0, clientInstance.sceneGraph.width, clientInstance.sceneGraph.length);
				}
			});
			controller.getForceMapUpdateBtn().setOnAction(evt -> {
				int positionX = clientInstance.xCameraPos;
				int positionY = clientInstance.yCameraPos;
				
				byte[] packData = MultiMapEncoder.encode(Lists.newArrayList(clientInstance.chunks));
				Client.runLater.add(() ->{
					clientInstance.loadChunks(MultiMapEncoder.decode(packData));
					fullMapView.resizeMap();
					clientInstance.xCameraPos = positionX;
					clientInstance.yCameraPos = positionY;
				});
			});
			primaryStage.setOnHiding((we) -> {

				Settings.putSetting("shutdown", true);
				if (clientInstance != null) {
					clientInstance.removeMapReadyListener(controlledMapReadyListener);
					try {
						clientInstance.exit();
					} catch(Exception ex) {
						ex.printStackTrace();

					}
				}
				if (controlledDocumentBridge != null) {
					controlledDocumentBridge.close();
					controlledDocumentBridge = null;
				}
				closeOsrsAutosave();
				if (osrsStudioProject != null) {
					osrsStudioProject.close();
					osrsStudioProject = null;
				}
				controlledSession = null;
				osrsProjectActive = false;
				if (controlledWorkspaceShell != null) {
					if (controlledWorkspaceShell.panelNode("viewport") instanceof ControlledViewportPanel viewport) {
						viewport.close();
					}
					if (controlledWorkspaceShell.panelNode("history") instanceof SessionHistoryPanel history) {
						history.close();
					}
					if (controlledWorkspaceShell.panelNode("inspector") instanceof SessionInspectorPanel inspector) {
						inspector.close();
					}
					if (controlledWorkspaceShell.panelNode("validation") instanceof ValidationPanel validation) {
						validation.close();
					}
					if (controlledWorkspaceShell.statusBar() instanceof com.rspsi.ui.workspace.WorkspaceStatusBar status) {
						status.close();
					}
				}
				if(singleton != null) {
					Platform.exit();
					System.exit(0);
				}
			});

			ChangeListenerUtil.addListener((oldVal, newVal) -> {
				if(Options.currentTool.get() == ToolType.SELECT_OBJECT){
					clientInstance.sceneGraph.rotateSelectedObjects(oldVal - newVal);
				}
				SceneGraph.onCycleEnd.add(() -> Client.getSingleton().sceneGraph.forceMouseInTile());
			}, Options.rotation);

			controller.getCopyTileFlags().setOnAction(evt -> {
				BitFlag flag = clientInstance.sceneGraph.getSelectedFlag();

				controller.getUnwalkableCheck().setSelected(flag.flagged(RenderFlags.BLOCKED_TILE));
				controller.getBridgeCheck().setSelected(flag.flagged(RenderFlags.BRIDGE_TILE));
				controller.getForceLowestCheck().setSelected(flag.flagged(RenderFlags.FORCE_LOWEST_PLANE));
				controller.getDrawOnLowerZCheck().setSelected(flag.flagged(RenderFlags.RENDER_ON_LOWER_Z));
				controller.getDisableRenderCheck().setSelected(flag.flagged(RenderFlags.DISABLE_RENDERING));

			});

			controller.getCopyTileHeights().setOnAction(evt -> {
				int height = clientInstance.sceneGraph.getSelectedHeight();
				System.out.println(height);
				if(height <= 0) {
					controller.getHeightLevelSlider().setValue(-height);
				} else {
					FXDialogs.showError(primaryStage,"Error while loading tile height", "There was a failure while attempting to grab\ntile height from the selected tile.");
				}
			});

			controller.getGetOverlayFromTile().setOnAction(evt -> {
				if(clientInstance.sceneGraph != null) {
					int overlayId = clientInstance.sceneGraph.getSelectedOverlay();
					int overlayShape = clientInstance.sceneGraph.getSelectedOverlayShape();
					log.info("id {} shape {}", overlayId, overlayShape);
					if(overlayId > 0) {

						overlaySwatch.setOverlayShape(overlayShape + 1);
						overlaySwatch.selectByOverlay(overlayId - 1);
					}
				}
			});

			controller.getGetUnderlayFromTile().setOnAction(evt -> {
				if(clientInstance.sceneGraph != null) {
					int underlayId = clientInstance.sceneGraph.getSelectedUnderlay();
					if(underlayId > 0) {
						underlaySwatch.selectByUnderlay(underlayId - 1);
					}
				}
			});



			controller.getImportTilesBtn().setOnAction(evt -> {
				File f = RetentionFileChooser.showOpenDialog(primaryStage, FilterMode.JMAP);
				if (f != null) {
					try {
						clientInstance.sceneGraph.importSelection(f);
					} catch (IOException e) {
						e.printStackTrace();
						FXDialogs.showError(primaryStage,"Error while loading prefab!",
								"There was an error while reading the selected file.");
					} catch (Exception e) {
						e.printStackTrace();
						FXDialogs.showError(primaryStage,"Error while parsing prefab!",
								"There was an error while parsing the selected file.");
					}
				}
			});



			ChangeListenerUtil.addListener(() -> {
				SceneGraph.onCycleEnd.add(() -> {
					Client.updateChunkTiles();
					SceneGraph.minimapUpdate = true;
				});
			}, Options.showHiddenTiles);

			ChangeListenerUtil.addListener(() -> {
				SceneGraph.onCycleEnd.add(() -> {
					Client.updateChunkTiles();
					Client.getSingleton().sceneGraph.resetTiles();
					SceneGraph.minimapUpdate = true;
				});
			}, Options.currentHeight);



			ApplicationPluginLoader.loadPlugins(this);
			ChangeListenerUtil.addListener(() -> {
				if(Client.gameLoaded.get()) {
					underlaySwatch.clear();
					overlaySwatch.clear();
					fillSwatches();
				}
			}, Options.hdTextures);
			
			this.setupOpenOptions();
			this.setupSaveOptions();

			final boolean reloadSaved = loadAutosave;

			ChangeListenerUtil.addListener(true, () -> {
				fillSwatches();

				try {
					byte[] modelData = ByteStreams.toByteArray(getClass().getResourceAsStream("/misc/mapfunction.dat"));

					MeshLoader.getSingleton().load(modelData, 111);
					
					
				} catch(Exception ex) {
					ex.printStackTrace();
				}
				

				if(reloadSaved) {
					File landscapeFile = new File(autosavePath, "autosave.pack");
					if(landscapeFile.exists()) {
						try {
							byte[] landscapeData = Files.readAllBytes(landscapeFile.toPath());

							final byte[] fLandscape = landscapeData;
							Client.runLater.add(() -> {
								clientInstance.loadChunks(MultiMapEncoder.decode(fLandscape));
								fullMapView.resizeMap();
							});//TODO
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							FXDialogs.showError(primaryStage,"Error while loading map!", "There was an error while loading or parsing the autosave data.");
						}
					}
				}
				Platform.runLater(() -> {
					objectPreviewWindow.fillList();
				});
				try {
					setupAutoSave();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}, Client.gameLoaded);

			EventBus.getDefault().register(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
		primaryStage.sizeToScene();
	}
	
	private static ScheduledExecutorService service = Executors.newScheduledThreadPool(4);
	private void setupAutoSave(){

		int autosaveSeconds = Settings.getSetting("autosaveSeconds", 60);
		Settings.putSetting("autosaveSeconds", autosaveSeconds);

		service.scheduleAtFixedRate(() -> AutoSaveJob.execute(clientInstance), 5, 5, TimeUnit.MINUTES);
	}

	/**
	 * Imports the loaded legacy scene into the neutral session only when the
	 * controlled workspace is enabled. The existing renderer remains the
	 * compatibility viewport while the bridge synchronizes scene objects.
	 */
	private void bindControlledWorkspaceSession() {
		if (osrsProjectActive) {
			return;
		}
		if (controlledWorkspaceShell == null || clientInstance == null
				|| clientInstance.mapRegion == null || clientInstance.sceneGraph == null) {
			return;
		}
		Platform.runLater(() -> {
			if (controlledWorkspaceShell == null || clientInstance == null
				|| clientInstance.mapRegion == null || clientInstance.sceneGraph == null) {
				return;
			}
			if (controlledDocumentBridge != null) {
				controlledDocumentBridge.close();
			}
			var document = LegacyMapDocumentBridge.importDocument(clientInstance.mapRegion,
					clientInstance.sceneGraph);
			controlledSession = new EditorSession(document);
			controlledSession.addStateListener(session -> updateHistoryMenuState());
			controlledDocumentBridge = new LegacyMapDocumentBridge(
					clientInstance.mapRegion, clientInstance.sceneGraph);
			controlledDocumentBridge.attach(controlledSession);

			ControlledWorkspaceBridge.bindSession(controlledWorkspaceShell, controlledSession,
					new WorldWindow(clientInstance.getBaseX(), clientInstance.getBaseY(),
							document.width(), document.length()), new LegacyDefinitionProvider());
			log.info("Controlled workspace session bound to legacy map {}x{} at {},{}",
					document.width(), document.length(), clientInstance.getBaseX(), clientInstance.getBaseY());
		});
	}

	/**
	 * Opens the explicit OSRS project workflow without replacing the legacy
	 * launch path. The source cache is always opened read-only. An explicitly
	 * selected prepared output cache enables editing, staged autosave, and the
	 * same neutral command/session workflow used by non-UI tests.
	 */
	private void openOsrsProject() {
		if (controlledWorkspaceShell == null) {
			FXDialogs.showInformation(stage, "Controlled workspace required",
					"Enable the controlled workspace setting before opening an OSRS project.");
			return;
		}
		DirectoryChooser chooser = new DirectoryChooser();
		chooser.setTitle("Choose RSPSi OSRS project folder");
		File projectDirectory = chooser.showDialog(stage);
		if (projectDirectory == null) return;

		ProjectLayout layout = new ProjectLayout(projectDirectory.toPath());
		ProjectMetadata metadata;
		try {
			metadata = layout.readMetadata();
		} catch (IOException exception) {
			FXDialogs.showException(stage, "Cannot open OSRS project",
					"The selected folder does not contain a readable project.json.", exception);
			return;
		}

		DirectoryChooser cacheChooser = new DirectoryChooser();
		cacheChooser.setTitle("Choose OSRS cache for this project");
		File cacheDirectory = cacheChooser.showDialog(stage);
		if (cacheDirectory == null) return;

		String openMode = FXDialogs.showConfirm(stage, "Choose OSRS project mode",
				"The selected source cache is never edited directly. Choose a separate prepared output cache to enable editing, or inspect the project read-only.",
				"Read-only", "Use output cache");
		Path outputCache = null;
		if ("Use output cache".equalsIgnoreCase(openMode)) {
			DirectoryChooser outputChooser = new DirectoryChooser();
			outputChooser.setTitle("Choose prepared writable OSRS output cache");
			File outputDirectory = outputChooser.showDialog(stage);
			if (outputDirectory == null) return;
			outputCache = outputDirectory.toPath();
		}

		String regionInput = FXDialogs.showTextInput(stage, "Choose starting region",
				"Enter region coordinates as regionX,regionY:", "50,50");
		int[] region = parseRegion(regionInput);
		if (region == null) {
			FXDialogs.showWarning(stage, "Invalid region",
					"Enter two region coordinates between 0 and 255, for example 50,50.");
			return;
		}

		OsrsStudioProject opened = null;
		SessionAutosaveCoordinator autosave = null;
		try {
			opened = outputCache == null
					? OsrsStudioProject.openReadOnly(cacheDirectory.toPath(), metadata)
					: OsrsStudioProject.openWithOpenRuneOutput(cacheDirectory.toPath(),
							outputCache, metadata);
			var projectRegion = opened.openRegion(region[0], region[1]);
			if (projectRegion.region().session().canEdit()) {
				autosave = opened.attachAutosave(layout, projectRegion.region().session());
			}
			closeOsrsAutosave();
			if (osrsStudioProject != null) osrsStudioProject.close();
			osrsStudioProject = opened;
			osrsAutosave = autosave;
			osrsProjectLayout = layout;
			opened = null;
			autosave = null;
			osrsProjectActive = true;
			// The controlled panels bind directly to this session. Keep the
			// window-level menu bridge on the same source of truth as well.
			controlledSession = projectRegion.region().session();
			if (clientInstance != null) {
				clientInstance.removeMapReadyListener(controlledMapReadyListener);
			}
			if (controlledDocumentBridge != null) {
				controlledDocumentBridge.close();
				controlledDocumentBridge = null;
			}
			ControlledWorkspaceBridge.bindProject(controlledWorkspaceShell, projectRegion,
					osrsStudioProject.definitions(), osrsStudioProject.assets());
			startOsrsAutosave(projectRegion.region().session());
			offerOsrsRecovery(projectRegion.region().session());
			updateHistoryMenuState();
			log.info("Opened {} OSRS project {} at region {},{}",
					projectRegion.region().session().canEdit() ? "editable" : "read-only",
					layout.root(), region[0], region[1]);
		} catch (IOException | RuntimeException exception) {
			if (autosave != null) autosave.close();
			if (opened != null) opened.close();
			FXDialogs.showException(stage, "Cannot open OSRS project",
					"The cache or selected region could not be opened.", exception);
		}
	}

	private void startOsrsAutosave(EditorSession session) {
		if (osrsAutosave == null || !session.canEdit()) return;
		osrsAutosaveTask = service.scheduleAtFixedRate(() -> {
			try {
				osrsAutosave.autosaveNow();
				log.debug("OSRS project autosave completed");
			} catch (Exception exception) {
				log.warn("OSRS project autosave failed", exception);
			}
		}, 60, 60, TimeUnit.SECONDS);
	}

	private void offerOsrsRecovery(EditorSession session) {
		if (osrsAutosave == null || osrsProjectLayout == null || !osrsAutosave.hasSnapshot()) {
			return;
		}
		try {
			if (!osrsAutosave.snapshotMatchesProject()) return;
			SessionAutosaveStore.AutosaveSnapshot snapshot = osrsAutosave.readSnapshot();
			if (snapshot.world().width() != session.world().width()
					|| snapshot.world().length() != session.world().length()
					|| snapshot.world().planes() != session.world().planes()) {
				FXDialogs.showWarning(stage, "Autosave cannot be restored",
						"The recovery snapshot belongs to a different region size or plane count. It was left untouched.");
				return;
			}
			String response = FXDialogs.showConfirm(stage, "Recover OSRS autosave?",
					"A matching recovery snapshot was found. Recover it as one undoable edit? The source cache remains unchanged until you save.",
					"Recover", "Discard");
			if ("Recover".equalsIgnoreCase(response)) {
				TileBounds bounds = new TileBounds(0, 0, snapshot.world().width() - 1,
						snapshot.world().length() - 1);
				WorldFragment fragment = WorldFragment.capture(snapshot.world(), bounds);
				session.execute(new PasteFragmentCommand(fragment, 0, 0,
						"Recover OSRS autosave"));
			}
		} catch (IOException | RuntimeException exception) {
			FXDialogs.showException(stage, "Cannot recover OSRS autosave",
					"The recovery snapshot was not applied. The project remains open.", exception);
		}
	}

	private void closeOsrsAutosave() {
		if (osrsAutosaveTask != null) {
			osrsAutosaveTask.cancel(false);
			osrsAutosaveTask = null;
		}
		if (osrsAutosave != null) {
			osrsAutosave.close();
			osrsAutosave = null;
		}
		osrsProjectLayout = null;
	}

	private static int[] parseRegion(String value) {
		if (value == null) return null;
		String[] parts = value.trim().split(",");
		if (parts.length != 2) return null;
		try {
			int x = Integer.parseInt(parts[0].trim());
			int y = Integer.parseInt(parts[1].trim());
			return x >= 0 && x <= 255 && y >= 0 && y <= 255 ? new int[] {x, y} : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	/**
	 * Keeps the existing menu usable while history migrates from SceneGraph to
	 * the neutral session. Once a canonical command exists, the session owns
	 * the menu state and dispatch; otherwise the legacy stacks remain the
	 * compatibility fallback.
	 */
	private void updateHistoryMenuState() {
		if (controller == null) {
			return;
		}
		boolean canUndo = controlledSession != null && controlledSession.history().canUndo();
		boolean canRedo = controlledSession != null && controlledSession.history().canRedo();
		if (!canUndo) {
			canUndo = !SceneGraph.undoList.isEmpty();
		}
		if (!canRedo) {
			canRedo = !SceneGraph.redoList.isEmpty();
		}
		controller.getUndoMenuItem().setDisable(!canUndo);
		controller.getRedoMenuItem().setDisable(!canRedo);
	}

	private void handleUndo() {
		if (controlledSession != null && controlledSession.history().canUndo()) {
			controlledSession.undo();
		} else {
			SceneGraph.undo();
		}
		updateHistoryMenuState();
	}

	private void handleRedo() {
		if (controlledSession != null && controlledSession.history().canRedo()) {
			controlledSession.redo();
		} else {
			SceneGraph.redo();
		}
		updateHistoryMenuState();
	}

	/** Dispatches keyboard undo through the same bridge as the Edit menu. */
	public void undoActiveEditorSession() {
		handleUndo();
	}

	/** Dispatches keyboard redo through the same bridge as the Edit menu. */
	public void redoActiveEditorSession() {
		handleRedo();
	}

	/**
	 * Deletes the canonical object selection when an OSRS project is active.
	 * Returning false leaves the legacy key path responsible for compatibility.
	 */
	public boolean deleteActiveEditorSelection() {
		if (!osrsProjectActive || controlledSession == null) {
			return false;
		}
		Selection selection = controlledSession.selection().current();
		Set<WorldObject> objects = new LinkedHashSet<>();
		if (selection instanceof ObjectSelection object) {
			objects.add(object.object());
		} else if (selection instanceof ObjectSetSelection objectSet) {
			objects.addAll(objectSet.objects());
		}
		if (!objects.isEmpty() && controlledSession.canEdit()) {
			controlledSession.execute(new CompositeEditCommand("Delete selected objects",
					objects.stream().map(DeleteObjectCommand::new).toList()));
			controlledSession.selection().clear();
			updateHistoryMenuState();
		}
		return true;
	}

	@Subscribe(threadMode = ThreadMode.ASYNC)
	public void onStatusUpdate(StatusUpdate update) {
		//Platform.runLater(() -> controller.getStatusLabel().setText(update.getText()));
	}

	public void setupSaveOptions() {

		controller.getSaveAsPackFile().setOnAction(act -> {

			File landscapeFile = RetentionFileChooser.showSaveDialog("Enter a name for packed maps file...", stage, "",
					FilterMode.PACK);
			if (landscapeFile == null)
				return;

			byte[] tileMap = MultiMapEncoder.encode(Lists.newArrayList(clientInstance.chunks));



			try {

				Files.write(landscapeFile.toPath(), tileMap);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				FXDialogs.showError(stage,"Error while saving map!", "There was an error while writing packed maps file.");
			}


		});
		controller.getSaveMenuItem().setOnAction(act -> {
			if (osrsProjectActive && controlledSession != null) {
				saveCanonicalSession();
				return;
			}
			int startX = clientInstance.xCameraPos;
			int startY = clientInstance.yCameraPos;

			for(Chunk chunk : clientInstance.chunks) {
				clientInstance.xCameraPos = (chunk.offsetX + 32) * 128;
				clientInstance.yCameraPos = (chunk.offsetY + 32) * 128;

				String saveNameTiles = String.valueOf(chunk.tileMapId);
				String saveNameObjects = String.valueOf(chunk.objectMapId);

				boolean saveGroupName = (Boolean) Settings.properties.getOrDefault("save_group_name",false);

				if (saveGroupName && !chunk.objectMapGroup.isEmpty() && !chunk.tileMapGroup.isEmpty()) {
                    saveNameTiles = chunk.tileMapGroup;
                    saveNameObjects = chunk.objectMapGroup;
				}

				File landscapeFile = RetentionFileChooser.showSaveDialog("Enter a name for tiles...", stage, saveNameTiles,
						FilterMode.DAT, FilterMode.GZIP);
				if (landscapeFile == null)
					return;
				File objectFile = RetentionFileChooser.showSaveDialog("Enter a name for objects...", stage, saveNameObjects,
						FilterMode.DAT, FilterMode.GZIP);

				if (objectFile == null)
					return;

				byte[] objectMap = clientInstance.sceneGraph.saveObjects(chunk);
				byte[] tileMap = chunk.mapRegion.save_terrain_block(chunk);

				if (landscapeFile.getName().endsWith(".gz")) {
					try {
						tileMap = GZIPUtils.gzipBytes(tileMap);
						if(tileMap == null)
							throw new IOException("GZIP error");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						FXDialogs.showError(stage,"Error while saving map!",
								"There was an error while writing map file.");
						return;
					}
				}
				if (objectFile.getName().endsWith(".gz")) {
					try {
						objectMap = GZIPUtils.gzipBytes(objectMap);
						if(objectMap == null)
							throw new IOException("GZIP error");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						FXDialogs.showError(stage,"Error while saving map!",
								"There was an error while writing map file.");
						return;
					}
				}

				try {

					Files.write(objectFile.toPath(), objectMap);
					Files.write(landscapeFile.toPath(), tileMap);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					FXDialogs.showError(stage,"Error while saving map!", "There was an error while writing map file.");
				}

			}


			clientInstance.xCameraPos = startX;
			clientInstance.yCameraPos = startY;

		});
	}

	private void saveCanonicalSession() {
		if (!controlledSession.canSave()) {
			FXDialogs.showWarning(stage, "Cannot save OSRS project",
					"This project session is read-only or has no output cache configured.");
			return;
		}
		try {
			controlledSession.save();
			updateHistoryMenuState();
		} catch (RuntimeException exception) {
			FXDialogs.showException(stage, "Cannot save OSRS project",
					"The canonical session could not write its configured output cache.", exception);
		}
	}

	public void setupOpenOptions() throws Exception {

		GenerateNewMapWindow genNew = new GenerateNewMapWindow();
		
		genNew.start(new Stage());
	
		controller.getNewMapButton().setOnAction(evt -> {
			/*try {
				byte[] landscape = ByteStreams.toByteArray(getClass().getResourceAsStream("/misc/blank_region.dat"));
				byte[] object = ByteStreams.toByteArray(getClass().getResourceAsStream("/misc/blank_regionO.dat"));

				Client.runLater.add(() -> {
					clientInstance.loadFiles(landscape, object, 0, 0);
					fullMapView.resizeMap();
				});
			} catch(Exception ex) {
				FXDialogs.showError("Error while creating new map", "There was a failure while attempting to initialize\na new map.");
				ex.printStackTrace();
			}*/
			
			genNew.show();
			if(genNew.okClicked) {
				int chunkWidth = genNew.getWidth();
				int chunkHeight = genNew.getLength();
				try {

					Client.runLater.add(() -> {
						clientInstance.loadNew(chunkWidth, chunkHeight, genNew.getHeights());
						fullMapView.resizeMap();
					});
				} catch(Exception ex) {
					FXDialogs.showError(stage,"Error while creating new map", "There was a failure while attempting to initialize\na new map.");
					ex.printStackTrace();
				}
			}
		
		});

		controller.getOpenAsPackBtn().setOnAction(act -> {

			selectPack.show();

			if(!selectPack.valid())
				return;

			File packFile = new File(selectPack.getPackText());


			try {
				final byte[] packData = Files.readAllBytes(packFile.toPath());


				Client.runLater.add(() ->{
					clientInstance.loadChunks(MultiMapEncoder.decode(packData));
					fullMapView.resizeMap();
				});
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				FXDialogs.showError(stage,"Error while loading map!",
						"There was an error while loading or parsing the selected file.");
			}

		});



		controller.getOpenFileButton().setOnAction(act -> {

			selectFiles.show();

			if(!selectFiles.valid())
				return;
			
			Client.runLater.add(() -> {
				clientInstance.loadChunks(selectFiles.prepareChunks());
				fullMapView.resizeMap();
			});

		});

		controller.getOpenOsrsProjectButton().setOnAction(evt -> openOsrsProject());

		controller.getOpenHashButton().setOnAction(evt -> {

			pickHash.show();
			if(!pickHash.valid())
				return;
			int hash = pickHash.getHash();
			int width = pickHash.getWidth();
			int length = pickHash.getLength();
			Client.runLater.add(() -> { 
				clientInstance.loadCoordinates((hash >> 8) * 64, (hash & 0xff) * 64, width, length);
				fullMapView.resizeMap();
			});
		});

		controller.getOpenCoordinateButton().setOnAction(evt -> {
			/*String value = FXDialogs.showTextInput("Load from coordinates", "Please enter the regions coordinates in the format x,y: ", "");
			if(value != null && !value.equals("")) {
				String[] split = value.replaceAll(" ", "").split(",");
				int x = Integer.valueOf(split[0]);
				int y = Integer.valueOf(split[1]);
				x /= 64;
				y /= 64;
				int hash = (x << 0x39b8d2e8) + y;
				Client.runLater.add(() -> clientInstance.loadCoordinates((hash >> 8) * 64, (hash & 0xff) * 64, 1, 1));
			}*/

			pickCoords.show();
			if(!pickCoords.valid())
				return;
			int x = pickCoords.getXCoordinate();
			int y = pickCoords.getYCoordinate();	
			x /= 64;
			y /= 64;
			int hash = (x << 8) + y;
			int width = pickCoords.getWidth();
			int length = pickCoords.getLength();
			Client.runLater.add(() -> { 
				clientInstance.loadCoordinates((hash >> 8) * 64, (hash & 0xff) * 64, width, length);
				fullMapView.resizeMap();
			});

		});
	}

	public static MainWindow getSingleton() {
		return singleton;
	}

}
