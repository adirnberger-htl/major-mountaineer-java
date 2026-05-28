package org.example.majorgame;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.majorgame.game.GameEngine;
import org.example.majorgame.game.GameResult;
import org.example.majorgame.game.GameSnapshot;
import org.example.majorgame.game.combat.CombatSnapshot;
import org.example.majorgame.game.combat.CombatTile;
import org.example.majorgame.game.combat.CombatUnitSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MajorGameController {
    private static final int STATUS_MAX = 100;
    private static final String ICON_PATH_PREFIX = "/org/example/majorgame/icons/";
    private static final int BATTLE_TILE_SIZE = 64;
    private static final String HOHE_DIRN_BACKDROP_CLASS = "hohe-dirn-bg";

    private final GameEngine engine = new GameEngine();
    private final List<String> commandHistory = new ArrayList<>();
    private final Map<String, Image> iconCache = new HashMap<>();
    private final ObservableList<String> inventoryItems = FXCollections.observableArrayList();
    private final FilteredList<String> filteredInventoryItems = new FilteredList<>(inventoryItems);
    private final SortedList<String> sortedInventoryItems = new SortedList<>(filteredInventoryItems, Comparator.naturalOrder());
    private int historyIndex = 0;
    private CombatSnapshot currentCombatSnapshot;
    private String selectedInventoryItemId;

    @FXML
    private BorderPane rootPane;

    @FXML
    private Label locationPromptLabel;

    @FXML
    private Label juiceLabel;

    @FXML
    private Label honorLabel;

    @FXML
    private ProgressBar honorBar;

    @FXML
    private ProgressBar juiceBar;

    @FXML
    private FlowPane inventoryIconsPane;

    @FXML
    private ListView<String> inventoryListView;

    @FXML
    private TextArea outputArea;

    @FXML
    private TextField commandField;

    @FXML
    private HBox commandPanel;

    @FXML
    private StackPane battlePane;

    @FXML
    private GridPane battleGrid;

    @FXML
    private VBox battleActionBox;

    @FXML
    private FlowPane battleActionsPane;

    @FXML
    private Label battleStatusLabel;

    @FXML
    private Button battleEndTurnButton;

    @FXML
    private void initialize() {
        appendResult(engine.openingScene());
        historyIndex = commandHistory.size();
        setupInventoryListView();
    }

    @FXML
    protected void onSubmitCommand() {
        String input = commandField.getText();
        if (input == null || input.isBlank()) {
            return;
        }

        String normalizedCommand = normalizeCommand(input);
        commandHistory.add(input);
        historyIndex = commandHistory.size();
        if ("clear".equals(normalizedCommand)) {
            outputArea.clear();
        } else {
            appendResult(engine.executeCommand(input));
        }
        commandField.clear();
    }

    @FXML
    protected void onCommandFieldKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.UP) {
            if (commandHistory.isEmpty()) {
                return;
            }
            historyIndex = Math.max(0, historyIndex - 1);
            commandField.setText(commandHistory.get(historyIndex));
            commandField.positionCaret(commandField.getText().length());
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.DOWN) {
            if (commandHistory.isEmpty()) {
                return;
            }
            if (historyIndex < commandHistory.size() - 1) {
                historyIndex += 1;
                commandField.setText(commandHistory.get(historyIndex));
            } else {
                historyIndex = commandHistory.size();
                commandField.clear();
            }
            commandField.positionCaret(commandField.getText().length());
            event.consume();
        }
    }

    private void appendResult(GameResult result) {
        if (result.snapshot().gameOver()) {
            outputArea.clear();
        }
        for (String message : result.messages()) {
            outputArea.appendText(message + System.lineSeparator());
        }
        outputArea.appendText(System.lineSeparator());
        updateBattleStatus(result);
        renderState(result.snapshot());
    }

    private void renderState(GameSnapshot snapshot) {
        locationPromptLabel.setText(snapshot.locationPrompt());
        juiceLabel.setText("Apple juice: " + snapshot.appleJuice() + "/" + STATUS_MAX);
        honorLabel.setText("Honor: " + snapshot.honor() + "/" + STATUS_MAX);
        juiceBar.setProgress(normalizeStatus(snapshot.appleJuice()));
        honorBar.setProgress(normalizeStatus(snapshot.honor()));
        renderInventory(snapshot.inventory());
        updateBackdrop(snapshot.locationId());
        renderMode(snapshot);
    }

    private void updateBackdrop(String locationId) {
        if (rootPane == null) {
            return;
        }
        boolean usePathwayBackdrop = "hohe_dirn".equals(locationId) || locationId.startsWith("hd_");
        if (usePathwayBackdrop) {
            if (!rootPane.getStyleClass().contains(HOHE_DIRN_BACKDROP_CLASS)) {
                rootPane.getStyleClass().add(HOHE_DIRN_BACKDROP_CLASS);
            }
            return;
        }
        rootPane.getStyleClass().remove(HOHE_DIRN_BACKDROP_CLASS);
    }

    private void renderMode(GameSnapshot snapshot) {
        boolean inCombat = snapshot.inCombat() && snapshot.combatSnapshot() != null;
        outputArea.setVisible(!inCombat);
        outputArea.setManaged(!inCombat);
        commandPanel.setVisible(!inCombat);
        commandPanel.setManaged(!inCombat);
        battlePane.setVisible(inCombat);
        battlePane.setManaged(inCombat);

        if (!inCombat) {
            currentCombatSnapshot = null;
            battleGrid.getChildren().clear();
            battleEndTurnButton.setVisible(false);
            battleEndTurnButton.setManaged(false);
            return;
        }

        currentCombatSnapshot = snapshot.combatSnapshot();
        if (battleStatusLabel.getText() == null || battleStatusLabel.getText().isBlank()) {
            battleStatusLabel.setText(currentCombatSnapshot.statusText());
        }
        battleEndTurnButton.setVisible(currentCombatSnapshot.endTurnAvailable());
        battleEndTurnButton.setManaged(currentCombatSnapshot.endTurnAvailable());
        renderBattleGrid(currentCombatSnapshot);
        renderBattleActions(currentCombatSnapshot);
    }

    private void renderBattleGrid(CombatSnapshot snapshot) {
        battleGrid.getChildren().clear();
        for (int row = 0; row < snapshot.rows(); row++) {
            for (int col = 0; col < snapshot.cols(); col++) {
                final int tileRow = row;
                final int tileCol = col;
                StackPane tile = new StackPane();
                tile.getStyleClass().add("battle-tile");
                tile.setMinSize(BATTLE_TILE_SIZE, BATTLE_TILE_SIZE);
                tile.setPrefSize(BATTLE_TILE_SIZE, BATTLE_TILE_SIZE);
                tile.setMaxSize(BATTLE_TILE_SIZE, BATTLE_TILE_SIZE);
                tile.setOnMouseClicked(event -> onBattleTileClicked(tileRow, tileCol));

                CombatTile tileRef = new CombatTile(tileRow, tileCol);
                if (snapshot.reachableTiles().contains(tileRef)) {
                    tile.getStyleClass().add("battle-tile-reachable");
                }
                if (snapshot.targetableTiles().contains(tileRef)) {
                    tile.getStyleClass().add("battle-tile-targetable");
                }

                CombatUnitSnapshot unit = findUnitAt(snapshot, tileRow, tileCol);
                if (unit != null) {
                    Label unitLabel = new Label(unit.player() ? "P" : "E");
                    unitLabel.getStyleClass().add(unit.player() ? "battle-unit-player" : "battle-unit-enemy");
                    Label hpLabel = new Label(unit.hp() + "/" + unit.maxHp());
                    hpLabel.getStyleClass().add("battle-unit-hp");
                    VBox unitNode = new VBox(2, unitLabel, hpLabel);
                    unitNode.getStyleClass().add("battle-unit");
                    tile.getChildren().add(unitNode);
                }
                battleGrid.add(tile, tileCol, tileRow);
            }
        }
    }

    private void renderBattleActions(CombatSnapshot snapshot) {
        boolean showActionBox = snapshot.playerTurn() && snapshot.selectedAbilityId() == null && !snapshot.turnLocked();
        battleActionBox.setVisible(showActionBox);
        battleActionBox.setManaged(showActionBox);
        battleActionsPane.getChildren().clear();
        if (!showActionBox) {
            return;
        }
        for (String abilityId : snapshot.playerAbilityIds()) {
            Button abilityButton = new Button(formatItemName(abilityId));
            abilityButton.getStyleClass().add("battle-action-button");
            abilityButton.setOnAction(event -> appendResult(engine.combatSelectAbility(abilityId)));
            battleActionsPane.getChildren().add(abilityButton);
        }
    }

    private CombatUnitSnapshot findUnitAt(CombatSnapshot snapshot, int row, int col) {
        for (CombatUnitSnapshot unit : snapshot.units()) {
            if (unit.row() == row && unit.col() == col) {
                return unit;
            }
        }
        return null;
    }

    private void onBattleTileClicked(int row, int col) {
        if (currentCombatSnapshot == null) {
            return;
        }
        GameResult result = currentCombatSnapshot.selectedAbilityId() == null
                ? engine.combatMovePlayer(row, col)
                : engine.combatTargetAt(row, col);
        appendResult(result);
    }

    @FXML
    protected void onBattleEndTurnClicked() {
        appendResult(engine.combatEndTurn());
    }

    private void updateBattleStatus(GameResult result) {
        if (!result.snapshot().inCombat() || battleStatusLabel == null) {
            return;
        }
        String status = result.snapshot().combatSnapshot() != null
                ? result.snapshot().combatSnapshot().statusText()
                : "";
        for (int index = result.messages().size() - 1; index >= 0; index--) {
            String message = result.messages().get(index);
            if (!message.isBlank()) {
                status = message;
                break;
            }
        }
        battleStatusLabel.setText(status);
    }

    private double normalizeStatus(int value) {
        int clamped = Math.max(0, Math.min(STATUS_MAX, value));
        return clamped / (double) STATUS_MAX;
    }

    private void renderInventory(Set<String> inventory) {
        inventoryIconsPane.getChildren().clear();
        inventoryItems.setAll(inventory);
        if (sortedInventoryItems.isEmpty()) {
            Label emptyLabel = new Label("No gear collected yet.");
            emptyLabel.getStyleClass().add("inventory-empty");
            inventoryIconsPane.getChildren().add(emptyLabel);
            if (inventoryListView != null) {
                inventoryListView.getSelectionModel().clearSelection();
            }
            selectedInventoryItemId = null;
            return;
        }

        if (inventoryListView != null
                && selectedInventoryItemId != null
                && !sortedInventoryItems.contains(selectedInventoryItemId)) {
            inventoryListView.getSelectionModel().clearSelection();
            selectedInventoryItemId = null;
        }

        sortedInventoryItems.stream()
                .map(this::createInventoryItemNode)
                .forEach(inventoryIconsPane.getChildren()::add);
    }

    private VBox createInventoryItemNode(String itemId) {
        VBox card = new VBox(6);
        card.getStyleClass().add("inventory-item");
        card.setOnMouseClicked(event -> {
            if (inventoryListView != null) {
                inventoryListView.getSelectionModel().select(itemId);
            }
        });

        Image itemIcon = loadItemIcon(itemId);
        if (itemIcon != null) {
            ImageView iconView = new ImageView(itemIcon);
            iconView.setFitWidth(56);
            iconView.setFitHeight(56);
            iconView.setPreserveRatio(true);
            card.getChildren().add(iconView);
        } else {
            Label fallbackIcon = new Label("?");
            fallbackIcon.getStyleClass().add("inventory-fallback-icon");
            card.getChildren().add(fallbackIcon);
        }

        Label nameLabel = new Label(formatItemName(itemId));
        nameLabel.getStyleClass().add("inventory-item-label");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(96);
        card.getChildren().add(nameLabel);
        return card;
    }

    private Image loadItemIcon(String itemId) {
        return iconCache.computeIfAbsent(itemId, key -> {
            var iconResource = MajorGameController.class.getResource(ICON_PATH_PREFIX + key + ".png");
            if (iconResource == null) {
                return null;
            }
            return new Image(iconResource.toExternalForm());
        });
    }

    private String formatItemName(String itemId) {
        String[] words = itemId.split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.toString();
    }

    private String normalizeCommand(String command) {
        String value = command.trim().toLowerCase();
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }

    private void setupInventoryListView() {
        if (inventoryListView == null) {
            return;
        }
        filteredInventoryItems.setPredicate(itemId -> itemId != null && !itemId.isBlank());
        inventoryListView.setItems(sortedInventoryItems);
        inventoryListView.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> selectedInventoryItemId = newValue);
    }
}
