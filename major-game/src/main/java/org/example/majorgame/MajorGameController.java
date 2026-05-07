package org.example.majorgame;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.example.majorgame.game.GameEngine;
import org.example.majorgame.game.GameResult;
import org.example.majorgame.game.GameSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MajorGameController {
    private static final int STATUS_MAX = 100;
    private static final String ICON_PATH_PREFIX = "/org/example/majorgame/icons/";

    private final GameEngine engine = new GameEngine();
    private final List<String> commandHistory = new ArrayList<>();
    private final Map<String, Image> iconCache = new HashMap<>();
    private int historyIndex = 0;

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
    private TextArea outputArea;

    @FXML
    private TextField commandField;

    @FXML
    private void initialize() {
        appendResult(engine.openingScene());
        historyIndex = commandHistory.size();
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
        for (String message : result.messages()) {
            outputArea.appendText(message + System.lineSeparator());
        }
        outputArea.appendText(System.lineSeparator());
        renderState(result.snapshot());
    }

    private void renderState(GameSnapshot snapshot) {
        locationPromptLabel.setText(snapshot.locationPrompt());
        juiceLabel.setText("Apple juice: " + snapshot.appleJuice() + "/" + STATUS_MAX);
        honorLabel.setText("Honor: " + snapshot.honor() + "/" + STATUS_MAX);
        juiceBar.setProgress(normalizeStatus(snapshot.appleJuice()));
        honorBar.setProgress(normalizeStatus(snapshot.honor()));
        renderInventory(snapshot.inventory());
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
                .map(this::createInventoryItemNode)
                .forEach(inventoryIconsPane.getChildren()::add);
    }

    private VBox createInventoryItemNode(String itemId) {
        VBox card = new VBox(6);
        card.getStyleClass().add("inventory-item");

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
}
