package org.example.majorgame;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import org.example.majorgame.game.Direction;
import org.example.majorgame.game.GameEngine;
import org.example.majorgame.game.GameResult;
import org.example.majorgame.game.GameSnapshot;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Manages all exploration/normal-mode UI: directions, description, dialog,
 * inventory, ground items, and drag-and-drop interactions.
 * Constructed by MajorGameController with the necessary FXML nodes passed in.
 */
public class ExplorationViewController {

    private final VBox topPanel;
    private final VBox descriptionContainer;
    private final HBox commandPanel;
    private final HBox inventoryPanel;
    private final ListView<?> outputArea;
    private final Label locationTitleLabel;
    private final Label locationPromptLabel;
    private final Label descriptionArea;
    private final VBox directionsListContainer;
    private final FlowPane inventoryIconsPane;
    private final FlowPane groundItemsPane;
    private final ListView<String> dialogListView;
    private final GameEngine engine;
    private final Consumer<GameResult> resultHandler;
    private final Map<String, Image> iconCache = new HashMap<>();

    public ExplorationViewController(
            VBox topPanel, VBox descriptionContainer,
            HBox commandPanel, HBox inventoryPanel, ListView<?> outputArea,
            Label locationTitleLabel, Label locationPromptLabel, Label descriptionArea,
            VBox directionsListContainer,
            FlowPane inventoryIconsPane, FlowPane groundItemsPane,
            ListView<String> dialogListView,
            GameEngine engine, Consumer<GameResult> resultHandler) {
        this.topPanel = topPanel;
        this.descriptionContainer = descriptionContainer;
        this.commandPanel = commandPanel;
        this.inventoryPanel = inventoryPanel;
        this.outputArea = outputArea;
        this.locationTitleLabel = locationTitleLabel;
        this.locationPromptLabel = locationPromptLabel;
        this.descriptionArea = descriptionArea;
        this.directionsListContainer = directionsListContainer;
        this.inventoryIconsPane = inventoryIconsPane;
        this.groundItemsPane = groundItemsPane;
        this.dialogListView = dialogListView;
        this.engine = engine;
        this.resultHandler = resultHandler;
    }

    /**
     * Initializes drag-and-drop handlers and the dialog list cell factory.
     * Call once after construction.
     */
    public void setup() {
        setupDragAndDrop();
        setupDialogListView();
    }

    /**
     * Shows all exploration-mode panels.
     */
    public void show() {
        setExplorationVisible(true);
    }

    /**
     * Hides all exploration-mode panels.
     */
    public void hide() {
        setExplorationVisible(false);
    }

    /**
     * Renders all exploration UI elements from the given game snapshot.
     */
    public void render(GameSnapshot snapshot) {
        locationPromptLabel.setText(snapshot.locationPrompt());
        locationTitleLabel.setText(snapshot.locationTitle().toUpperCase());

        renderDirections(snapshot.availableDirections());
        descriptionArea.setText(String.join(System.lineSeparator(), snapshot.locationDescription()));

        dialogListView.getItems().clear();
        dialogListView.getItems().addAll(snapshot.locationDialog());

        renderInventory(snapshot.inventory());
        renderGroundItems(snapshot.visibleItems());
    }

    // ── Visibility ──────────────────────────────────────────────────────────

    private void setExplorationVisible(boolean visible) {
        outputArea.setVisible(visible);
        outputArea.setManaged(visible);
        topPanel.setVisible(visible);
        topPanel.setManaged(visible);
        descriptionContainer.setVisible(visible);
        descriptionContainer.setManaged(visible);
        commandPanel.setVisible(visible);
        commandPanel.setManaged(visible);
        inventoryPanel.setVisible(visible);
        inventoryPanel.setManaged(visible);
    }

    // ── Directions ──────────────────────────────────────────────────────────

    private void renderDirections(Map<Direction, String> directions) {
        directionsListContainer.getChildren().clear();
        for (Map.Entry<Direction, String> entry : directions.entrySet()) {
            String item = entry.getKey().name() + " -> " + entry.getValue();
            directionsListContainer.getChildren().add(createDirectionNode(item));
        }
    }

    private Node createDirectionNode(String item) {
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
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(4, 8, 4, 8));

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

    // ── Inventory & Ground Items ────────────────────────────────────────────

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

        items.forEach(itemId ->
                groundItemsPane.getChildren().add(createInventoryItemNode(itemId, false))
        );
    }

    private VBox createInventoryItemNode(String itemId, boolean isInventory) {
        VBox card = new VBox(6);
        card.getStyleClass().add("inventory-item");

        card.setOnDragDetected(event -> {
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(itemId);
            db.setContent(content);
            event.consume();
        });

        Image itemIcon = UIUtils.loadItemIcon(itemId, iconCache);
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

        Label nameLabel = new Label(UIUtils.formatItemName(itemId));
        nameLabel.getStyleClass().add("inventory-item-label");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(96);
        card.getChildren().add(nameLabel);

        Button actionBtn = new Button(isInventory ? "Drop" : "Take");
        actionBtn.getStyleClass().add("command-button");
        actionBtn.setOnAction(e -> {
            if (isInventory) {
                resultHandler.accept(engine.drop(itemId));
            } else {
                resultHandler.accept(engine.take(itemId));
            }
        });
        card.getChildren().add(actionBtn);

        return card;
    }

    // ── Drag and Drop ───────────────────────────────────────────────────────

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
                resultHandler.accept(engine.take(itemId));
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
                resultHandler.accept(engine.drop(itemId));
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    // ── Dialog List Cell Factory ────────────────────────────────────────────

    private void setupDialogListView() {
        dialogListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setGraphic(buildDialogCell(item));
                }
            }
        });
    }

    private HBox buildDialogCell(String item) {
        HBox box = new HBox(12);
        Label msgLabel = new Label();
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(400);
        msgLabel.getStyleClass().add("dialog-message");

        if (item.startsWith("SYSTEM: ")) {
            msgLabel.setText(item.substring(8).trim());
            msgLabel.getStyleClass().add("dialog-system");
            box.setAlignment(Pos.CENTER);
            box.getChildren().add(msgLabel);
        } else if (item.startsWith("Major: ")) {
            msgLabel.setText(item.substring(7).trim());
            msgLabel.getStyleClass().add("dialog-major");
            box.setAlignment(Pos.CENTER_RIGHT);
            box.getChildren().add(msgLabel);
        } else {
            msgLabel.getStyleClass().add("dialog-npc");
            box.setAlignment(Pos.CENTER_LEFT);

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
                        Image avatarImg = new Image(getClass().getResourceAsStream(avatarPath));
                        ImageView avatarView = new ImageView(avatarImg);
                        avatarView.setFitWidth(44);
                        avatarView.setFitHeight(44);

                        Circle clip = new Circle(22, 22, 22);
                        avatarView.setClip(clip);

                        VBox avatarContainer = new VBox(avatarView);
                        avatarContainer.setAlignment(Pos.TOP_CENTER);

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

        return box;
    }
}
