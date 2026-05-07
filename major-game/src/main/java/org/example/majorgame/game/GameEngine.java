package org.example.majorgame.game;

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
            "train_station",
            "storage_building",
            "arm_chair"
    );

    private final GameWorld world = GameWorld.createDefault();

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
        messages.addAll(instructionsText());
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
        }, true);
    }

    public GameResult take(String itemName) {
        String item = normalizeItem(itemName);
        return runTurn(messages -> {
            if (inventory.contains(item)) {
                messages.add("You're already holding it!");
                return;
            }

            if (location.equals(itemLocations.get(item))) {
                itemLocations.remove(item);
                inventory.add(item);
                messages.add("OK.");
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

            inventory.remove(item);
            itemLocations.put(item, location);
            messages.add("OK.");
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
        Room room = world.room(location);
        return new GameSnapshot(
                location,
                room.title(),
                buildLocationPrompt(),
                appleJuice,
                honor,
                zeroJuiceTurns,
                Set.copyOf(inventory),
                List.copyOf(visibleItems()),
                false
        );
    }

    private List<String> instructionsText() {
        return List.of(
                "Available commands are:",
                "n | s | e | w       -- to go in that direction.",
                "take ITEM           -- to pick up an object.",
                "drop ITEM           -- to put down an object.",
                "drink               -- to drink one apple_juice from inventory.",
                "check               -- to set this place as checkpoint (if allowed).",
                "clear               -- to clear the dialogue log.",
                "instructions        -- to see this message again.",
                "halt | die          -- to respawn at checkpoint."
        );
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

    private String buildLocationPrompt() {
        Room room = world.room(location);
        List<String> lines = new ArrayList<>();
        lines.add("---- " + room.title() + " ----");
        lines.addAll(room.descriptionLines());
        for (String item : visibleItems()) {
            lines.add("There is a " + item + " here.");
        }
        return String.join(System.lineSeparator(), lines);
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
    }

    private void appendDeathStats(List<String> messages) {
        messages.add(
                "[Stats] Commands this run: " + commandsSinceRespawn
                        + " | Moves: " + movesSinceRespawn
                        + " | Apple juices drunk: " + appleJuicesDrunkSinceRespawn
                        + " | Deaths total: " + deathCount
        );
    }

    private void respawnAtCheckpoint(List<String> messages) {
        location = checkpointLocation;
        appleJuice = MAX_JUICE;
        zeroJuiceTurns = 0;
        commandsSinceRespawn = 0;
        movesSinceRespawn = 0;
        appleJuicesDrunkSinceRespawn = 0;
        messages.add("---- Respawned at checkpoint ----");
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
        return new GameResult(List.copyOf(messages), snapshot());
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
    }
}
