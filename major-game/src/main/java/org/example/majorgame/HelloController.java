package org.example.majorgame;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.example.majorgame.game.GameEngine;
import org.example.majorgame.game.GameResult;
import org.example.majorgame.game.GameSnapshot;

public class HelloController {
    private final GameEngine engine = new GameEngine();

    @FXML
    private Label locationLabel;

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
        appendResult(engine.start());
    }

    @FXML
    protected void onSubmitCommand() {
        String input = commandField.getText();
        if (input == null || input.isBlank()) {
            return;
        }

        appendResult(engine.executeCommand(input));
        commandField.clear();
    }

    private void appendResult(GameResult result) {
        for (String message : result.messages()) {
            outputArea.appendText(message + System.lineSeparator());
        }
        outputArea.appendText(System.lineSeparator());
        renderState(result.snapshot());
    }

    private void renderState(GameSnapshot snapshot) {
        locationLabel.setText("Location: " + snapshot.locationTitle() + " (" + snapshot.locationId() + ")");
        juiceLabel.setText("Apple juice: " + snapshot.appleJuice());
        honorLabel.setText("Honor: " + snapshot.honor());
        inventoryLabel.setText(
                "Inventory: " + (snapshot.inventory().isEmpty() ? "(empty)" : String.join(", ", snapshot.inventory()))
        );
        juiceBar.setProgress(snapshot.appleJuice() / 100.0);
    }
}
