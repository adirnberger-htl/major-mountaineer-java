package org.example.majorgame.game.combat;

import org.example.majorgame.game.GameTextCatalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CombatEngine {
    private static final int PLAYER_MAX_HP = 28;
    private static final int PLAYER_BASE_MOVES = 1;
    private static final String PLAYER_UNIT_ID = "player_major";
    private static final String PLAYER_NAME = "Major";
    private static final String PLAYER_WEAPON_ID = "fists";

    private final CombatDataCatalog catalog;
    private final CombatDataCatalog.EncounterDefinition encounter;
    private final CombatUnit player;
    private final List<CombatUnit> enemies;

    private int playerMoveModifier;
    private int playerMovesRemaining;
    private String selectedAbilityId; // acts as selectedActionId
    private boolean turnLocked;
    private String statusText = "Choose your move.";
    private boolean finished;
    private boolean playerWon;

    private CombatEngine(
            CombatDataCatalog catalog,
            CombatDataCatalog.EncounterDefinition encounter,
            CombatUnit player,
            List<CombatUnit> enemies
    ) {
        this.catalog = catalog;
        this.encounter = encounter;
        this.player = player;
        this.enemies = enemies;
        this.playerMoveModifier = 0;
        this.playerMovesRemaining = effectivePlayerMoves();
    }

    public static CombatEngine start(
            CombatDataCatalog catalog,
            CombatDataCatalog.EncounterDefinition encounter,
            Collection<String> playerInventory
    ) {
        Map<String, CombatAction> playerActions = new LinkedHashMap<>();

        // Add default fists weapon
        GameTextCatalog.ItemDefinition fistsDef = GameTextCatalog.defaultCatalog().items().get(PLAYER_WEAPON_ID);
        if (fistsDef != null && fistsDef.isWeapon()) {
            playerActions.put(PLAYER_WEAPON_ID, new CombatAction(
                    PLAYER_WEAPON_ID, fistsDef.name(), fistsDef.getDamage(), fistsDef.getRange(), true
            ));
        } else {
            playerActions.put(PLAYER_WEAPON_ID, new CombatAction(PLAYER_WEAPON_ID, "Fists", 5, 1, true));
        }

        // Add weapons from player's inventory
        for (String itemId : playerInventory) {
            GameTextCatalog.ItemDefinition item = GameTextCatalog.defaultCatalog().items().get(itemId);
            if (item != null && item.isWeapon()) {
                playerActions.put(itemId, new CombatAction(
                        itemId, item.name(), item.getDamage(), item.getRange(), true
                ));
            }
        }

        CombatUnit player = new CombatUnit(
                PLAYER_UNIT_ID,
                PLAYER_NAME,
                PLAYER_MAX_HP,
                PLAYER_MAX_HP,
                PLAYER_BASE_MOVES,
                new CombatDataCatalog.GridPosition(encounter.playerStart().row(), encounter.playerStart().col()),
                playerActions,
                true
        );

        List<CombatUnit> enemies = new ArrayList<>();
        for (CombatDataCatalog.EnemyDefinition enemy : encounter.enemies()) {
            Map<String, CombatAction> enemyActions = new LinkedHashMap<>();

            // Add enemy's weapons
            for (String weaponId : enemy.weaponIds()) {
                GameTextCatalog.ItemDefinition weaponDef = GameTextCatalog.defaultCatalog().items().get(weaponId);
                if (weaponDef != null && weaponDef.isWeapon()) {
                    enemyActions.put(weaponId, new CombatAction(
                            weaponId, weaponDef.name(), weaponDef.getDamage(), weaponDef.getRange(), true
                    ));
                }
            }

            // Add enemy's inline abilities
            for (CombatDataCatalog.AbilityDefinition ab : enemy.abilities()) {
                enemyActions.put(ab.id(), new CombatAction(
                        ab.id(), ab.name(), ab.damage(), ab.range(), false
                ));
            }

            enemies.add(new CombatUnit(
                    enemy.id(),
                    enemy.name(),
                    enemy.maxHp(),
                    enemy.maxHp(),
                    enemy.movesPerTurn(),
                    new CombatDataCatalog.GridPosition(enemy.position().row(), enemy.position().col()),
                    enemyActions,
                    false
            ));
        }

        return new CombatEngine(catalog, encounter, player, enemies);
    }

    public List<String> openingMessages() {
        return List.of(
                "A fight begins in " + encounter.location() + "!",
                "Enemy appears: " + enemies.stream().map(unit -> unit.name).distinct().collect(java.util.stream.Collectors.joining(", "))
        );
    }

    public CombatActionResult movePlayer(int row, int col) {
        if (finished) {
            return new CombatActionResult(List.of("The battle is already over."));
        }
        if (turnLocked) {
            return new CombatActionResult(List.of("Turn is locked. Press End Turn."));
        }
        if (selectedAbilityId != null) {
            return new CombatActionResult(List.of("Target a unit for " + abilityName(selectedAbilityId) + "."));
        }
        if (playerMovesRemaining <= 0) {
            return new CombatActionResult(List.of("No movement points left this turn."));
        }

        CombatDataCatalog.GridPosition target = new CombatDataCatalog.GridPosition(row, col);
        if (!reachableTiles().contains(new CombatTile(row, col))) {
            return new CombatActionResult(List.of("You can only move one tile orthogonally."));
        }
        if (isOccupied(target)) {
            return new CombatActionResult(List.of("That tile is occupied."));
        }

        player.position = target;
        playerMovesRemaining -= 1;
        statusText = playerMovesRemaining > 0 ? "Move or choose action." : "Choose your action.";
        return new CombatActionResult(List.of("Major moves to [" + row + "," + col + "]."));
    }

    public CombatActionResult selectAbility(String abilityId) {
        if (finished) {
            return new CombatActionResult(List.of("The battle is already over."));
        }
        if (turnLocked) {
            return new CombatActionResult(List.of("Turn is locked. Press End Turn."));
        }
        if (!player.actions.containsKey(abilityId)) {
            return new CombatActionResult(List.of("Action not available."));
        }
        selectedAbilityId = abilityId;
        Set<CombatTile> targetable = targetableTiles();
        statusText = targetable.isEmpty()
                ? "No targets in range. Move closer or choose another action."
                : "Choose a target for " + abilityName(abilityId) + ".";
        return new CombatActionResult(List.of("Selected " + abilityName(abilityId) + "."));
    }

    public CombatActionResult targetAt(int row, int col) {
        if (finished) {
            return new CombatActionResult(List.of("The battle is already over."));
        }
        if (turnLocked) {
            return new CombatActionResult(List.of("Turn is locked. Press End Turn."));
        }
        if (selectedAbilityId == null) {
            return new CombatActionResult(List.of("Choose an action first."));
        }
        CombatUnit target = unitAt(row, col);
        if (target == null) {
            return new CombatActionResult(List.of("Select a unit tile."));
        }
        if (!targetableTiles().contains(new CombatTile(row, col))) {
            return new CombatActionResult(List.of("Target is out of range."));
        }

        List<String> messages = new ArrayList<>();
        applyDamage(player, target, selectedAbilityId, messages);
        selectedAbilityId = null;

        if (target.hp <= 0 && !target.player) {
            messages.add(target.name + " is defeated.");
        }
        if (livingEnemies().isEmpty()) {
            finished = true;
            playerWon = true;
            statusText = "Victory!";
            messages.add("You won the fight.");
            return new CombatActionResult(List.copyOf(messages));
        }

        turnLocked = true;
        playerMovesRemaining = 0;
        statusText = "Action locked. Press End Turn to continue.";
        messages.add("Action locked. Press End Turn.");
        return new CombatActionResult(List.copyOf(messages));
    }

    public CombatActionResult endTurn() {
        if (finished) {
            return new CombatActionResult(List.of("The battle is already over."));
        }
        List<String> messages = new ArrayList<>();
        if (selectedAbilityId != null) {
            selectedAbilityId = null;
            messages.add("Ability selection canceled.");
        }
        enemyTurn(messages);
        if (player.hp <= 0) {
            finished = true;
            playerWon = false;
            statusText = "Defeat...";
            messages.add("Major collapses in battle.");
            return new CombatActionResult(List.copyOf(messages));
        }
        if (livingEnemies().isEmpty()) {
            finished = true;
            playerWon = true;
            statusText = "Victory!";
            messages.add("You won the fight.");
            return new CombatActionResult(List.copyOf(messages));
        }
        turnLocked = false;
        playerMovesRemaining = effectivePlayerMoves();
        statusText = "Your turn.";
        if (messages.isEmpty()) {
            messages.add("Turn ended.");
        }
        return new CombatActionResult(List.copyOf(messages));
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean playerWon() {
        return playerWon;
    }

    public CombatSnapshot snapshot() {
        List<CombatUnitSnapshot> units = new ArrayList<>();
        units.add(new CombatUnitSnapshot(
                player.id,
                player.name,
                player.position.row(),
                player.position.col(),
                Math.max(0, player.hp),
                player.maxHp,
                true
        ));
        for (CombatUnit enemy : livingEnemies()) {
            units.add(new CombatUnitSnapshot(
                    enemy.id,
                    enemy.name,
                    enemy.position.row(),
                    enemy.position.col(),
                    enemy.hp,
                    enemy.maxHp,
                    false
            ));
        }
        units.sort(Comparator.comparing(CombatUnitSnapshot::id));
        return new CombatSnapshot(
                encounter.rows(),
                encounter.cols(),
                List.copyOf(units),
                !finished,
                turnLocked,
                !finished,
                playerMovesRemaining,
                List.copyOf(player.actions.keySet()),
                selectedAbilityId,
                reachableTiles(),
                targetableTiles(),
                statusText
        );
    }

    private void enemyTurn(List<String> messages) {
        for (CombatUnit enemy : livingEnemies()) {
            if (player.hp <= 0) {
                return;
            }
            moveEnemyTowardPlayer(enemy, messages);
            String actionId = firstUsableAction(enemy);
            if (actionId != null) {
                CombatAction action = enemy.actions.get(actionId);
                if (action.damage() < 0) {
                    applyDamage(enemy, enemy, actionId, messages);
                } else {
                    applyDamage(enemy, player, actionId, messages);
                }
            }
        }
    }

    private void moveEnemyTowardPlayer(CombatUnit enemy, List<String> messages) {
        int steps = Math.max(0, enemy.movesPerTurn);
        for (int i = 0; i < steps; i++) {
            CombatDataCatalog.GridPosition next = stepToward(enemy.position, player.position);
            if (next == null || isOccupied(next)) {
                break;
            }
            enemy.position = next;
            messages.add(enemy.name + " moves to [" + next.row() + "," + next.col() + "].");
        }
    }

    private CombatDataCatalog.GridPosition stepToward(
            CombatDataCatalog.GridPosition from,
            CombatDataCatalog.GridPosition to
    ) {
        int rowDiff = Integer.compare(to.row(), from.row());
        int colDiff = Integer.compare(to.col(), from.col());

        CombatDataCatalog.GridPosition rowStep = new CombatDataCatalog.GridPosition(from.row() + rowDiff, from.col());
        if (rowDiff != 0 && inBounds(rowStep) && !isOccupied(rowStep)) {
            return rowStep;
        }
        CombatDataCatalog.GridPosition colStep = new CombatDataCatalog.GridPosition(from.row(), from.col() + colDiff);
        if (colDiff != 0 && inBounds(colStep) && !isOccupied(colStep)) {
            return colStep;
        }
        return null;
    }

    private String firstUsableAction(CombatUnit enemy) {
        for (Map.Entry<String, CombatAction> entry : enemy.actions.entrySet()) {
            CombatAction action = entry.getValue();
            if (action.damage() < 0) {
                if (enemy.hp < enemy.maxHp) {
                    return entry.getKey();
                }
            } else {
                if (distance(enemy.position, player.position) <= action.range()) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private void applyDamage(CombatUnit attacker, CombatUnit defender, String actionId, List<String> messages) {
        CombatAction action = attacker.actions.get(actionId);
        if (action == null) {
            return;
        }
        int damage = action.damage();
        if (action.isWeapon()) {
            defender.hp = Math.max(0, defender.hp - damage);
            messages.add(attacker.name + " attacks " + defender.name + " with " + action.name() + " for " + damage + " damage.");
        } else {
            if (damage < 0) {
                int healAmount = -damage;
                defender.hp = Math.min(defender.maxHp, defender.hp + healAmount);
                messages.add(attacker.name + " uses " + action.name() + " on " + defender.name + ", healing " + healAmount + " HP.");
            } else {
                defender.hp = Math.max(0, defender.hp - damage);
                messages.add(attacker.name + " uses " + action.name() + " on " + defender.name + " for " + damage + " damage.");
            }
        }
    }

    private boolean isOccupied(CombatDataCatalog.GridPosition position) {
        if (player.hp > 0 && sameTile(player.position, position)) {
            return true;
        }
        return livingEnemies().stream().anyMatch(enemy -> sameTile(enemy.position, position));
    }

    private Set<CombatTile> reachableTiles() {
        if (finished || turnLocked || selectedAbilityId != null || playerMovesRemaining <= 0) {
            return Set.of();
        }
        Set<CombatTile> tiles = new LinkedHashSet<>();
        addReachableTile(tiles, player.position.row() - 1, player.position.col());
        addReachableTile(tiles, player.position.row() + 1, player.position.col());
        addReachableTile(tiles, player.position.row(), player.position.col() - 1);
        addReachableTile(tiles, player.position.row(), player.position.col() + 1);
        return Set.copyOf(tiles);
    }

    private Set<CombatTile> targetableTiles() {
        if (finished || turnLocked || selectedAbilityId == null) {
            return Set.of();
        }
        CombatAction action = player.actions.get(selectedAbilityId);
        if (action == null) {
            return Set.of();
        }
        int range = action.range();
        Set<CombatTile> tiles = new LinkedHashSet<>();
        if (action.damage() < 0) {
            tiles.add(new CombatTile(player.position.row(), player.position.col()));
        } else {
            for (CombatUnit enemy : livingEnemies()) {
                if (distance(player.position, enemy.position) <= range) {
                    tiles.add(new CombatTile(enemy.position.row(), enemy.position.col()));
                }
            }
        }
        return Set.copyOf(tiles);
    }

    private void addReachableTile(Set<CombatTile> tiles, int row, int col) {
        CombatDataCatalog.GridPosition position = new CombatDataCatalog.GridPosition(row, col);
        if (inBounds(position) && !isOccupied(position)) {
            tiles.add(new CombatTile(row, col));
        }
    }

    private CombatUnit unitAt(int row, int col) {
        if (player.position.row() == row && player.position.col() == col) {
            return player;
        }
        for (CombatUnit enemy : livingEnemies()) {
            if (enemy.position.row() == row && enemy.position.col() == col) {
                return enemy;
            }
        }
        return null;
    }

    private List<CombatUnit> livingEnemies() {
        return enemies.stream().filter(enemy -> enemy.hp > 0).toList();
    }

    private int distance(CombatDataCatalog.GridPosition a, CombatDataCatalog.GridPosition b) {
        return Math.abs(a.row() - b.row()) + Math.abs(a.col() - b.col());
    }

    private int effectivePlayerMoves() {
        return Math.max(0, PLAYER_BASE_MOVES + playerMoveModifier);
    }

    private boolean inBounds(CombatDataCatalog.GridPosition position) {
        return position.row() >= 0
                && position.col() >= 0
                && position.row() < encounter.rows()
                && position.col() < encounter.cols();
    }

    private boolean sameTile(CombatDataCatalog.GridPosition a, CombatDataCatalog.GridPosition b) {
        return a.row() == b.row() && a.col() == b.col();
    }

    private String abilityName(String actionId) {
        CombatAction action = player.actions.get(actionId);
        return action != null ? action.name() : actionId;
    }

    public record CombatAction(
            String id,
            String name,
            int damage,
            int range,
            boolean isWeapon
    ) {
    }

    private static final class CombatUnit {
        private final String id;
        private final String name;
        private int hp;
        private final int maxHp;
        private final int movesPerTurn;
        private CombatDataCatalog.GridPosition position;
        private final Map<String, CombatAction> actions;
        private final boolean player;

        private CombatUnit(
                String id,
                String name,
                int hp,
                int maxHp,
                int movesPerTurn,
                CombatDataCatalog.GridPosition position,
                Map<String, CombatAction> actions,
                boolean player
        ) {
            this.id = id;
            this.name = name;
            this.hp = hp;
            this.maxHp = maxHp;
            this.movesPerTurn = movesPerTurn;
            this.position = position;
            this.actions = actions;
            this.player = player;
        }
    }
}
