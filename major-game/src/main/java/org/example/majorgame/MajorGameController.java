package org.example.majorgame;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.example.majorgame.game.GameEngine;
import org.example.majorgame.game.GameResult;
import org.example.majorgame.game.GameSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main FXML controller — acts as an orchestrator.
 * Delegates combat UI to {@link BattleViewController},
 * exploration UI to {@link ExplorationViewController},
 * and sound triggers to {@link GameSoundController}.
 */
public class MajorGameController {
    private static final int STATUS_MAX = 100;
    private static final String HOHE_DIRN_BACKDROP_CLASS = "hohe-dirn-bg";

    private final GameEngine engine = new GameEngine();
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = 0;

    private PauseTransition notificationTimer;

    // ── Sub-controllers ─────────────────────────────────────────────────────
    private BattleViewController battleView;
    private ExplorationViewController explorationView;
    private GameSoundController gameSoundController;

    // ── FXML nodes ──────────────────────────────────────────────────────────

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
    @FXML private Label locationPromptLabel;
    @FXML private ListView outputArea;

    // ── Initialization ──────────────────────────────────────────────────────

    @FXML
    private void initialize() {
        gameSoundController = new GameSoundController(new SoundManager());
        gameSoundController.playInitialMusic();

        battleView = new BattleViewController(
                battlePane, battleGrid, battleActionBox, battleActionsPane,
                battleStatusLabel, battleEndTurnButton,
                engine, this::appendResult, gameSoundController
        );

        explorationView = new ExplorationViewController(
                topPanel, descriptionContainer, commandPanel, inventoryPanel, outputArea,
                locationTitleLabel, locationPromptLabel, descriptionArea,
                directionsListContainer, inventoryIconsPane, groundItemsPane,
                dialogListView, engine, this::appendResult
        );
        explorationView.setup();

        appendResult(engine.openingScene());
        historyIndex = commandHistory.size();

        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.TAB) {
                        if (!commandField.isFocused() && !instructionsOverlay.isVisible()) {
                            commandField.requestFocus();
                            event.consume();
                        }
                    }
                });
            }
        });
    }

    // ── Command Input ───────────────────────────────────────────────────────

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

    // ── Instructions Overlay ────────────────────────────────────────────────

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

    // ── Battle Delegate ─────────────────────────────────────────────────────

    @FXML
    protected void onBattleEndTurnClicked() {
        battleView.onEndTurnClicked();
    }

    // ── Result Handling & Rendering ─────────────────────────────────────────

    private void appendResult(GameResult result) {
        String messages = result.messages().stream()
                .filter(m -> !m.isBlank())
                .collect(Collectors.joining("\n"));
        if (!messages.isEmpty()) {
            showNotification(messages);
        }
        battleView.updateStatus(result);
        renderState(result.snapshot());
    }

    private void renderState(GameSnapshot snapshot) {
        gameSoundController.onLocationChanged(snapshot.locationId());

        // Status bars (always visible, regardless of mode)
        juiceLabel.setText("Apple juice: " + snapshot.appleJuice() + "/" + STATUS_MAX);
        honorLabel.setText("Honor: " + snapshot.honor() + "/" + STATUS_MAX);
        juiceBar.setProgress(normalizeStatus(snapshot.appleJuice()));
        honorBar.setProgress(normalizeStatus(snapshot.honor()));

        // Exploration content (directions, description, dialog, items)
        explorationView.render(snapshot);

        updateBackdrop(snapshot.locationId());
        renderMode(snapshot);
    }

    private void renderMode(GameSnapshot snapshot) {
        boolean inCombat = snapshot.inCombat() && snapshot.combatSnapshot() != null;
        gameSoundController.onCombatStateChanged(inCombat, snapshot.freshRespawn());

        if (inCombat) {
            explorationView.hide();
            battleView.show(snapshot.combatSnapshot());
        } else {
            battleView.hide();
            explorationView.show();
        }
    }

    // ── Notifications ───────────────────────────────────────────────────────

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

    // ── Helpers ─────────────────────────────────────────────────────────────

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

    private double normalizeStatus(int value) {
        int clamped = Math.max(0, Math.min(STATUS_MAX, value));
        return clamped / (double) STATUS_MAX;
    }

    private String normalizeCommand(String command) {
        String value = command.trim().toLowerCase();
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }
}
