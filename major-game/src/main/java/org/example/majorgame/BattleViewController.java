package org.example.majorgame;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.majorgame.game.GameEngine;
import org.example.majorgame.game.GameResult;
import org.example.majorgame.game.combat.CombatSnapshot;
import org.example.majorgame.game.combat.CombatTile;
import org.example.majorgame.game.combat.CombatUnitSnapshot;

import java.util.function.Consumer;

/**
 * Manages all combat/battle UI: the grid, action buttons, tile interactions, and status display.
 * Constructed by MajorGameController with the necessary FXML nodes passed in.
 */
public class BattleViewController {
    private static final double TILE_SIZE = 80.0;

    private final HBox battlePane;
    private final GridPane battleGrid;
    private final VBox battleActionBox;
    private final FlowPane battleActionsPane;
    private final Label battleStatusLabel;
    private final Button battleEndTurnButton;
    private final GameEngine engine;
    private final Consumer<GameResult> resultHandler;
    private final GameSoundController soundController;

    private CombatSnapshot currentSnapshot;

    public BattleViewController(HBox battlePane, GridPane battleGrid,
                                VBox battleActionBox, FlowPane battleActionsPane,
                                Label battleStatusLabel, Button battleEndTurnButton,
                                GameEngine engine, Consumer<GameResult> resultHandler,
                                GameSoundController soundController) {
        this.battlePane = battlePane;
        this.battleGrid = battleGrid;
        this.battleActionBox = battleActionBox;
        this.battleActionsPane = battleActionsPane;
        this.battleStatusLabel = battleStatusLabel;
        this.battleEndTurnButton = battleEndTurnButton;
        this.engine = engine;
        this.resultHandler = resultHandler;
        this.soundController = soundController;
    }

    /**
     * Shows the battle pane and renders the current combat state.
     */
    public void show(CombatSnapshot snapshot) {
        currentSnapshot = snapshot;
        battlePane.setVisible(true);
        battlePane.setManaged(true);

        if (battleStatusLabel.getText() == null || battleStatusLabel.getText().isBlank()) {
            battleStatusLabel.setText(snapshot.statusText());
        }
        battleEndTurnButton.setVisible(true);
        battleEndTurnButton.setManaged(true);
        battleEndTurnButton.setDisable(!snapshot.endTurnAvailable());

        renderGrid(snapshot);
        renderActions(snapshot);
    }

    /**
     * Hides the battle pane and clears combat state.
     */
    public void hide() {
        currentSnapshot = null;
        battlePane.setVisible(false);
        battlePane.setManaged(false);
        battleGrid.getChildren().clear();
        battleEndTurnButton.setVisible(false);
        battleEndTurnButton.setManaged(false);
    }

    /**
     * Delegates end-turn action to the engine and forwards the result.
     */
    public void onEndTurnClicked() {
        resultHandler.accept(engine.combatEndTurn());
    }

    /**
     * Updates the battle status label with the latest message from a game result.
     */
    public void updateStatus(GameResult result) {
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

    private void renderGrid(CombatSnapshot snapshot) {
        battleGrid.getChildren().clear();
        for (int row = 0; row < snapshot.rows(); row++) {
            for (int col = 0; col < snapshot.cols(); col++) {
                final int tileRow = row;
                final int tileCol = col;

                StackPane tile = new StackPane();
                tile.getStyleClass().add("battle-tile");
                tile.setMinSize(TILE_SIZE, TILE_SIZE);
                tile.setPrefSize(TILE_SIZE, TILE_SIZE);
                tile.setMaxSize(TILE_SIZE, TILE_SIZE);
                tile.setOnMouseClicked(event -> onTileClicked(tileRow, tileCol));

                CombatTile tileRef = new CombatTile(tileRow, tileCol);
                if (snapshot.reachableTiles().contains(tileRef)) {
                    tile.getStyleClass().add("battle-tile-reachable");
                }
                if (snapshot.targetableTiles().contains(tileRef)) {
                    tile.getStyleClass().add("battle-tile-targetable");
                }

                CombatUnitSnapshot unit = findUnitAt(snapshot, tileRow, tileCol);
                if (unit != null) {
                    tile.getChildren().add(createUnitNode(unit));
                }
                battleGrid.add(tile, tileCol, tileRow);
            }
        }
    }

    private VBox createUnitNode(CombatUnitSnapshot unit) {
        VBox unitNode = new VBox(2);
        unitNode.getStyleClass().add("battle-unit");
        unitNode.setAlignment(Pos.CENTER);

        if (unit.player()) {
            Label unitLabel = new Label("M");
            unitLabel.getStyleClass().add("battle-unit-player");
            unitNode.getChildren().add(unitLabel);
        } else {
            String imgName = unit.name().toLowerCase().replace(" ", "_") + "_combat.png";
            String imgPath = "images/" + imgName;
            try {
                Image img = new Image(getClass().getResourceAsStream(imgPath));
                ImageView imgView = new ImageView(img);
                imgView.setFitWidth(TILE_SIZE * 0.8);
                imgView.setFitHeight(TILE_SIZE * 0.8);
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

        return unitNode;
    }

    private void renderActions(CombatSnapshot snapshot) {
        battleActionBox.setVisible(true);
        battleActionBox.setManaged(true);
        battleActionsPane.getChildren().clear();

        boolean actionsEnabled = snapshot.playerTurn()
                && snapshot.selectedAbilityId() == null
                && !snapshot.turnLocked();

        for (String abilityId : snapshot.playerAbilityIds()) {
            Button abilityButton = new Button(UIUtils.formatItemName(abilityId));
            abilityButton.getStyleClass().add("battle-action-button");
            abilityButton.setDisable(!actionsEnabled);
            abilityButton.setOnAction(event ->
                    resultHandler.accept(engine.combatSelectAbility(abilityId))
            );
            battleActionsPane.getChildren().add(abilityButton);
        }
    }

    private void onTileClicked(int row, int col) {
        if (currentSnapshot == null) return;

        boolean isTargetingAbility = currentSnapshot.selectedAbilityId() != null;

        GameResult result = currentSnapshot.selectedAbilityId() == null
                ? engine.combatMovePlayer(row, col)
                : engine.combatTargetAt(row, col);

        if (isTargetingAbility) {
            soundController.playAttackSound();
        }

        resultHandler.accept(result);
    }

    private CombatUnitSnapshot findUnitAt(CombatSnapshot snapshot, int row, int col) {
        for (CombatUnitSnapshot unit : snapshot.units()) {
            if (unit.row() == row && unit.col() == col) {
                return unit;
            }
        }
        return null;
    }
}
