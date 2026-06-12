package org.example.majorgame.game;

import org.example.majorgame.game.combat.CombatActionResult;
import org.example.majorgame.game.combat.CombatDataCatalog;
import org.example.majorgame.game.combat.CombatEngine;
import org.example.majorgame.game.combat.CombatSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GameEngine {
    private static final int MAX_JUICE = 100;
    private static final int MOVE_JUICE_COST = 10;
    private static final int LOW_JUICE_THRESHOLD = 60;
    private static final String DEFAULT_CHECKPOINT = "mud_huzz";
    private static final Set<String> ALLOWED_CHECKPOINT_LOCATIONS = Set.of(
            "mud_huzz",
            "hohe_dirn"
    );
    private static final GameTextCatalog TEXTS = GameTextCatalog.defaultCatalog();
    private static final CombatDataCatalog COMBAT_DATA = CombatDataCatalog.defaultCatalog();

    private final GameWorld world = GameWorld.createDefault();
    private final Set<String> clearedCombatLocations = new LinkedHashSet<>();

    private String location;
    private String checkpointLocation;
    private Map<String, String> itemLocations;
    private final Set<String> inventory = new LinkedHashSet<>();
    private int appleJuice;
    private int zeroJuiceTurns;
    private int honor;
    private int commandsSinceRespawn;
    private int movesSinceRespawn;
    private int appleJuicesDrunkSinceRespawn;
    private int deathCount;
    private boolean clearOutputOnNextResult;
    private CombatEngine activeCombat;

    public GameEngine() {
        reset();
    }

    public void setCheckpoint(String roomId) {
        if (world.room(roomId) != null) {
            checkpointLocation = roomId;
        }
    }

    public GameResult executeCommand(String rawCommand) {
        String command = normalizeCommand(rawCommand);
        if (command.isEmpty()) {
            return resultOf(List.of("Enter a command."));
        }

        if (activeCombat != null && !activeCombat.isFinished()) {
            return switch (command) {
                case "die", "halt" -> die();
                case "instructions" -> resultOf(List.of(
                        "You are in combat. Use the battle grid and action box.",
                        "Combat commands: click tile to move, choose ability, click target, then End Turn."
                ));
                default -> resultOf(List.of("You are in combat. Use the battle controls."));
            };
        }

        Direction direction = Direction.fromToken(command);
        if (direction != null) {
            return move(direction);
        }

        if (command.startsWith("take(") && command.endsWith(")")) {
            return take(command.substring(5, command.length() - 1));
        }
        if (command.startsWith("drop(") && command.endsWith(")")) {
            return drop(command.substring(5, command.length() - 1));
        }
        if (command.startsWith("take ")) {
            return take(command.substring(5));
        }
        if (command.startsWith("drop ")) {
            return drop(command.substring(5));
        }
        return switch (command) {
            case "drink" -> drink();
            case "check" -> check();
            case "instructions" -> instructions();
            case "die", "halt" -> die();
            default -> resultOf(List.of("Unknown command."));
        };
    }

    public GameResult openingScene() {
        List<String> messages = new ArrayList<>();
        return resultOf(messages);
    }

    public GameResult move(Direction direction) {
        return runTurn(messages -> {
            String next = world.nextLocation(location, direction);
            if (next == null) {
                messages.add("You can't go that way.");
                return;
            }

            location = next;
            handleAutoRoute(messages);
            lowerAppleJuice();
            movesSinceRespawn += 1;
            printLowJuiceUpdate(messages);
            maybeStartCombat(messages);
        }, true);
    }

    public GameResult combatMovePlayer(int row, int col) {
        if (activeCombat == null || activeCombat.isFinished()) {
            return resultOf(List.of("No active combat."));
        }
        CombatActionResult result = activeCombat.movePlayer(row, col);
        return resultOf(result.messages());
    }

    public GameResult combatSelectAbility(String abilityId) {
        if (activeCombat == null || activeCombat.isFinished()) {
            return resultOf(List.of("No active combat."));
        }
        CombatActionResult result = activeCombat.selectAbility(abilityId);
        return resultOf(result.messages());
    }

    public GameResult combatTargetAt(int row, int col) {
        if (activeCombat == null || activeCombat.isFinished()) {
            return resultOf(List.of("No active combat."));
        }
        CombatActionResult result = activeCombat.targetAt(row, col);
        List<String> messages = new ArrayList<>(result.messages());
        handleCombatCompletion(messages);
        return resultOf(messages);
    }

    public GameResult combatEndTurn() {
        if (activeCombat == null || activeCombat.isFinished()) {
            return resultOf(List.of("No active combat."));
        }
        CombatActionResult result = activeCombat.endTurn();
        List<String> messages = new ArrayList<>(result.messages());
        handleCombatCompletion(messages);
        return resultOf(messages);
    }

    public boolean inCombat() {
        return activeCombat != null && !activeCombat.isFinished();
    }

    public GameResult take(String itemName) {
        String item = normalizeItem(itemName);
        return runTurn(messages -> {
            if (inventory.contains(item)) {
                messages.add("You're already holding it!");
                return;
            }
            if (inventory.size() >= 4) {
                messages.add("Your inventory is full (max 4 items).");
                return;
            }

            if (location.equals(itemLocations.get(item))) {
                itemLocations.remove(item);
                inventory.add(item);
                return;
            }

            messages.add("I don't see it here.");
        }, true);
    }

    public GameResult drop(String itemName) {
        String item = normalizeItem(itemName);
        return runTurn(messages -> {
            if (!inventory.contains(item)) {
                messages.add("You aren't holding it!");
                return;
            }
            if (visibleItems().size() >= 4) {
                messages.add("The ground is too cluttered here (max 4 items).");
                return;
            }

            inventory.remove(item);
            itemLocations.put(item, location);
        }, true);
    }

    public GameResult drink() {
        return runTurn(messages -> {
            if (!inventory.contains("apple_juice")) {
                messages.add("Major growls: \"No apple juice? Um gods wün!\"");
                return;
            }

            inventory.remove("apple_juice");
            refillAppleJuice();
            appleJuicesDrunkSinceRespawn += 1;
            messages.add("You drink an apple juice.");
            printLowJuiceUpdate(messages);
        }, true);
    }

    public GameResult instructions() {
        return runTurn(messages -> messages.addAll(instructionsText()), true);
    }

    public GameResult check() {
        return runTurn(messages -> {
            Room room = world.room(location);
            if (!ALLOWED_CHECKPOINT_LOCATIONS.contains(location)) {
                messages.add("You cannot set a checkpoint here.");
                return;
            }
            if (location.equals(checkpointLocation)) {
                messages.add("Checkpoint already set here.");
                return;
            }

            checkpointLocation = location;
            messages.add("Checkpoint set at " + room.title() + ".");
        }, true);
    }

    public GameResult die() {
        return runTurn(messages -> {
            triggerDeathAndRespawn(messages, "You died of death.");
        }, false);
    }

    public GameSnapshot snapshot() {
        return snapshot(false);
    }

    private GameSnapshot snapshot(boolean gameOver) {
        Room room = world.room(location);
        CombatSnapshot combatSnapshot = inCombat() ? activeCombat.snapshot() : null;
        return new GameSnapshot(
                location,
                room.title(),
                List.copyOf(room.descriptionLines()),
                List.copyOf(room.dialogLines()),
                world.availableExits(location),
                appleJuice,
                honor,
                zeroJuiceTurns,
                Set.copyOf(inventory),
                List.copyOf(visibleItems()),
                gameOver,
                inCombat(),
                combatSnapshot
        );
    }

    private List<String> instructionsText() {
        return TEXTS.instructions();
    }

    private interface TurnAction {
        void run(List<String> messages);
    }

    private GameResult runTurn(TurnAction action, boolean applyEndTurn) {
        boolean wasZeroBefore = appleJuice == 0;
        List<String> messages = new ArrayList<>();
        commandsSinceRespawn += 1;
        action.run(messages);
        if (applyEndTurn) {
            endTurn(wasZeroBefore, messages);
        }
        return resultOf(messages);
    }

    private void endTurn(boolean wasZeroBefore, List<String> messages) {
        if (appleJuice == 0) {
            if (wasZeroBefore) {
                zeroJuiceTurns += 1;
                if (zeroJuiceTurns >= 2) {
                    appleDeath(messages);
                } else {
                    printAppleDeathWarning(messages);
                }
            } else {
                zeroJuiceTurns = 0;
                printAppleDeathWarning(messages);
            }
            return;
        }
        zeroJuiceTurns = 0;
    }

    private void handleAutoRoute(List<String> messages) {
        Room room = world.room(location);
        if (room.autoRouteDirection() != null) {
            messages.add(room.autoRouteMessage());
            applyRouteMove(room.autoRouteDirection());
        }
    }

    private void applyRouteMove(Direction move) {
        String next = world.nextLocation(location, move);
        if (next != null) {
            location = next;
        }
    }

    private List<String> visibleItems() {
        return itemLocations.entrySet().stream()
                .filter(entry -> location.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted(Comparator.naturalOrder())
                .toList();
    }



    private void lowerAppleJuice() {
        appleJuice = Math.max(0, appleJuice - MOVE_JUICE_COST);
    }

    private void refillAppleJuice() {
        appleJuice = MAX_JUICE;
    }

    private void printLowJuiceUpdate(List<String> messages) {
        if (appleJuice <= LOW_JUICE_THRESHOLD) {
            messages.add("[Meter] Apple juice is at " + appleJuice + ".");
        }
    }

    private void printAppleDeathWarning(List<String> messages) {
        int remaining = 2 - zeroJuiceTurns;
        if (remaining > 0) {
            messages.add("[Warning] You got no apple juice left. This is bad ... very bad. " + remaining +
                    " command(s) left before you die a painful death.");
        }
    }

    private void appleDeath(List<String> messages) {
        messages.add("Major screams: \"AHHHH, IT HURTS, AHHHHHH.\"");
        triggerDeathAndRespawn(messages, "You died of death. Two commands passed at zero juice.");
    }

    private void triggerDeathAndRespawn(List<String> messages, String reason) {
        messages.add("");
        messages.add(reason);
        messages.add("");
        deathCount += 1;
        appendDeathStats(messages);
        messages.add("");
        messages.add("You wake up at your checkpoint.");
        messages.add("");
        respawnAtCheckpoint(messages);
        clearOutputOnNextResult = true;
    }

    private void appendDeathStats(List<String> messages) {
        messages.add("========== [Stats] ==========");
        messages.add("Commands this run : " + commandsSinceRespawn);
        messages.add("Moves             : " + movesSinceRespawn);
        messages.add("Apple juices drunk: " + appleJuicesDrunkSinceRespawn);
        messages.add("Deaths total      : " + deathCount);
        messages.add("=============================");
    }

    private void respawnAtCheckpoint(List<String> messages) {
        location = checkpointLocation;
        appleJuice = MAX_JUICE;
        zeroJuiceTurns = 0;
        commandsSinceRespawn = 0;
        movesSinceRespawn = 0;
        appleJuicesDrunkSinceRespawn = 0;
        activeCombat = null;
        messages.add("---- Respawned at checkpoint ----");
    }

    private void maybeStartCombat(List<String> messages) {
        if (activeCombat != null && !activeCombat.isFinished()) {
            return;
        }
        if (clearedCombatLocations.contains(location)) {
            return;
        }

        CombatDataCatalog.EncounterDefinition encounter = COMBAT_DATA.encounterForLocation(location);
        if (encounter == null) {
            return;
        }

        activeCombat = CombatEngine.start(COMBAT_DATA, encounter);
        messages.addAll(activeCombat.openingMessages());
        messages.add("Combat started. Use the battle grid to move and attack.");
    }

    private void handleCombatCompletion(List<String> messages) {
        if (activeCombat == null || !activeCombat.isFinished()) {
            return;
        }
        if (activeCombat.playerWon()) {
            clearedCombatLocations.add(location);
            messages.add("The path is clear for now.");
            world.appendDialog(location, "SYSTEM: Major defeated the enemy!");
            return;
        }
        world.appendDialog(location, "SYSTEM: Major was defeated in combat.");
        triggerDeathAndRespawn(messages, "You were defeated in combat.");
    }

    private String normalizeCommand(String rawCommand) {
        if (rawCommand == null) {
            return "";
        }
        String value = rawCommand.trim().toLowerCase();
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }

    private String normalizeItem(String itemName) {
        return itemName.trim().toLowerCase().replace(' ', '_');
    }

    private GameResult resultOf(List<String> messages) {
        boolean gameOver = clearOutputOnNextResult;
        clearOutputOnNextResult = false;
        return new GameResult(List.copyOf(messages), snapshot(gameOver));
    }

    private void reset() {
        location = DEFAULT_CHECKPOINT;
        checkpointLocation = DEFAULT_CHECKPOINT;
        itemLocations = world.initialItemLocations();
        inventory.clear();
        appleJuice = MAX_JUICE;
        zeroJuiceTurns = 0;
        honor = 25;
        commandsSinceRespawn = 0;
        movesSinceRespawn = 0;
        appleJuicesDrunkSinceRespawn = 0;
        deathCount = 0;
        clearOutputOnNextResult = false;
        activeCombat = null;
        clearedCombatLocations.clear();
    }
}
