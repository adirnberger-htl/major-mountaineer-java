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

    private final GameWorld world = GameWorld.createDefault();

    private String location;
    private Map<String, String> itemLocations;
    private final Set<String> inventory = new LinkedHashSet<>();
    private int appleJuice;
    private int zeroJuiceTurns;
    private int honor;
    private boolean gameOver;

    public GameEngine() {
        reset();
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
        if (command.startsWith("route(") && command.endsWith(")")) {
            return route(parseRoute(command.substring(6, command.length() - 1)));
        }

        return switch (command) {
            case "start" -> start();
            case "look" -> look();
            case "drink" -> drink();
            case "juicy" -> juicy();
            case "instructions" -> instructions();
            case "die", "halt" -> die();
            default -> resultOf(List.of("Unknown command."));
        };
    }

    public GameResult start() {
        return runTurn(messages -> {
            messages.addAll(instructionsText());
            renderCurrentLocation(messages);
        }, true);
    }

    public GameResult look() {
        return runTurn(this::renderCurrentLocation, true);
    }

    public GameResult move(Direction direction) {
        return runTurn(messages -> {
            String next = world.nextLocation(location, direction);
            if (next == null) {
                messages.add("You can't go that way.");
                return;
            }

            location = next;
            lowerAppleJuice();
            renderCurrentLocation(messages);
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
            messages.add("You drink an apple juice.");
            printLowJuiceUpdate(messages);
        }, true);
    }

    public GameResult juicy() {
        return runTurn(messages -> messages.add("Apple juice meter: " + appleJuice + "."), true);
    }

    public GameResult instructions() {
        return runTurn(messages -> messages.addAll(instructionsText()), true);
    }

    public GameResult route(List<Direction> moves) {
        if (gameOver) {
            return resultOf(List.of("The game is over."));
        }
        for (Direction move : moves) {
            String next = world.nextLocation(location, move);
            if (next == null) {
                break;
            }
            location = next;
        }
        List<String> messages = new ArrayList<>();
        renderCurrentLocation(messages);
        return resultOf(messages);
    }

    public GameResult die() {
        if (gameOver) {
            return resultOf(List.of("The game is over."));
        }
        gameOver = true;
        return resultOf(List.of("The game is over."));
    }

    public GameSnapshot snapshot() {
        Room room = world.room(location);
        return new GameSnapshot(
                location,
                room.title(),
                appleJuice,
                honor,
                zeroJuiceTurns,
                Set.copyOf(inventory),
                List.copyOf(visibleItems()),
                gameOver
        );
    }

    private List<String> instructionsText() {
        return List.of(
                "Available commands are:",
                "start               -- to start the game.",
                "n | s | e | w       -- to go in that direction.",
                "take ITEM           -- to pick up an object.",
                "drop ITEM           -- to put down an object.",
                "look                -- to look around you again.",
                "route([n,e,...])    -- run a silent batch of moves without juice use.",
                "drink               -- to drink one apple_juice from inventory.",
                "juicy               -- to check apple juice meter.",
                "instructions        -- to see this message again.",
                "halt | die          -- to end the game."
        );
    }

    private interface TurnAction {
        void run(List<String> messages);
    }

    private GameResult runTurn(TurnAction action, boolean applyEndTurn) {
        if (gameOver) {
            return resultOf(List.of("The game is over."));
        }

        boolean wasZeroBefore = appleJuice == 0;
        List<String> messages = new ArrayList<>();
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

    private void renderCurrentLocation(List<String> messages) {
        Room room = world.room(location);
        if (room.autoRouteDirection() != null) {
            messages.add(room.autoRouteMessage());
            applyRouteMove(room.autoRouteDirection());
            room = world.room(location);
        }

        messages.add("---- " + room.title() + " ----");
        messages.addAll(room.descriptionLines());
        for (String item : visibleItems()) {
            messages.add("There is a " + item + " here.");
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
        messages.add("You died of death. Two commands passed at zero juice.");
        messages.add("The game is over.");
        gameOver = true;
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

    private List<Direction> parseRoute(String routeBody) {
        String cleaned = routeBody.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        if (cleaned.isEmpty()) {
            return List.of();
        }

        List<Direction> moves = new ArrayList<>();
        for (String token : cleaned.split(",")) {
            Direction direction = Direction.fromToken(token.trim());
            if (direction != null) {
                moves.add(direction);
            }
        }
        return moves;
    }

    private GameResult resultOf(List<String> messages) {
        return new GameResult(List.copyOf(messages), snapshot());
    }

    private void reset() {
        location = "mud_huzz";
        itemLocations = world.initialItemLocations();
        inventory.clear();
        appleJuice = MAX_JUICE;
        zeroJuiceTurns = 0;
        honor = 25;
        gameOver = false;
    }
}
