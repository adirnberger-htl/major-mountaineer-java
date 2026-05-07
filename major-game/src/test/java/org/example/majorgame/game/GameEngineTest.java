package org.example.majorgame;

import org.example.majorgame.game.GameEngine;
import org.example.majorgame.game.GameResult;
import org.example.majorgame.game.GameSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEngineTest {
    @Test
    void startsInMudHuzzWithFullJuice() {
        GameEngine engine = new GameEngine();

        GameSnapshot snapshot = engine.snapshot();

        assertEquals("mud_huzz", snapshot.locationId());
        assertEquals(100, snapshot.appleJuice());
        assertEquals(25, snapshot.honor());
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
    void diesAfterTooManyCommandsAtZeroJuice() {
        GameEngine engine = new GameEngine();
        for (int i = 0; i < 5; i++) {
            engine.executeCommand("n");
            engine.executeCommand("s");
        }

        assertEquals(0, engine.snapshot().appleJuice());
        assertTrue(engine.executeCommand("look").messages().stream().anyMatch(line -> line.contains("command(s) left")));

        GameResult deathTurn = engine.executeCommand("look");

        assertTrue(deathTurn.snapshot().gameOver());
        assertTrue(deathTurn.messages().stream().anyMatch(line -> line.contains("You died of death")));
    }
}
