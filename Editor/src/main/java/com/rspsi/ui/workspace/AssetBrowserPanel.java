package com.rspsi.ui.workspace;

import com.rspsi.editor.assets.AssetDescriptor;
import com.rspsi.editor.assets.AssetRepository;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** JavaFX adapter for the neutral asset repository. */
public final class AssetBrowserPanel extends VBox {
    private static final String ALL = "All";

    private final TextField searchField = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    private final ListView<AssetDescriptor> results = new ListView<>();
    private final Label status = new Label();
    private final Label details = new Label();
    private final Label searchLabel = new Label("Search");
    private final StackPane filterHost = new StackPane();
    private final HBox horizontalFilters = new HBox(8);
    private final VBox verticalFilters = new VBox(6);
    private AssetRepository repository;
    private Consumer<AssetDescriptor> selectionListener = ignored -> { };

    public AssetBrowserPanel(AssetRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        setSpacing(8);
        setPadding(new Insets(12));
        getStyleClass().add("workspace-asset-browser");
        setAccessibleText("Asset browser");

        Label title = new Label("Assets");
        title.getStyleClass().add("workspace-panel-title");

        searchLabel.setLabelFor(searchField);
        searchField.setPromptText("Name, symbolic key, or ID");
        searchField.setAccessibleText("Search assets by name, symbolic key, or numeric ID");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        category.getItems().setAll(ALL, "Objects", "Underlays", "Overlays", "Textures", "Models", "Sprites");
        category.setValue(ALL);
        category.setAccessibleText("Asset category filter");
        category.setPrefWidth(110);

        horizontalFilters.getChildren().setAll(searchLabel, searchField, category);
        horizontalFilters.getStyleClass().add("workspace-asset-filters");
        verticalFilters.getStyleClass().add("workspace-asset-filters");
        filterHost.getChildren().setAll(horizontalFilters);
        filterHost.setMinHeight(Region.USE_PREF_SIZE);
        filterHost.widthProperty().addListener((observable, oldValue, newValue) ->
                updateFilterLayout(newValue.doubleValue()));

        status.getStyleClass().add("workspace-panel-status");
        results.setPlaceholder(new Label("No matching assets."));
        results.setAccessibleText("Asset search results");
        results.setCellFactory(view -> new AssetCell());
        VBox.setVgrow(results, Priority.ALWAYS);

        details.setWrapText(true);
        details.setMinHeight(48);
        details.getStyleClass().add("workspace-asset-details");

        searchField.textProperty().addListener((observable, oldValue, newValue) -> refresh());
        category.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
        results.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            showDetails(newValue);
            selectionListener.accept(newValue);
        });

        getChildren().addAll(title, filterHost, status, results, new Separator(), details);
        updateFilterLayout(getWidth());
        refresh();
    }

    public void setRepository(AssetRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        refresh();
    }

    public Optional<AssetDescriptor> selectedAsset() {
        return Optional.ofNullable(results.getSelectionModel().getSelectedItem());
    }

    public void onAssetSelected(Consumer<AssetDescriptor> listener) {
        this.selectionListener = Objects.requireNonNull(listener, "listener");
    }

    public void refresh() {
        List<AssetDescriptor> assets = repository.search(searchField.getText());
        String selectedCategory = category.getValue();
        if (selectedCategory != null && !ALL.equals(selectedCategory)) {
            String type = selectedCategory.substring(0, selectedCategory.length() - 1).toLowerCase(Locale.ROOT);
            assets = assets.stream().filter(asset -> asset.type().equals(type)).toList();
        }
        AssetDescriptor selected = selectedAsset().orElse(null);
        results.setItems(FXCollections.observableArrayList(assets));
        if (selected != null) results.getSelectionModel().select(selected);
        status.setText(assets.size() + (assets.size() == 1 ? " asset" : " assets"));
        showDetails(results.getSelectionModel().getSelectedItem());
    }

    private void updateFilterLayout(double width) {
        boolean narrow = width > 0 && width < 460;
        if (narrow) {
            if (filterHost.getChildren().size() != 1 || filterHost.getChildren().get(0) != verticalFilters) {
                horizontalFilters.getChildren().clear();
                verticalFilters.getChildren().setAll(searchLabel, searchField, category);
                filterHost.getChildren().setAll(verticalFilters);
            }
        } else if (filterHost.getChildren().size() != 1 || filterHost.getChildren().get(0) != horizontalFilters) {
            verticalFilters.getChildren().clear();
            horizontalFilters.getChildren().setAll(searchLabel, searchField, category);
            filterHost.getChildren().setAll(horizontalFilters);
        }
        searchField.setMaxWidth(Double.MAX_VALUE);
        category.setMaxWidth(narrow ? Double.MAX_VALUE : 110);
        if (narrow) {
            VBox.setVgrow(searchField, Priority.NEVER);
        } else {
            HBox.setHgrow(searchField, Priority.ALWAYS);
        }
    }

    private void showDetails(AssetDescriptor asset) {
        if (asset == null) {
            details.setText("Select an asset to inspect its definition.");
            return;
        }
        // Search results use lightweight model descriptors. Resolve only the
        // selected item, so listing a large model index does not decode every mesh.
        AssetDescriptor resolved = repository.get(asset.id(), asset.type()).orElse(asset);
        String symbolic = resolved.symbolicName().map(value -> "\nSymbolic: " + value).orElse("");
        String properties = resolved.details().isEmpty()
                ? "" : "\n" + String.join("\n", resolved.details());
        details.setText(resolved.name() + "\nType: " + resolved.type() + " · ID: " + resolved.id()
                + symbolic + properties);
    }

    private static final class AssetCell extends ListCell<AssetDescriptor> {
        @Override
        protected void updateItem(AssetDescriptor asset, boolean empty) {
            super.updateItem(asset, empty);
            if (empty || asset == null) {
                setText(null);
                return;
            }
            String symbolic = asset.symbolicName().map(value -> " · " + value).orElse("");
            setText(asset.name() + "\n" + asset.type() + " · ID " + asset.id() + symbolic);
        }
    }
}
