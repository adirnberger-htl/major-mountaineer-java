package org.example.majorgame.game;

import org.example.majorgame.game.combat.CombatActionResult;
import org.example.majorgame.game.combat.CombatDataCatalog;
import org.example.majorgame.game.combat.CombatEngine;
import org.example.majorgame.game.combat.CombatSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private static final CombatDataCatalog COMBAT_DATA = CombatDataCatalog.defaultCatalog();

    private final GameTextCatalog texts;
    private final GameWorld world;
    private final Set<String> clearedCombatLocations = new LinkedHashSet<>();
    private final Map<String, Map<String, Boolean>> scopedStoryFlags = new LinkedHashMap<>();
    private final Map<String, String> activeCheckpoints = new LinkedHashMap<>();

    private String location;
    private String checkpointLocation;
    private List<GameTextCatalog.ItemPlacement> itemLocations;
    private final List<String> inventory = new ArrayList<>();
    private int appleJuice;
    private int zeroJuiceTurns;
    private int honor;
    private int commandsSinceRespawn;
    private int movesSinceRespawn;
    private int appleJuicesDrunkSinceRespawn;
    private int deathCount;
    private boolean clearOutputOnNextResult;
    private CombatEngine activeCombat;
    private DialogueEngine activeDialogue;
    private ChallengeEngine activeChallenge;

    public GameEngine() {
        this(GameTextCatalog.defaultCatalog());
    }

    public GameEngine(GameTextCatalog texts) {
        this.texts = texts;
        this.world = GameWorld.create(texts);
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

        // Dialogue Mode
        if (activeDialogue != null) {
            if (command.equals("exit")) {
                activeDialogue = null;
                return resultOf(List.of("Dialogue ended."));
            }
            if (command.equals("die") || command.equals("halt")) {
                activeDialogue = null;
                return die();
            }
            if (command.equals("instructions")) {
                return resultOf(List.of(
                        "You are in a conversation. Type the number of your choice (e.g. '1', '2')."
                ));
            }
            try {
                int choiceIndex = Integer.parseInt(command) - 1;
                List<String> messages = new ArrayList<>();
                DialogueEngine.DialogueChoiceResult result = activeDialogue.choose(choiceIndex, messages);
                if (result.success()) {
                    messages.add(result.text());
                    if (result.ended()) {
                        activeDialogue = null;
                        messages.add("--- Dialogue ended. ---");
                        Room room = world.room(location);
                        if (room != null && room.descriptionLines() != null) {
                            messages.addAll(room.descriptionLines());
                        }
                    } else {
                        DialogueDefinition.DialogueNode nextNode = activeDialogue.getCurrentNode();
                        messages.add(nextNode.speaker() + ": " + nextNode.text());
                        List<DialogueEngine.DialogueChoiceStatus> choices = activeDialogue.getChoicesStatus();
                        for (int i = 0; i < choices.size(); i++) {
                            messages.add((i + 1) + ") " + choices.get(i).choice().text());
                        }
                    }
                    return resultOf(messages);
                } else {
                    return resultOf(List.of(result.text()));
                }
            } catch (NumberFormatException e) {
                return resultOf(List.of("Enter the number of your choice."));
            }
        }

        // Challenge Mode
        if (activeChallenge != null) {
            if (command.equals("die") || command.equals("halt")) {
                activeChallenge = null;
                return die();
            }
            if (command.equals("instructions")) {
                return resultOf(List.of(
                        "You are in a challenge. Type 'w' or 'l' to steer left, 'e' or 'r' to steer right, or 'tick <seconds>' to simulate time."
                ));
            }
            if (command.equals("w") || command.equals("l")) {
                activeChallenge.applyImpulse(-0.15);
                return resultOf(List.of("Steered left. Position: " + String.format("%.2f", activeChallenge.getPlayerPosition())));
            }
            if (command.equals("e") || command.equals("r")) {
                activeChallenge.applyImpulse(0.15);
                return resultOf(List.of("Steered right. Position: " + String.format("%.2f", activeChallenge.getPlayerPosition())));
            }
            if (command.startsWith("tick ")) {
                try {
                    double dt = Double.parseDouble(command.substring(5).trim());
                    activeChallenge.update(dt);
                    List<String> messages = new ArrayList<>();
                    messages.add("Ticked challenge by " + dt + "s. Position: " + String.format("%.2f", activeChallenge.getPlayerPosition()));
                    if (activeChallenge.isFinished()) {
                        handleChallengeCompletion(messages);
                    }
                    return resultOf(messages);
                } catch (NumberFormatException e) {
                    return resultOf(List.of("Enter a number of seconds to tick."));
                }
            }
            return resultOf(List.of("You are in a challenge. Control with steer inputs or tick."));
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
        messages.add("Welcome, Major! Your journey begins at the Mud Hut.");
        messages.add("Type 'instructions' to see available commands.");
        return resultOf(messages);
    }

    public GameResult move(Direction direction) {
        return runTurn(messages -> {
            Room currentRoom = world.room(location);
            PathDefinition path = currentRoom != null ? currentRoom.paths().get(direction) : null;
            String next;
            if (path != null) {
                next = path.targetLocation();
                // Check requirements
                for (String req : path.requireFlags()) {
                    if (!checkRequirement(req)) {
                        String failMsg = path.failMessage();
                        if (failMsg == null || failMsg.isEmpty()) {
                            failMsg = "You cannot go that way yet.";
                        }
                        messages.add(failMsg);
                        return;
                    }
                }
                
                // Apply costs/rewards
                if (path.juiceCost() > 0) {
                    appleJuice = Math.max(0, appleJuice - path.juiceCost());
                }
                if (path.honorGain() != 0) {
                    honor = Math.max(0, honor + path.honorGain());
                }
                if (path.itemGain() != null && !path.itemGain().isEmpty()) {
                    if (inventory.size() < 4) {
                        inventory.add(path.itemGain());
                        messages.add("You found a: " + path.itemGain());
                    } else {
                        messages.add("You found a " + path.itemGain() + ", but your inventory is full!");
                        itemLocations.add(new GameTextCatalog.ItemPlacement(path.itemGain(), next));
                    }
                }
            } else {
                next = world.nextLocation(location, direction);
                if (next == null) {
                    messages.add("You can't go that way.");
                    return;
                }
            }
            
            transitionScopes(location, next);
            
            location = next;
            registerCheckpointIfNecessary(location);
            handleAutoRoute(messages);
            lowerAppleJuice();
            movesSinceRespawn += 1;
            printLowJuiceUpdate(messages);
            triggerRoomEvents(messages);
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
            if (inventory.size() >= 4) {
                messages.add("Your inventory is full (max 4 items).");
                return;
            }

            GameTextCatalog.ItemPlacement found = null;
            for (GameTextCatalog.ItemPlacement placement : itemLocations) {
                if (placement.locationId().equals(location) && placement.itemId().equals(item)) {
                    found = placement;
                    break;
                }
            }

            if (found != null) {
                itemLocations.remove(found);
                inventory.add(item);
                return;
            }

            messages.add("I don't see it here.");
        }, false);
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
            itemLocations.add(new GameTextCatalog.ItemPlacement(item, location));
        }, false);
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
            if (room == null) return;
            
            boolean isAllowed;
            if (texts.campaignSections().isEmpty()) {
                isAllowed = ALLOWED_CHECKPOINT_LOCATIONS.contains(location);
            } else {
                isAllowed = room.checkpoint();
            }

            if (!isAllowed) {
                messages.add("You cannot set a checkpoint here.");
                return;
            }

            String innermostScope = getFullScopePath(location) + "/";
            activeCheckpoints.put(innermostScope, location);
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

    private GameSnapshot snapshot(boolean freshRespawn) {
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
                List.copyOf(inventory),
                List.copyOf(visibleItems()),
                freshRespawn,
                inCombat(),
                combatSnapshot
        );
    }

    private List<String> instructionsText() {
        return texts.instructions();
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
            transitionScopes(location, next);
            location = next;
        }
    }

    private List<String> visibleItems() {
        return itemLocations.stream()
                .filter(placement -> location.equals(placement.locationId()))
                .map(GameTextCatalog.ItemPlacement::itemId)
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
        honor = Math.max(0, honor - 5);
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
        String respawnLoc = resolveActiveCheckpoint();
        transitionScopes(location, respawnLoc);
        location = respawnLoc;
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

        Room room = world.room(location);
        CombatDataCatalog.EncounterDefinition encounter = null;
        if (room != null && room.encounter() != null) {
            encounter = room.encounter();
        } else {
            encounter = COMBAT_DATA.encounterForLocation(location);
        }

        if (encounter == null) {
            return;
        }

        activeCombat = CombatEngine.start(COMBAT_DATA, encounter, inventory);
        messages.addAll(activeCombat.openingMessages());
        messages.add("Combat started. Use the battle grid to move and attack.");
    }

    private void triggerRoomEvents(List<String> messages) {
        Room room = world.room(location);
        if (room == null) return;

        // 1. Trigger dialogue if present and not already cleared/befriended
        if (room.dialogue() != null && !clearedCombatLocations.contains(location)) {
            activeDialogue = new DialogueEngine(room.dialogue(), this);
            messages.add("--- Conversation started with " + activeDialogue.getCurrentNode().speaker() + " ---");
            messages.add(activeDialogue.getCurrentNode().speaker() + ": " + activeDialogue.getCurrentNode().text());
            
            // Print available choices
            List<DialogueEngine.DialogueChoiceStatus> choices = activeDialogue.getChoicesStatus();
            for (int i = 0; i < choices.size(); i++) {
                messages.add((i + 1) + ") " + choices.get(i).choice().text());
            }
            return;
        }

        // 2. Trigger challenge if present and not already completed
        if (room.challenge() != null && !getStoryFlag(room.id() + "_challenge_cleared")) {
            activeChallenge = new BalanceBarChallengeEngine(room.challenge().difficulty());
            messages.add("--- Challenge Started: " + room.challenge().type() + " ---");
            messages.add("You must balance across! Command 'w' / 'l' to steer left, 'e' / 'r' to steer right.");
            return;
        }

        // 3. Trigger combat if present and not cleared
        maybeStartCombat(messages);
    }

    private void handleCombatCompletion(List<String> messages) {
        if (activeCombat == null || !activeCombat.isFinished()) {
            return;
        }
        if (activeCombat.playerWon()) {
            clearedCombatLocations.add(location);
            messages.add("The path is clear for now.");
            world.appendDialog(location, "SYSTEM: Major defeated the enemy!");
            honor = Math.min(100, honor + 5);
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
        boolean freshRespawn = clearOutputOnNextResult;
        clearOutputOnNextResult = false;
        return new GameResult(List.copyOf(messages), snapshot(freshRespawn));
    }

    private void reset() {
        location = DEFAULT_CHECKPOINT;
        checkpointLocation = DEFAULT_CHECKPOINT;
        itemLocations = new ArrayList<>(world.initialItemLocations());
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
        activeDialogue = null;
        activeChallenge = null;
        clearedCombatLocations.clear();
        initializeScopesForLocation(location);
    }

    private String getFullScopePath(String locationId) {
        if (locationId == null) {
            return "/";
        }
        String parentScope = texts.roomToScopePath().get(locationId);
        if (parentScope == null) {
            return "/" + locationId;
        }
        return parentScope + locationId;
    }

    private Map<String, Boolean> declaredVariablesForScopePath(String scopePath) {
        if (scopePath == null || "/".equals(scopePath)) {
            return texts.globalVariables();
        }
        String[] parts = scopePath.split("/");
        String lastPart = parts[parts.length - 1];
        Room room = texts.flatRooms().get(lastPart);
        if (room != null) {
            return room.variables();
        }
        SectionDefinition current = null;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (current == null) {
                current = texts.campaignSections().get(part);
            } else {
                current = current.subSections().get(part);
            }
        }
        return current != null ? current.variables() : Map.of();
    }

    private void initializeScopesForLocation(String targetLocation) {
        scopedStoryFlags.clear();
        activeCheckpoints.clear();
        
        String fullPath = getFullScopePath(targetLocation) + "/";
        String[] parts = fullPath.split("/");
        
        StringBuilder scopeBuilder = new StringBuilder();
        List<String> activePaths = new ArrayList<>();
        activePaths.add("/");
        
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (scopeBuilder.length() > 0) {
                scopeBuilder.append("/");
            }
            scopeBuilder.append(part);
            activePaths.add("/" + scopeBuilder.toString() + "/");
        }
        
        for (String path : activePaths) {
            Map<String, Boolean> declared = declaredVariablesForScopePath(path);
            scopedStoryFlags.put(path, new LinkedHashMap<>(declared));
        }
    }

    private void transitionScopes(String oldLoc, String newLoc) {
        String oldScopePath = getFullScopePath(oldLoc);
        String newScopePath = getFullScopePath(newLoc);

        String[] oldParts = oldScopePath.split("/");
        String[] newParts = newScopePath.split("/");

        int lcaIndex = 0;
        while (lcaIndex < oldParts.length && lcaIndex < newParts.length && oldParts[lcaIndex].equals(newParts[lcaIndex])) {
            lcaIndex++;
        }

        // Clean up exited scopes
        StringBuilder exitedPath = new StringBuilder();
        for (int i = 0; i < oldParts.length; i++) {
            if (i > 0) exitedPath.append("/");
            exitedPath.append(oldParts[i]);
            String path = exitedPath.toString() + "/";
            if (i >= lcaIndex) {
                scopedStoryFlags.remove(path);
                activeCheckpoints.remove(path);
            }
        }

        // Initialize entered scopes
        StringBuilder enteredPath = new StringBuilder();
        for (int i = 0; i < newParts.length; i++) {
            if (i > 0) enteredPath.append("/");
            enteredPath.append(newParts[i]);
            String path = enteredPath.toString() + "/";
            if (i >= lcaIndex) {
                Map<String, Boolean> declared = declaredVariablesForScopePath(path);
                scopedStoryFlags.put(path, new LinkedHashMap<>(declared));
            }
        }
    }

    boolean getStoryFlag(String flagName) {
        boolean negate = false;
        if (flagName.startsWith("!")) {
            negate = true;
            flagName = flagName.substring(1);
        }

        String currentScopePath = getFullScopePath(location) + "/";
        String[] parts = currentScopePath.split("/");

        StringBuilder scopeBuilder = new StringBuilder();
        List<String> activePaths = new ArrayList<>();
        activePaths.add("/");
        
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (scopeBuilder.length() > 0) {
                scopeBuilder.append("/");
            }
            scopeBuilder.append(part);
            activePaths.add("/" + scopeBuilder.toString() + "/");
        }

        for (int i = activePaths.size() - 1; i >= 0; i--) {
            String path = activePaths.get(i);
            Map<String, Boolean> scopeFlags = scopedStoryFlags.get(path);
            if (scopeFlags != null && scopeFlags.containsKey(flagName)) {
                boolean val = scopeFlags.get(flagName);
                return negate ? !val : val;
            }
        }

        return negate;
    }

    void setStoryFlag(String flagName, boolean value) {
        String currentScopePath = getFullScopePath(location) + "/";
        String[] parts = currentScopePath.split("/");

        StringBuilder scopeBuilder = new StringBuilder();
        List<String> activePaths = new ArrayList<>();
        activePaths.add("/");
        
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (scopeBuilder.length() > 0) {
                scopeBuilder.append("/");
            }
            scopeBuilder.append(part);
            activePaths.add("/" + scopeBuilder.toString() + "/");
        }

        for (int i = activePaths.size() - 1; i >= 0; i--) {
            String path = activePaths.get(i);
            Map<String, Boolean> scopeFlags = scopedStoryFlags.get(path);
            if (scopeFlags != null && scopeFlags.containsKey(flagName)) {
                scopeFlags.put(flagName, value);
                return;
            }
        }

        String innermostPath = activePaths.get(activePaths.size() - 1);
        scopedStoryFlags.computeIfAbsent(innermostPath, k -> new LinkedHashMap<>()).put(flagName, value);
    }

    private boolean checkRequirement(String req) {
        if (req.startsWith("item:")) {
            String itemId = req.substring(5);
            return inventory.contains(itemId);
        } else if (req.startsWith("!item:")) {
            String itemId = req.substring(6);
            return !inventory.contains(itemId);
        } else if (req.startsWith("honor:")) {
            String opVal = req.substring(6);
            if (opVal.startsWith(">=")) {
                int val = Integer.parseInt(opVal.substring(2).trim());
                return honor >= val;
            } else if (opVal.startsWith("<=")) {
                int val = Integer.parseInt(opVal.substring(2).trim());
                return honor <= val;
            } else {
                int val = Integer.parseInt(opVal.trim());
                return honor >= val;
            }
        } else {
            return getStoryFlag(req);
        }
    }

    private void registerCheckpointIfNecessary(String loc) {
        Room r = world.room(loc);
        if (r != null && r.checkpoint()) {
            String innermostScope = getFullScopePath(loc) + "/";
            activeCheckpoints.put(innermostScope, loc);
        }
    }

    private String resolveActiveCheckpoint() {
        String currentScopePath = getFullScopePath(location) + "/";
        String[] parts = currentScopePath.split("/");

        StringBuilder scopeBuilder = new StringBuilder();
        List<String> activePaths = new ArrayList<>();
        activePaths.add("/");
        
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (scopeBuilder.length() > 0) {
                scopeBuilder.append("/");
            }
            scopeBuilder.append(part);
            activePaths.add("/" + scopeBuilder.toString() + "/");
        }

        for (int i = activePaths.size() - 1; i >= 0; i--) {
            String path = activePaths.get(i);
            String chk = activeCheckpoints.get(path);
            if (chk != null) {
                return chk;
            }
        }

        if (checkpointLocation != null) {
            return checkpointLocation;
        }
        return DEFAULT_CHECKPOINT;
    }

    public int getHonor() {
        return honor;
    }

    public List<String> getInventory() {
        return List.copyOf(inventory);
    }

    public void addHonor(int amt) {
        honor = Math.max(0, honor + amt);
    }

    public void gainItem(String item) {
        if (inventory.size() < 4) {
            inventory.add(item);
        } else {
            itemLocations.add(new GameTextCatalog.ItemPlacement(item, location));
        }
    }

    public void markLocationCombatCleared() {
        clearedCombatLocations.add(location);
    }

    public void triggerCombatFromDialogue(List<String> messages) {
        maybeStartCombat(messages);
    }

    public DialogueEngine getActiveDialogue() {
        return activeDialogue;
    }

    public ChallengeEngine getActiveChallenge() {
        return activeChallenge;
    }

    public void handleChallengeCompletion(List<String> messages) {
        if (activeChallenge == null || !activeChallenge.isFinished()) {
            return;
        }

        Room room = world.room(location);
        if (room == null || room.challenge() == null) {
            activeChallenge = null;
            return;
        }

        ChallengeDefinition.ChallengeResultDefinition resultDef;
        if (activeChallenge.isSuccess()) {
            resultDef = room.challenge().onSuccess();
            setStoryFlag(room.id() + "_challenge_cleared", true);
            messages.add("Challenge Succeeded!");
        } else {
            resultDef = room.challenge().onFailure();
            messages.add("Challenge Failed!");
        }

        if (resultDef != null) {
            if (resultDef.message() != null && !resultDef.message().isEmpty()) {
                messages.add(resultDef.message());
            }

            for (Map.Entry<String, Boolean> entry : resultDef.setFlags().entrySet()) {
                setStoryFlag(entry.getKey(), entry.getValue());
            }

            if (resultDef.itemGain() != null && !resultDef.itemGain().isEmpty()) {
                gainItem(resultDef.itemGain());
            }

            if (resultDef.honorGain() != 0) {
                addHonor(resultDef.honorGain());
            }
            if (resultDef.honorCost() > 0) {
                honor = Math.max(0, honor - resultDef.honorCost());
            }

            int totalJuiceDeduction = resultDef.juiceCost() + resultDef.damage();
            if (totalJuiceDeduction > 0) {
                appleJuice = Math.max(0, appleJuice - totalJuiceDeduction);
                if (appleJuice == 0) {
                    triggerDeathAndRespawn(messages, "You died from challenge injuries/exhaustion.");
                    activeChallenge = null;
                    return;
                }
            }

            if (!activeChallenge.isSuccess() && resultDef.failTarget() != null && !resultDef.failTarget().isEmpty()) {
                String target = resultDef.failTarget();
                transitionScopes(location, target);
                location = target;
                messages.add("Teleported to: " + world.room(location).title());
                triggerRoomEvents(messages);
            }
        }

        activeChallenge = null;
    }
}
