package org.example.majorgame;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.majorgame.game.DialogueDefinition;
import org.example.majorgame.game.DialogueEngine;
import org.example.majorgame.game.GameEngine;
import org.example.majorgame.game.GameResult;

import java.util.List;
import java.util.function.Consumer;

/**
 * Controller for the dialogue overlay UI.
 * Dynamically builds a premium-styled modal to present dialogues and branching choices.
 */
public class DialogueViewController {

    private final StackPane rootStackPane;
    private final GameEngine engine;
    private final Consumer<GameResult> resultHandler;

    private VBox overlayContainer;
    private Label speakerLabel;
    private Label dialogueTextLabel;
    private VBox choicesContainer;

    public DialogueViewController(StackPane rootStackPane, GameEngine engine, Consumer<GameResult> resultHandler) {
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
                "-fx-background-color: rgba(6, 12, 20, 0.88);" +
                "-fx-background-radius: 0;"
        );
        overlayContainer.setVisible(false);
        overlayContainer.setManaged(false);

        // Dialogue Card Container
        VBox dialogCard = new VBox(20);
        dialogCard.setMaxWidth(650);
        dialogCard.setAlignment(Pos.TOP_LEFT);
        dialogCard.setPadding(new Insets(28));
        dialogCard.setStyle(
                "-fx-background-color: rgba(14, 25, 38, 0.95);" +
                "-fx-border-color: rgba(192, 215, 230, 0.45);" +
                "-fx-border-radius: 12;" +
                "-fx-background-radius: 12;" +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.65), 15, 0, 0, 8);"
        );

        // Speaker Badge
        speakerLabel = new Label("SPEAKER");
        speakerLabel.setStyle(
                "-fx-text-fill: #FFAA00;" +
                "-fx-font-size: 20px;" +
                "-fx-font-weight: 800;" +
                "-fx-padding: 0 0 6 0;" +
                "-fx-border-color: transparent transparent rgba(192, 215, 230, 0.2) transparent;" +
                "-fx-border-width: 0 0 1 0;"
        );
        speakerLabel.setMaxWidth(Double.MAX_VALUE);

        // Speech Text
        dialogueTextLabel = new Label("Dialogue text goes here...");
        dialogueTextLabel.setWrapText(true);
        dialogueTextLabel.setStyle(
                "-fx-text-fill: #e7f0f7;" +
                "-fx-font-size: 16px;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-line-spacing: 4;"
        );
        dialogueTextLabel.setMinHeight(60);

        // Choices list
        choicesContainer = new VBox(10);
        choicesContainer.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(choicesContainer, Priority.ALWAYS);

        dialogCard.getChildren().addAll(speakerLabel, dialogueTextLabel, choicesContainer);
        overlayContainer.getChildren().add(dialogCard);

        // Add to the main StackPane
        rootStackPane.getChildren().add(overlayContainer);
    }

    public void show() {
        overlayContainer.setVisible(true);
        overlayContainer.setManaged(true);
        overlayContainer.toFront();
        render();
    }

    public void hide() {
        overlayContainer.setVisible(false);
        overlayContainer.setManaged(false);
    }

    public void render() {
        DialogueEngine activeDialogue = engine.getActiveDialogue();
        if (activeDialogue == null) {
            hide();
            return;
        }

        DialogueDefinition.DialogueNode node = activeDialogue.getCurrentNode();
        if (node == null) {
            hide();
            return;
        }

        speakerLabel.setText(node.speaker().toUpperCase());
        dialogueTextLabel.setText(node.text());

        choicesContainer.getChildren().clear();
        List<DialogueEngine.DialogueChoiceStatus> choices = activeDialogue.getChoicesStatus();

        if (choices.isEmpty()) {
            // If it's a terminal node but dialogue is somehow still active, show a Close button
            Button closeBtn = new Button("Continue");
            closeBtn.getStyleClass().add("command-button");
            closeBtn.setMaxWidth(Double.MAX_VALUE);
            closeBtn.setStyle("-fx-font-size: 15px; -fx-padding: 8 16;");
            closeBtn.setOnAction(e -> {
                // Exit dialogue in engine
                resultHandler.accept(engine.executeCommand("exit"));
            });
            choicesContainer.getChildren().add(closeBtn);
        } else {
            for (int i = 0; i < choices.size(); i++) {
                final int index = i;
                DialogueEngine.DialogueChoiceStatus choiceStatus = choices.get(index);
                DialogueDefinition.DialogueChoice choice = choiceStatus.choice();

                Button choiceBtn = new Button((index + 1) + ". " + choice.text());
                choiceBtn.setMaxWidth(Double.MAX_VALUE);
                choiceBtn.setAlignment(Pos.CENTER_LEFT);
                choiceBtn.setWrapText(true);

                if (choiceStatus.allowed()) {
                    choiceBtn.getStyleClass().add("button");
                    choiceBtn.setStyle("-fx-font-size: 14px; -fx-padding: 10 16;");
                    choiceBtn.setOnAction(e -> {
                        // Submit choice as command
                        GameResult res = engine.executeCommand(String.valueOf(index + 1));
                        resultHandler.accept(res);
                    });
                } else {
                    choiceBtn.setDisable(true);
                    choiceBtn.setStyle(
                            "-fx-background-color: rgba(30, 20, 20, 0.4);" +
                            "-fx-text-fill: rgba(231, 240, 247, 0.35);" +
                            "-fx-border-color: rgba(255, 68, 68, 0.25);" +
                            "-fx-border-radius: 8;" +
                            "-fx-font-size: 14px;" +
                            "-fx-padding: 10 16;"
                    );

                    Label lockLabel = new Label("Locked: " + choiceStatus.failReason());
                    lockLabel.setStyle("-fx-text-fill: #FF6666; -fx-font-size: 12px; -fx-font-style: italic; -fx-padding: 2 0 8 16;");

                    VBox choiceBox = new VBox(2);
                    choiceBox.getChildren().addAll(choiceBtn, lockLabel);
                    choicesContainer.getChildren().add(choiceBox);
                    continue; // Skip direct add of button
                }

                choicesContainer.getChildren().add(choiceBtn);
            }
        }
    }
}
