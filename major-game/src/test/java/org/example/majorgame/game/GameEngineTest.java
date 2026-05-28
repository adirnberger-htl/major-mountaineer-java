package org.example.majorgame;

import org.example.majorgame.game.GameEngine;
import org.example.majorgame.game.GameResult;
import org.example.majorgame.game.GameSnapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEngineTest {
    @BeforeAll
    static void disablePersistence() {
        System.setProperty("majorgame.persistence", "disabled");
    }

    @Test
    void startsInMudHuzzWithFullJuice() {
        GameEngine engine = new GameEngine();

        GameSnapshot snapshot = engine.snapshot();

        assertEquals("mud_huzz", snapshot.locationId());
        assertEquals(100, snapshot.appleJuice());
        assertEquals(25, snapshot.honor());
        assertTrue(snapshot.locationPrompt().contains("Mud Huzz"));
    }

    @Test
    void moveDrainsJuiceByTen() {
        GameEngine engine = new GameEngine();

        GameResult result = engine.executeCommand("e");

        assertEquals("storage_building", result.snapshot().locationId());
        assertEquals(90, result.snapshot().appleJuice());
    }

    @Test
    void canTakeAndDrinkAppleJuiceToRefill() {
        GameEngine engine = new GameEngine();
        engine.executeCommand("e");
        engine.executeCommand("take apple_juice");
        engine.executeCommand("w");
        engine.executeCommand("n");
        engine.executeCommand("s");
        engine.executeCommand("n");

        GameResult result = engine.executeCommand("drink");

        assertEquals(100, result.snapshot().appleJuice());
        assertTrue(result.snapshot().inventory().isEmpty());
    }

    @Test
    void reachingTraunsteinAutoRoutesToHoheDirn() {
        GameEngine engine = new GameEngine();
        engine.executeCommand("n");

        GameResult result = engine.executeCommand("n");

        assertEquals("hohe_dirn", result.snapshot().locationId());
        assertTrue(result.messages().stream().anyMatch(line -> line.contains("fell asleep")));
    }

    @Test
    void respawnsAtCheckpointAfterTooManyCommandsAtZeroJuice() {
        GameEngine engine = new GameEngine();
        for (int i = 0; i < 5; i++) {
            engine.executeCommand("n");
            engine.executeCommand("s");
        }

        assertEquals(0, engine.snapshot().appleJuice());
        assertTrue(engine.executeCommand("instructions").messages().stream().anyMatch(line -> line.contains("command(s) left")));

        GameResult deathTurn = engine.executeCommand("instructions");

        assertEquals("mud_huzz", deathTurn.snapshot().locationId());
        assertEquals(100, deathTurn.snapshot().appleJuice());
        assertTrue(deathTurn.messages().stream().anyMatch(line -> line.contains("You died of death")));
        assertTrue(deathTurn.messages().stream().anyMatch(line -> line.contains("Respawned at checkpoint")));
    }

    @Test
    void startJuicyLookAndRouteCommandsAreRemoved() {
        GameEngine engine = new GameEngine();

        GameResult start = engine.executeCommand("start");
        GameResult juicy = engine.executeCommand("juicy");
        GameResult look = engine.executeCommand("look");
        GameResult route = engine.executeCommand("route([n,e])");

        assertEquals(List.of("Unknown command."), start.messages());
        assertEquals(List.of("Unknown command."), juicy.messages());
        assertEquals(List.of("Unknown command."), look.messages());
        assertEquals(List.of("Unknown command."), route.messages());
    }

    @Test
    void dieCommandShowsStatsAndRespawnsAtCheckpoint() {
        GameEngine engine = new GameEngine();
        engine.executeCommand("n");
        engine.executeCommand("s");

        GameResult dieResult = engine.executeCommand("die");

        assertEquals("mud_huzz", dieResult.snapshot().locationId());
        assertEquals(100, dieResult.snapshot().appleJuice());
        assertTrue(dieResult.snapshot().gameOver());
        assertTrue(dieResult.messages().stream().anyMatch(line -> line.contains("[Stats]")));
        assertTrue(dieResult.messages().stream().anyMatch(line -> line.contains("Apple juices drunk")));
        assertTrue(dieResult.messages().stream().anyMatch(line -> line.contains("Respawned at checkpoint")));
    }

    @Test
    void checkCommandSetsCheckpointAndRespawnsThere() {
        GameEngine engine = new GameEngine();
        engine.executeCommand("n");

        GameResult checkResult = engine.executeCommand("check");
        GameResult dieResult = engine.executeCommand("die");

        assertTrue(checkResult.messages().stream().anyMatch(line -> line.contains("Checkpoint set at Train Station.")));
        assertEquals("train_station", dieResult.snapshot().locationId());
    }

    @Test
    void checkCommandIsRejectedOutsideAllowedCheckpointAreas() {
        GameEngine engine = new GameEngine();
        engine.executeCommand("n");
        engine.executeCommand("check");
        engine.executeCommand("n");
        assertEquals("hohe_dirn", engine.snapshot().locationId());

        GameResult notAllowedResult = engine.executeCommand("check");

        assertTrue(notAllowedResult.messages().stream().anyMatch(line -> line.contains("You cannot set a checkpoint here.")));
    }

    @Test
    void enteringSlumsStartsCombatWithOldMan() {
        GameEngine engine = new GameEngine();
        engine.executeCommand("n");
        engine.executeCommand("e");

        GameResult result = engine.executeCommand("n");

        assertTrue(result.snapshot().inCombat());
        assertTrue(result.messages().stream().anyMatch(line -> line.contains("Old Man")));
        assertTrue(result.snapshot().combatSnapshot().playerAbilityIds().contains("punch"));
    }

    @Test
    void endTurnCanBeUsedAnytimeDuringCombat() {
        GameEngine engine = new GameEngine();
        engine.executeCommand("n");
        engine.executeCommand("e");
        engine.executeCommand("n");

        engine.combatSelectAbility("punch");
        GameResult endTurnResult = engine.combatEndTurn();

        assertTrue(endTurnResult.snapshot().inCombat());
        assertNull(endTurnResult.snapshot().combatSnapshot().selectedAbilityId());
        assertTrue(endTurnResult.messages().stream().anyMatch(line -> line.contains("Ability selection canceled.")));
    }
}
