package org.example.majorgame;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.majorgame.game.BalanceBarChallengeEngine;
import org.example.majorgame.game.ChallengeEngine;
import org.example.majorgame.game.GameEngine;
import org.example.majorgame.game.GameResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controller for the Balance Bar mini-game overlay.
 * Uses a JavaFX AnimationTimer to run a ~60fps physics simulation loop.
 */
public class ChallengeViewController {

    private final StackPane rootStackPane;
    private final GameEngine engine;
    private final Consumer<GameResult> resultHandler;

    private VBox overlayContainer;
    private Label difficultyLabel;
    private Label instructionLabel;
    private Pane trackPane;
    private Pane safeZoneNode;
    private Pane playerIndicatorNode;
    private ProgressBar timeProgressBar;
    private Label progressLabel;
    private HBox buttonBox;

    private AnimationTimer timer;
    private long lastTime = 0;

    public ChallengeViewController(StackPane rootStackPane, GameEngine engine, Consumer<GameResult> resultHandler) {
        this.rootStackPane = rootStackPane;
        this.engine = engine;
        this.resultHandler = resultHandler;
        buildUI();
    }

    private void buildUI() {
        // Semi-transparent dark backdrop overlay
        overlayContainer = new VBox(24);
        overlayContainer.setAlignment(Pos.CENTER);
        overlayContainer.setPadding(new Insets(32));
        overlayContainer.setStyle(
                "-fx-background-color: rgba(6, 12, 20, 0.9);" +
                "-fx-background-radius: 0;"
        );
        overlayContainer.setVisible(false);
        overlayContainer.setManaged(false);

        // Challenge Card Container
        VBox challengeCard = new VBox(24);
        challengeCard.setMaxWidth(600);
        challengeCard.setAlignment(Pos.CENTER);
        challengeCard.setPadding(new Insets(28));
        challengeCard.setStyle(
                "-fx-background-color: rgba(14, 25, 38, 0.95);" +
                "-fx-border-color: #8fcf78;" +
                "-fx-border-radius: 12;" +
                "-fx-background-radius: 12;" +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.65), 15, 0, 0, 8);"
        );

        // Title
        Label titleLabel = new Label("BALANCE CHALLENGE");
        titleLabel.setStyle(
                "-fx-text-fill: #e7f0f7;" +
                "-fx-font-size: 22px;" +
                "-fx-font-weight: 800;" +
                "-fx-alignment: center;"
        );

        // Difficulty Badge
        difficultyLabel = new Label("DIFFICULTY: MEDIUM");
        difficultyLabel.setStyle(
                "-fx-text-fill: #8fcf78;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: 700;" +
                "-fx-background-color: rgba(143, 207, 120, 0.15);" +
                "-fx-padding: 4 10 4 10;" +
                "-fx-background-radius: 4;"
        );

        instructionLabel = new Label("Keep the indicator in the green safe zone!\nUse A/D, Left/Right arrows, or the buttons below to balance.");
        instructionLabel.setWrapText(true);
        instructionLabel.setStyle(
                "-fx-text-fill: #d8e8f5;" +
                "-fx-font-size: 14px;" +
                "-fx-text-alignment: center;"
        );

        // Track and Cursor Pane
        trackPane = new Pane();
        trackPane.setPrefSize(500, 40);
        trackPane.setMinSize(500, 40);
        trackPane.setMaxSize(500, 40);
        trackPane.setStyle(
                "-fx-background-color: rgba(10, 18, 27, 0.6);" +
                "-fx-border-color: rgba(192, 215, 230, 0.3);" +
                "-fx-border-radius: 20;" +
                "-fx-background-radius: 20;"
        );

        // Safe Zone Node
        safeZoneNode = new Pane();
        safeZoneNode.setStyle(
                "-fx-background-color: rgba(143, 207, 120, 0.35);" +
                "-fx-border-color: rgba(143, 207, 120, 0.7);" +
                "-fx-border-width: 0 1 0 1;"
        );

        // Player indicator (cursor)
        playerIndicatorNode = new Pane();
        playerIndicatorNode.setPrefSize(18, 32);
        playerIndicatorNode.setMinSize(18, 32);
        playerIndicatorNode.setMaxSize(18, 32);
        playerIndicatorNode.setStyle(
                "-fx-background-color: #FFAA00;" +
                "-fx-background-radius: 9;" +
                "-fx-effect: dropshadow(three-pass-box, #FFAA00, 8, 0.5, 0, 0);"
        );

        trackPane.getChildren().addAll(safeZoneNode, playerIndicatorNode);

        // Progress bar for holding time
        VBox progressBox = new VBox(8);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(10, 0, 0, 0));

        timeProgressBar = new ProgressBar(0.0);
        timeProgressBar.setPrefWidth(500);
        timeProgressBar.getStyleClass().add("juice-bar"); // uses green coloring from template
        timeProgressBar.setStyle("-fx-accent: #8fcf78;");

        progressLabel = new Label("Time Held: 0.0s / 5.0s");
        progressLabel.setStyle("-fx-text-fill: #e7f0f7; -fx-font-size: 14px; -fx-font-weight: 600;");

        progressBox.getChildren().addAll(progressLabel, timeProgressBar);

        // Steer Buttons
        buttonBox = new HBox(20);
        buttonBox.setAlignment(Pos.CENTER);

        Button steerLeftBtn = new Button("◀ Steer Left");
        steerLeftBtn.getStyleClass().add("button");
        steerLeftBtn.setStyle("-fx-padding: 8 20; -fx-font-size: 14px;");
        steerLeftBtn.setOnAction(e -> applyLeftImpulse());

        Button steerRightBtn = new Button("Steer Right ▶");
        steerRightBtn.getStyleClass().add("button");
        steerRightBtn.setStyle("-fx-padding: 8 20; -fx-font-size: 14px;");
        steerRightBtn.setOnAction(e -> applyRightImpulse());

        buttonBox.getChildren().addAll(steerLeftBtn, steerRightBtn);

        challengeCard.getChildren().addAll(titleLabel, difficultyLabel, instructionLabel, trackPane, progressBox, buttonBox);
        overlayContainer.getChildren().add(challengeCard);

        // Add to rootStackPane
        rootStackPane.getChildren().add(overlayContainer);

        // Setup keyboard key press handlers when overlay is shown
        overlayContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
            }
        });
    }

    private void handleKeyPress(KeyEvent event) {
        if (!overlayContainer.isVisible()) return;

        KeyCode code = event.getCode();
        if (code == KeyCode.A || code == KeyCode.LEFT || code == KeyCode.W || code == KeyCode.L) {
            applyLeftImpulse();
            event.consume();
        } else if (code == KeyCode.D || code == KeyCode.RIGHT || code == KeyCode.E || code == KeyCode.R) {
            applyRightImpulse();
            event.consume();
        }
    }

    private void applyLeftImpulse() {
        ChallengeEngine active = engine.getActiveChallenge();
        if (active != null) {
            active.applyImpulse(-0.16); // slightly higher impulse for better manual feedback
        }
    }

    private void applyRightImpulse() {
        ChallengeEngine active = engine.getActiveChallenge();
        if (active != null) {
            active.applyImpulse(0.16);
        }
    }

    public void show() {
        overlayContainer.setVisible(true);
        overlayContainer.setManaged(true);
        overlayContainer.toFront();

        ChallengeEngine active = engine.getActiveChallenge();
        if (active != null) {
            if (active instanceof BalanceBarChallengeEngine balance) {
                difficultyLabel.setText("DIFFICULTY: " + balance.getTimeNeeded() + "s HOLD REQUIRED");
            } else {
                difficultyLabel.setText("CHALLENGE ACTIVE");
            }
            renderStaticProperties(active);
        }

        startTimerLoop();
    }

    public void hide() {
        stopTimerLoop();
        overlayContainer.setVisible(false);
        overlayContainer.setManaged(false);
    }

    private void renderStaticProperties(ChallengeEngine active) {
        // Position safe zone
        double start = active.getTargetRegionStart();
        double end = active.getTargetRegionEnd();
        double width = (end - start) * 500;
        double x = start * 500;

        safeZoneNode.setLayoutX(x);
        safeZoneNode.setPrefWidth(width);
        safeZoneNode.setMinWidth(width);
        safeZoneNode.setMaxWidth(width);
        safeZoneNode.setPrefHeight(40);
    }

    private void startTimerLoop() {
        if (timer != null) {
            timer.stop();
        }

        lastTime = System.nanoTime();
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double elapsedSeconds = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;

                // Clamp delta time to avoid physics exploding during lag spikes
                if (elapsedSeconds > 0.1) {
                    elapsedSeconds = 0.1;
                }

                updateSimulation(elapsedSeconds);
            }
        };
        timer.start();
    }

    private void stopTimerLoop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    private void updateSimulation(double dt) {
        ChallengeEngine active = engine.getActiveChallenge();
        if (active == null) {
            hide();
            return;
        }

        // Update physics
        active.update(dt);

        // Update indicator UI
        double pos = active.getPlayerPosition();
        double cursorX = (pos * 500) - (playerIndicatorNode.getPrefWidth() / 2.0);
        playerIndicatorNode.setLayoutX(Math.max(0, Math.min(500 - playerIndicatorNode.getPrefWidth(), cursorX)));
        playerIndicatorNode.setLayoutY(4);

        if (active instanceof BalanceBarChallengeEngine balance) {
            double held = balance.getTimeElapsed();
            double needed = balance.getTimeNeeded();
            timeProgressBar.setProgress(Math.min(1.0, held / needed));
            progressLabel.setText(String.format("Time Held: %.1fs / %.1fs", held, needed));
        }

        // Check completion
        if (active.isFinished()) {
            stopTimerLoop();
            completeChallenge();
        }
    }

    private void completeChallenge() {
        List<String> messages = new ArrayList<>();
        engine.handleChallengeCompletion(messages);
        GameResult result = new GameResult(messages, engine.snapshot());
        resultHandler.accept(result);
        hide();
    }
}
