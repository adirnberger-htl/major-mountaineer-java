package org.example.majorgame;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.example.majorgame.game.Direction;
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
import java.util.stream.Collectors;

public class MajorGameController {
    private static final int STATUS_MAX = 100;
    private static final String ICON_PATH_PREFIX = "/org/example/majorgame/icons/";
    private static final double BATTLE_TILE_SIZE = 80.0;
    private static final String HOHE_DIRN_BACKDROP_CLASS = "hohe-dirn-bg";

    private final GameEngine engine = new GameEngine();
    private final List<String> commandHistory = new ArrayList<>();
    private final Map<String, Image> iconCache = new HashMap<>();
    private int historyIndex = 0;
    private CombatSnapshot currentCombatSnapshot;
    private PauseTransition notificationTimer;

    @FXML private BorderPane rootPane;
    @FXML private VBox topPanel;
    @FXML private VBox mainContentBox;
    @FXML private VBox instructionsOverlay;
    @FXML private VBox instructionsListContainer;
    @FXML private VBox descriptionContainer;
    @FXML private Label locationTitleLabel;
    @FXML private Label notificationLabel;
    @FXML private HBox inventoryPanel;
    @FXML private VBox directionsListContainer;
    @FXML private Label descriptionArea;
    @FXML private FlowPane groundItemsPane;
    @FXML private ListView<String> dialogListView;

    @FXML private Label juiceLabel;
    @FXML private Label honorLabel;
    @FXML private ProgressBar honorBar;
    @FXML private ProgressBar juiceBar;
    @FXML private FlowPane inventoryIconsPane;

    @FXML private TextField commandField;
    @FXML private HBox commandPanel;

    @FXML private HBox battlePane;
    @FXML private GridPane battleGrid;
    @FXML private VBox battleActionBox;
    @FXML private FlowPane battleActionsPane;
    @FXML private Label battleStatusLabel;
    @FXML private Button battleEndTurnButton;

    @FXML
    private void initialize() {
        setupDragAndDrop();
        setupListViews();
        appendResult(engine.openingScene());
        historyIndex = commandHistory.size();
        
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == javafx.scene.input.KeyCode.TAB) {
                        if (!commandField.isFocused() && !instructionsOverlay.isVisible()) {
                            commandField.requestFocus();
                            event.consume();
                        }
                    }
                });
            }
        });
    }

    private void setupListViews() {
        dialogListView.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox(12);
                    Label msgLabel = new Label();
                    msgLabel.setWrapText(true);
                    msgLabel.setMaxWidth(400);
                    msgLabel.getStyleClass().add("dialog-message");

                    if (item.startsWith("SYSTEM: ")) {
                        msgLabel.setText(item.substring(8).trim());
                        msgLabel.getStyleClass().add("dialog-system");
                        box.setAlignment(javafx.geometry.Pos.CENTER);
                        box.getChildren().add(msgLabel);
                    } else if (item.startsWith("Major: ")) {
                        msgLabel.setText(item.substring(7).trim());
                        msgLabel.getStyleClass().add("dialog-major");
                        box.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
                        box.getChildren().add(msgLabel);
                    } else {
                        msgLabel.getStyleClass().add("dialog-npc");
                        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        
                        int colonIndex = item.indexOf(": ");
                        if (colonIndex != -1) {
                            String name = item.substring(0, colonIndex);
                            msgLabel.setText(item.substring(colonIndex + 2).trim());
                            
                            String avatarPath = null;
                            if (name.equalsIgnoreCase("Man") || name.equalsIgnoreCase("Old Man")) {
                                avatarPath = "images/old_man_avatar.png";
                            }
                            
                            if (avatarPath != null) {
                                try {
                                    javafx.scene.image.Image avatarImg = new javafx.scene.image.Image(getClass().getResourceAsStream(avatarPath));
                                    javafx.scene.image.ImageView avatarView = new javafx.scene.image.ImageView(avatarImg);
                                    avatarView.setFitWidth(44);
                                    avatarView.setFitHeight(44);
                                    
                                    javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(22, 22, 22);
                                    avatarView.setClip(clip);
                                    
                                    VBox avatarContainer = new VBox(avatarView);
                                    avatarContainer.setAlignment(javafx.geometry.Pos.TOP_CENTER);
                                    
                                    box.getChildren().addAll(avatarContainer, msgLabel);
                                } catch (Exception e) {
                                    box.getChildren().add(msgLabel);
                                }
                            } else {
                                box.getChildren().add(msgLabel);
                            }
                        } else {
                            msgLabel.setText(item);
                            box.getChildren().add(msgLabel);
                        }
                    }
                    
                    setGraphic(box);
                }
            }
        });
    }

    private void setupDragAndDrop() {
        inventoryIconsPane.setOnDragOver(event -> {
            if (event.getGestureSource() != inventoryIconsPane && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        inventoryIconsPane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String itemId = db.getString();
                appendResult(engine.take(itemId));
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        groundItemsPane.setOnDragOver(event -> {
            if (event.getGestureSource() != groundItemsPane && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        groundItemsPane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String itemId = db.getString();
                appendResult(engine.drop(itemId));
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
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
            notificationLabel.setVisible(false);
            showNotification("Output cleared.");
        } else if ("instructions".equals(normalizedCommand)) {
            showInstructionsOverlay();
        } else {
            appendResult(engine.executeCommand(input));
        }
        commandField.clear();
    }

    @FXML
    protected void onInstructionsCommand() {
        showInstructionsOverlay();
    }
    
    @FXML
    protected void onCloseInstructions() {
        instructionsOverlay.setVisible(false);
        instructionsOverlay.setManaged(false);
        rootPane.setOpacity(1.0);
        rootPane.setDisable(false);
        commandField.requestFocus();
    }
    
    private void showInstructionsOverlay() {
        instructionsListContainer.getChildren().clear();
        List<String> rawLines = engine.instructions().messages();
        for (String line : rawLines) {
            if (line.isBlank() || line.equals("Available commands are:")) continue;
            HBox itemBox = new HBox(8);
            itemBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Label bullet = new Label("•");
            bullet.getStyleClass().add("instruction-bullet");
            
            HBox contentBox = new HBox(12);
            contentBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            
            String[] parts = line.split("--", 2);
            if (parts.length == 2) {
                Label cmdLabel = new Label(parts[0].trim());
                cmdLabel.getStyleClass().add("instruction-command");
                cmdLabel.setPrefWidth(120);
                
                Label sepLabel = new Label("-");
                sepLabel.getStyleClass().add("instruction-item");
                
                Label descLabel = new Label(parts[1].trim());
                descLabel.getStyleClass().add("instruction-item");
                
                contentBox.getChildren().addAll(cmdLabel, sepLabel, descLabel);
            } else {
                Label text = new Label(line.trim());
                text.getStyleClass().add("instruction-item");
                contentBox.getChildren().add(text);
            }
            
            itemBox.getChildren().addAll(bullet, contentBox);
            instructionsListContainer.getChildren().add(itemBox);
        }
        instructionsOverlay.setVisible(true);
        instructionsOverlay.setManaged(true);
        rootPane.setOpacity(0.3);
        rootPane.setDisable(true);
    }

    @FXML
    protected void onCommandFieldKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.UP) {
            if (commandHistory.isEmpty()) return;
            historyIndex = Math.max(0, historyIndex - 1);
            commandField.setText(commandHistory.get(historyIndex));
            commandField.positionCaret(commandField.getText().length());
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.DOWN) {
            if (commandHistory.isEmpty()) return;
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
        String messages = result.messages().stream()
                .filter(m -> !m.isBlank())
                .collect(Collectors.joining("\n"));
        if (!messages.isEmpty()) {
            showNotification(messages);
        }
        updateBattleStatus(result);
        renderState(result.snapshot());
    }

    private void showNotification(String text) {
        notificationLabel.getStyleClass().removeAll("notification-info", "notification-error");
        
        String lowerText = text.toLowerCase();
        if (lowerText.contains("can't") || lowerText.contains("aren't") || 
            lowerText.contains("don't") || lowerText.contains("full") || 
            lowerText.contains("nothing") || lowerText.contains("invalid")) {
            notificationLabel.getStyleClass().add("notification-error");
        } else {
            notificationLabel.getStyleClass().add("notification-info");
        }
        
        notificationLabel.setText(text);
        notificationLabel.setVisible(true);
        if (notificationTimer != null) {
            notificationTimer.stop();
        }
        notificationTimer = new PauseTransition(Duration.seconds(5));
        notificationTimer.setOnFinished(e -> notificationLabel.setVisible(false));
        notificationTimer.play();
    }

    private void renderState(GameSnapshot snapshot) {
        locationTitleLabel.setText(snapshot.locationTitle().toUpperCase());
        
        directionsListContainer.getChildren().clear();
        for (Map.Entry<Direction, String> entry : snapshot.availableDirections().entrySet()) {
            String item = entry.getKey().name() + " -> " + entry.getValue();
            directionsListContainer.getChildren().add(createDirectionNode(item));
        }

        descriptionArea.setText(String.join(System.lineSeparator(), snapshot.locationDescription()));
        
        dialogListView.getItems().clear();
        dialogListView.getItems().addAll(snapshot.locationDialog());

        juiceLabel.setText("Apple juice: " + snapshot.appleJuice() + "/" + STATUS_MAX);
        honorLabel.setText("Honor: " + snapshot.honor() + "/" + STATUS_MAX);
        juiceBar.setProgress(normalizeStatus(snapshot.appleJuice()));
        honorBar.setProgress(normalizeStatus(snapshot.honor()));

        renderInventory(snapshot.inventory());
        renderGroundItems(snapshot.visibleItems());

        updateBackdrop(snapshot.locationId());
        renderMode(snapshot);
    }

    private void updateBackdrop(String locationId) {
        if (rootPane == null) return;
        boolean usePathwayBackdrop = "hohe_dirn".equals(locationId) || locationId.startsWith("hd_");
        if (usePathwayBackdrop) {
            if (!rootPane.getStyleClass().contains(HOHE_DIRN_BACKDROP_CLASS)) {
                rootPane.getStyleClass().add(HOHE_DIRN_BACKDROP_CLASS);
            }
        } else {
            rootPane.getStyleClass().remove(HOHE_DIRN_BACKDROP_CLASS);
        }
    }

    private void renderMode(GameSnapshot snapshot) {
        boolean inCombat = snapshot.inCombat() && snapshot.combatSnapshot() != null;
        
        topPanel.setVisible(!inCombat);
        topPanel.setManaged(!inCombat);

        descriptionContainer.setVisible(!inCombat);
        descriptionContainer.setManaged(!inCombat);
        
        commandPanel.setVisible(!inCombat);
        commandPanel.setManaged(!inCombat);
        
        inventoryPanel.setVisible(!inCombat);
        inventoryPanel.setManaged(!inCombat);
        
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
        battleEndTurnButton.setVisible(true);
        battleEndTurnButton.setManaged(true);
        battleEndTurnButton.setDisable(!currentCombatSnapshot.endTurnAvailable());
        
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
                    VBox unitNode = new VBox(2);
                    unitNode.getStyleClass().add("battle-unit");
                    unitNode.setAlignment(javafx.geometry.Pos.CENTER);

                    if (unit.player()) {
                        Label unitLabel = new Label("M");
                        unitLabel.getStyleClass().add("battle-unit-player");
                        unitNode.getChildren().add(unitLabel);
                    } else {
                        String imgName = unit.name().toLowerCase().replace(" ", "_") + "_combat.png";
                        String imgPath = "images/" + imgName;
                        try {
                            javafx.scene.image.Image img = new javafx.scene.image.Image(getClass().getResourceAsStream(imgPath));
                            javafx.scene.image.ImageView imgView = new javafx.scene.image.ImageView(img);
                            imgView.setFitWidth(BATTLE_TILE_SIZE * 0.8);
                            imgView.setFitHeight(BATTLE_TILE_SIZE * 0.8);
                            imgView.setPreserveRatio(true);
                            unitNode.getChildren().add(imgView);
                        } catch (Exception e) {
                            Label unitLabel = new Label(unit.name().substring(0, 1));
                            unitLabel.getStyleClass().add("battle-unit-enemy");
                            unitNode.getChildren().add(unitLabel);
                        }
                    }
                    
                    Label hpLabel = new Label(unit.hp() + "/" + unit.maxHp());
                    hpLabel.getStyleClass().add("battle-unit-hp");
                    unitNode.getChildren().add(hpLabel);
                    
                    tile.getChildren().add(unitNode);
                }
                battleGrid.add(tile, tileCol, tileRow);
            }
        }
    }

    private void renderBattleActions(CombatSnapshot snapshot) {
        battleActionBox.setVisible(true);
        battleActionBox.setManaged(true);
        battleActionsPane.getChildren().clear();
        
        boolean actionsEnabled = snapshot.playerTurn() && snapshot.selectedAbilityId() == null && !snapshot.turnLocked();
        
        for (String abilityId : snapshot.playerAbilityIds()) {
            Button abilityButton = new Button(formatItemName(abilityId));
            abilityButton.getStyleClass().add("battle-action-button");
            abilityButton.setDisable(!actionsEnabled);
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
        if (currentCombatSnapshot == null) return;
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
        if (!result.snapshot().inCombat() || battleStatusLabel == null) return;
        
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
        if (inventory.isEmpty()) {
            Label emptyLabel = new Label("No gear collected yet.");
            emptyLabel.getStyleClass().add("inventory-empty");
            inventoryIconsPane.getChildren().add(emptyLabel);
            return;
        }

        inventory.stream()
                .sorted(Comparator.naturalOrder())
                .map(itemId -> createInventoryItemNode(itemId, true))
                .forEach(inventoryIconsPane.getChildren()::add);
    }

    private void renderGroundItems(List<String> items) {
        groundItemsPane.getChildren().clear();
        if (items.isEmpty()) {
            Label emptyLabel = new Label("Nothing here.");
            emptyLabel.getStyleClass().add("inventory-empty");
            groundItemsPane.getChildren().add(emptyLabel);
            return;
        }

        items.forEach(itemId -> groundItemsPane.getChildren().add(createInventoryItemNode(itemId, false)));
    }

    private VBox createInventoryItemNode(String itemId, boolean isInventory) {
        VBox card = new VBox(6);
        card.getStyleClass().add("inventory-item");
        
        // Setup dragging
        card.setOnDragDetected(event -> {
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(itemId);
            db.setContent(content);
            event.consume();
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
        
        Button actionBtn = new Button(isInventory ? "Drop" : "Take");
        actionBtn.getStyleClass().add("command-button");
        actionBtn.setOnAction(e -> {
            if (isInventory) {
                appendResult(engine.drop(itemId));
            } else {
                appendResult(engine.take(itemId));
            }
        });
        card.getChildren().add(actionBtn);
        
        return card;
    }

    private Image loadItemIcon(String itemId) {
        return iconCache.computeIfAbsent(itemId, key -> {
            var iconResource = MajorGameController.class.getResource(ICON_PATH_PREFIX + key + ".png");
            if (iconResource == null) return null;
            return new Image(iconResource.toExternalForm());
        });
    }

    private String formatItemName(String itemId) {
        String[] words = itemId.split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!builder.isEmpty()) builder.append(' ');
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

    private javafx.scene.Node createDirectionNode(String item) {
        String[] mainParts = item.split(" -> ", 2);
        String direction = mainParts[0];
        String rest = mainParts.length > 1 ? mainParts[1] : "";
        
        String title = rest;
        String desc = "";
        int descIndex = rest.indexOf(" - ");
        if (descIndex != -1) {
            title = rest.substring(0, descIndex);
            desc = rest.substring(descIndex + 3);
        }
        
        HBox box = new HBox(8);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        box.setPadding(new javafx.geometry.Insets(4, 8, 4, 8));
        
        Label dirLabel = new Label(direction);
        dirLabel.getStyleClass().add("direction-badge");
        
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("direction-title");
        
        box.getChildren().addAll(dirLabel, titleLabel);
        
        if (!desc.isEmpty()) {
            Label descLabel = new Label("• " + desc);
            descLabel.getStyleClass().add("direction-desc");
            descLabel.setWrapText(true);
            box.getChildren().add(descLabel);
        }
        return box;
    }
}
