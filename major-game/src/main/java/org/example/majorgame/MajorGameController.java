package org.example.majorgame;

import javafx.fxml.FXML;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.example.majorgame.game.GameEngine;
import org.example.majorgame.game.GameResult;
import org.example.majorgame.game.GameSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MajorGameController {
    private final GameEngine engine = new GameEngine();
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = 0;

    @FXML
    private Label locationPromptLabel;

    @FXML
    private Label juiceLabel;

    @FXML
    private Label honorLabel;

    @FXML
    private Label inventoryLabel;

    @FXML
    private ProgressBar juiceBar;

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

        commandHistory.add(input);
        historyIndex = commandHistory.size();
        appendResult(engine.executeCommand(input));
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
        juiceLabel.setText("Apple juice: " + snapshot.appleJuice());
        honorLabel.setText("Honor: " + snapshot.honor());
        inventoryLabel.setText(
                "Inventory: " + (snapshot.inventory().isEmpty() ? "(empty)" : String.join(", ", snapshot.inventory()))
        );
        juiceBar.setProgress(snapshot.appleJuice() / 100.0);
    }
}
