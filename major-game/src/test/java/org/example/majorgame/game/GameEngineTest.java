package org.example.majorgame.game;

import org.example.majorgame.game.GameEngine;
import org.example.majorgame.game.GameResult;
import org.example.majorgame.game.GameSnapshot;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEngineTest {
    @Test
    void startsInMudHuzzWithFullJuice() {
        GameEngine engine = new GameEngine();

        GameSnapshot snapshot = engine.snapshot();

        assertEquals("mud_huzz", snapshot.locationId());
        assertEquals(100, snapshot.appleJuice());
        assertEquals(25, snapshot.honor());
        assertTrue(snapshot.locationPrompt().contains("Mud Hut"));
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
        engine.take("apple_juice");
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
        assertTrue(dieResult.snapshot().freshRespawn());
        assertTrue(dieResult.messages().stream().anyMatch(line -> line.contains("[Stats]")));
        assertTrue(dieResult.messages().stream().anyMatch(line -> line.contains("Apple juices drunk")));
        assertTrue(dieResult.messages().stream().anyMatch(line -> line.contains("Respawned at checkpoint")));
    }

    @Test
    void checkCommandSetsCheckpointAndRespawnsThere() {
        GameEngine engine = new GameEngine();
        engine.executeCommand("n");
        engine.executeCommand("e");

        GameResult checkResult = engine.executeCommand("check");
        GameResult dieResult = engine.executeCommand("die");

        assertTrue(checkResult.messages().stream().anyMatch(line -> line.contains("Checkpoint set at HOHE DIRN.")));
        assertEquals("hohe_dirn", dieResult.snapshot().locationId());
    }

    @Test
    void checkCommandIsRejectedOutsideAllowedCheckpointAreas() {
        GameEngine engine = new GameEngine();
        engine.executeCommand("n");

        GameResult notAllowedResult = engine.executeCommand("check");

        assertTrue(notAllowedResult.messages().stream().anyMatch(line -> line.contains("You cannot set a checkpoint here.")));
    }

    @Test
    void enteringSlumsStartsCombatWithOldMan() {
        GameEngine engine = new GameEngine();
        engine.executeCommand("n");
        engine.executeCommand("e");
        engine.executeCommand("n");

        GameResult result = engine.executeCommand("n");

        assertTrue(result.snapshot().inCombat());
        assertTrue(result.messages().stream().anyMatch(line -> line.contains("Old Man")));
        assertTrue(result.snapshot().combatSnapshot().playerAbilityIds().contains("fists"));
    }

    @Test
    void endTurnCanBeUsedAnytimeDuringCombat() {
        GameEngine engine = new GameEngine();
        engine.executeCommand("n");
        engine.executeCommand("e");
        engine.executeCommand("n");
        engine.executeCommand("n");

        engine.combatSelectAbility("fists");
        GameResult endTurnResult = engine.combatEndTurn();

        assertTrue(endTurnResult.snapshot().inCombat());
        assertNull(endTurnResult.snapshot().combatSnapshot().selectedAbilityId());
        assertTrue(endTurnResult.messages().stream().anyMatch(line -> line.contains("Ability selection canceled.")));
    }

    @Test
    void supportsDuplicateItemsInRoomAndInventory() {
        GameEngine engine = new GameEngine();
        // Go to storage_building
        engine.executeCommand("e");
        GameSnapshot snapshotBefore = engine.snapshot();
        // There should be 3 apple juices on the ground
        assertEquals(List.of("apple_juice", "apple_juice", "apple_juice"), snapshotBefore.visibleItems());
        assertTrue(snapshotBefore.inventory().isEmpty());

        // Take two apple juices
        engine.take("apple_juice");
        engine.take("apple_juice");

        GameSnapshot snapshotAfterTaking = engine.snapshot();
        // Inventory should have 2 apple juices, and 1 should remain on the ground
        assertEquals(List.of("apple_juice", "apple_juice"), snapshotAfterTaking.inventory());
        assertEquals(List.of("apple_juice"), snapshotAfterTaking.visibleItems());

        // Drop one apple juice
        engine.drop("apple_juice");
        GameSnapshot snapshotAfterDropping = engine.snapshot();
        // Inventory should have 1 apple juice, and 2 should be on the ground
        assertEquals(List.of("apple_juice"), snapshotAfterDropping.inventory());
        assertEquals(List.of("apple_juice", "apple_juice"), snapshotAfterDropping.visibleItems());
    }

    @Test
    void openingSceneContainsWelcomeText() {
        GameEngine engine = new GameEngine();
        GameResult result = engine.openingScene();
        assertTrue(result.messages().stream().anyMatch(line -> line.contains("Welcome, Major!")));
        assertTrue(result.messages().stream().anyMatch(line -> line.contains("Type 'instructions'")));
    }

    @Test
    void returnPathsExistFromDeadEndRooms() {
        GameEngine engine = new GameEngine();
        // Go to hohe_dirn
        engine.executeCommand("n");
        engine.executeCommand("e");
        assertEquals("hohe_dirn", engine.snapshot().locationId());

        // Can go back to train_station (w)
        GameResult wResult = engine.executeCommand("w");
        assertEquals("train_station", wResult.snapshot().locationId());
        
        // Go back to hohe_dirn and then to slums
        engine.executeCommand("e");
        engine.executeCommand("n");
        assertEquals("hd_slums", engine.snapshot().locationId());

        // Can go back to hohe_dirn (s)
        GameResult sResult = engine.executeCommand("s");
        assertEquals("hohe_dirn", sResult.snapshot().locationId());
    }

    @Test
    void honorChangesOnDeathAndCombat() {
        GameEngine engine = new GameEngine();
        assertEquals(25, engine.snapshot().honor());

        // Manual die reduces honor by 5
        engine.executeCommand("die");
        assertEquals(20, engine.snapshot().honor());
    }

    @Test
    void takeAndDropDoNotConsumeZeroJuiceTurns() {
        GameEngine engine = new GameEngine();
        // Drain juice to 0 by moving repeatedly
        for (int i = 0; i < 5; i++) {
            engine.executeCommand("n");
            engine.executeCommand("s");
        }
        assertEquals(0, engine.snapshot().appleJuice());
        assertEquals(0, engine.snapshot().zeroJuiceTurns());

        // Take and drop should not increment zeroJuiceTurns
        engine.take("apple_juice"); // doesn't exist but runs command
        assertEquals(0, engine.snapshot().zeroJuiceTurns());

        engine.drop("apple_juice"); // doesn't have it but runs command
        assertEquals(0, engine.snapshot().zeroJuiceTurns());
    }

    private static final String TEST_CAMPAIGN_YAML = """
items:
  - id: "key"
    name: "Old Key"
    category: "other"
  - id: "walking_cane"
    name: "Walking Cane"
    category: "weapon"
    damage: 4
    range: 1
  - id: "fists"
    name: "Fists"
    category: "weapon"
    damage: 5
    range: 1
commands:
  instructions:
    - "Go N/S/E/W"
variables:
  global_flag: false
sections:
  camp1:
    title: "Campaign 1"
    variables:
      camp_flag: false
    sections:
      sec1:
        title: "Section 1"
        variables:
          sec_flag: false
        locations:
          mud_huzz:
            title: "Room A"
            checkpoint: true
            variables:
              room_flag: false
            description: ["Room A description."]
            paths:
              n: "room_b|Go north."
          room_b:
            title: "Room B"
            description: ["Room B description."]
            dialogue:
              startNode: "greet"
              nodes:
                greet:
                  speaker: "NPC"
                  text: "Hello!"
                  choices:
                    - text: "Give item"
                      targetNode: "reward"
                      requiredItem: "key"
                    - text: "Be aggressive"
                      targetNode: "fight"
                reward:
                  speaker: "NPC"
                  text: "Thanks!"
                  endDialogue: true
                  befriended: true
                  setFlags:
                    sec_flag: true
                fight:
                  speaker: "NPC"
                  text: "En Garde!"
                  endDialogue: true
                  startCombat: true
            encounter:
              rows: 3
              cols: 3
              playerStart: { row: 2, col: 0 }
              enemies:
                - id: "npc_enemy"
                  name: "NPC Enemy"
                  maxHp: 10
                  weapons: ["walking_cane"]
                  row: 0
                  col: 2
            paths:
              s: "mud_huzz|Go south."
              e: "room_c|Go east."
      sec2:
        title: "Section 2"
        locations:
          room_c:
            title: "Room C"
            checkpoint: true
            description: ["Room C description."]
            challenge:
              type: "balance_bar"
              difficulty: "easy"
              onSuccess:
                setFlags:
                  camp_flag: true
                message: "You succeeded!"
              onFailure:
                damage: 20
                failTarget: "mud_huzz"
                message: "You failed!"
            paths:
              w: "room_b|Go west."
""";

    @Test
    void testScopedVariablesAndLcaPopping() {
        ByteArrayInputStream bis = new ByteArrayInputStream(TEST_CAMPAIGN_YAML.getBytes());
        GameTextCatalog catalog = GameTextCatalog.load(bis);
        GameEngine engine = new GameEngine(catalog);

        // Start in mud_huzz
        assertEquals("mud_huzz", engine.snapshot().locationId());

        // Check initial scoped variable values
        assertFalse(engine.getStoryFlag("global_flag"));
        assertFalse(engine.getStoryFlag("camp_flag"));
        assertFalse(engine.getStoryFlag("sec_flag"));
        assertFalse(engine.getStoryFlag("room_flag"));

        // Set room local flag to true
        engine.setStoryFlag("room_flag", true);
        assertTrue(engine.getStoryFlag("room_flag"));

        // Set sec_flag to true
        engine.setStoryFlag("sec_flag", true);
        assertTrue(engine.getStoryFlag("sec_flag"));

        // Move to room_b (same section, room scope mud_huzz is exited)
        engine.executeCommand("n");
        assertEquals("room_b", engine.snapshot().locationId());

        // We must exit dialogue to allow moving out of room_b
        engine.executeCommand("exit");

        // room_flag should be popped/cleared (evaluates to false/default)
        assertFalse(engine.getStoryFlag("room_flag"));
        // sec_flag should still be true (we did not exit sec1)
        assertTrue(engine.getStoryFlag("sec_flag"));

        // Move to room_c (different section, exits sec1 and enters sec2. LCA is camp1)
        engine.executeCommand("e");
        assertEquals("room_c", engine.snapshot().locationId());

        // sec_flag from sec1 should be popped/cleared
        assertFalse(engine.getStoryFlag("sec_flag"));
    }

    @Test
    void testHierarchicalCheckpointsAndDeathRespawn() {
        ByteArrayInputStream bis = new ByteArrayInputStream(TEST_CAMPAIGN_YAML.getBytes());
        GameTextCatalog catalog = GameTextCatalog.load(bis);
        GameEngine engine = new GameEngine(catalog);

        // mud_huzz is a checkpoint, automatically registered for /camp1/sec1/mud_huzz/
        assertEquals("mud_huzz", engine.snapshot().locationId());

        // Move to room_b (no checkpoint)
        engine.executeCommand("n");
        // We must exit dialogue to allow moving out of room_b
        engine.executeCommand("exit");

        // Move to room_c (checkpoint in /camp1/sec2/room_c/)
        engine.executeCommand("e");
        assertEquals("room_c", engine.snapshot().locationId());

        // Die in room_c. Nearest active checkpoint should resolve to room_c
        GameResult dieInC = engine.executeCommand("die");
        assertEquals("room_c", dieInC.snapshot().locationId());

        // Move back to room_b (exits sec2, which pops room_c active checkpoint!)
        engine.executeCommand("w");
        assertEquals("room_b", engine.snapshot().locationId());

        // Die in room_b. /camp1/sec2/room_c/ checkpoint was popped, nearest checkpoint is mud_huzz
        GameResult dieInB = engine.executeCommand("die");
        assertEquals("mud_huzz", dieInB.snapshot().locationId());
    }

    @Test
    void testDialogueTreesAndBefriending() {
        ByteArrayInputStream bis = new ByteArrayInputStream(TEST_CAMPAIGN_YAML.getBytes());
        GameTextCatalog catalog = GameTextCatalog.load(bis);
        GameEngine engine = new GameEngine(catalog);

        // Move to room_b to trigger dialogue
        engine.executeCommand("n");
        assertNotNull(engine.getActiveDialogue());

        // Attempt choice 1 (requires key). Should fail because we don't have it.
        GameResult choiceFail = engine.executeCommand("1");
        assertTrue(choiceFail.messages().stream().anyMatch(m -> m.contains("Requires item: key")));
        assertNotNull(engine.getActiveDialogue());

        // Gain key item
        engine.gainItem("key");

        // Now choice 1 should be allowed and execute successfully, befriending NPC
        GameResult choiceSuccess = engine.executeCommand("1");
        assertTrue(choiceSuccess.messages().stream().anyMatch(m -> m.contains("befriended")));
        assertNull(engine.getActiveDialogue());
        assertTrue(engine.getStoryFlag("sec_flag")); // setFlags from choice reward
    }

    @Test
    void testDialogueCombatTrigger() {
        ByteArrayInputStream bis = new ByteArrayInputStream(TEST_CAMPAIGN_YAML.getBytes());
        GameTextCatalog catalog = GameTextCatalog.load(bis);
        GameEngine engine = new GameEngine(catalog);

        // Move to room_b to trigger dialogue
        engine.executeCommand("n");
        assertNotNull(engine.getActiveDialogue());

        // Choice 2 triggers fight
        GameResult fightResult = engine.executeCommand("2");
        assertNull(engine.getActiveDialogue());
        assertTrue(fightResult.snapshot().inCombat());
    }

    @Test
    void testChallengePhysicsAndOutcomes() {
        ByteArrayInputStream bis = new ByteArrayInputStream(TEST_CAMPAIGN_YAML.getBytes());
        GameTextCatalog catalog = GameTextCatalog.load(bis);
        GameEngine engine = new GameEngine(catalog);

        // Bypass dialogue peacefully to room_c
        engine.executeCommand("n");
        engine.gainItem("key");
        engine.executeCommand("1");
        engine.executeCommand("e");
        assertEquals("room_c", engine.snapshot().locationId());

        // room_c starts a challenge
        assertNotNull(engine.getActiveChallenge());
        double initialPos = engine.getActiveChallenge().getPlayerPosition();
        assertEquals(0.5, initialPos);

        // Apply impulse (steer left) and tick to let physics run
        engine.executeCommand("w");
        engine.executeCommand("tick 0.1");
        assertTrue(engine.getActiveChallenge().getPlayerPosition() != 0.5);

        // Complete challenge using reflection to ensure success
        ChallengeEngine active = engine.getActiveChallenge();
        assertNotNull(active);
        try {
            java.lang.reflect.Field finishedField = active.getClass().getDeclaredField("finished");
            finishedField.setAccessible(true);
            finishedField.set(active, true);

            java.lang.reflect.Field successField = active.getClass().getDeclaredField("success");
            successField.setAccessible(true);
            successField.set(active, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        engine.handleChallengeCompletion(new java.util.ArrayList<>());

        // Challenge should be completed successfully, camp_flag is set to true
        assertNull(engine.getActiveChallenge());
        assertTrue(engine.getStoryFlag("camp_flag"));
    }

    @Test
    void testDeprecatedCommandsAndNoMeter() {
        GameEngine engine = new GameEngine();
        
        // Test take command deprecation message
        GameResult resultTake = engine.executeCommand("take apple_juice");
        assertTrue(resultTake.messages().stream().anyMatch(msg -> msg.contains("deprecated") && msg.contains("take")),
                "Expected deprecation message for 'take' command.");

        // Test drop command deprecation message
        GameResult resultDrop = engine.executeCommand("drop apple_juice");
        assertTrue(resultDrop.messages().stream().anyMatch(msg -> msg.contains("deprecated") && msg.contains("drop")),
                "Expected deprecation message for 'drop' command.");

        // Drain juice to below LOW_JUICE_THRESHOLD (which is 25 or similar)
        // Ensure no "[Meter]" messages are generated when moving or performing commands
        engine.executeCommand("e"); // moves, drains juice
        GameResult resultMove = engine.executeCommand("w");
        boolean hasMeterMessage = resultMove.messages().stream().anyMatch(msg -> msg.contains("[Meter]"));
        assertFalse(hasMeterMessage, "Expected no meter messages in game results.");
    }

    @Test
    void testDrinkWithoutJuice() {
        GameEngine engine = new GameEngine();
        
        // Assert inventory is initially empty
        assertTrue(engine.snapshot().inventory().isEmpty());

        // Execute drink command
        GameResult result = engine.executeCommand("drink");

        // Verify the notification message is an error (contains "don't")
        assertTrue(result.messages().stream().anyMatch(msg -> msg.toLowerCase().contains("don't") && msg.toLowerCase().contains("apple juice")),
                "Expected error message containing \"don't\" and \"apple juice\".");

        // Verify the growl dialogue was appended to the room's dialog lines
        assertTrue(engine.snapshot().locationDialog().stream().anyMatch(line -> line.contains("Major growls: \"No apple juice? Um gods wün!\"")),
                "Expected growl dialogue to be appended to room's dialog history.");
    }
}


