package org.example.majorgame.game.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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
    private final Set<String> playerAbilities;

    private int playerMoveModifier;
    private int playerMovesRemaining;
    private String selectedAbilityId;
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
        this.playerAbilities = new LinkedHashSet<>();
        this.playerAbilities.add("punch");
        this.playerMoveModifier = 0;
        this.playerMovesRemaining = effectivePlayerMoves();
    }

    public static CombatEngine start(CombatDataCatalog catalog, CombatDataCatalog.EncounterDefinition encounter) {
        CombatUnit player = new CombatUnit(
                PLAYER_UNIT_ID,
                PLAYER_NAME,
                PLAYER_MAX_HP,
                PLAYER_MAX_HP,
                PLAYER_BASE_MOVES,
                new CombatDataCatalog.GridPosition(encounter.playerStart().row(), encounter.playerStart().col()),
                List.of("punch"),
                PLAYER_WEAPON_ID,
                true
        );

        List<CombatUnit> enemies = new ArrayList<>();
        for (CombatDataCatalog.EnemySpawn enemySpawn : encounter.enemies()) {
            CombatDataCatalog.EnemyTypeDefinition enemyType = catalog.enemyType(enemySpawn.type());
            enemies.add(new CombatUnit(
                    enemySpawn.id(),
                    enemyType.name(),
                    enemyType.maxHp(),
                    enemyType.maxHp(),
                    enemyType.movesPerTurn(),
                    new CombatDataCatalog.GridPosition(enemySpawn.position().row(), enemySpawn.position().col()),
                    enemyType.abilities(),
                    enemyType.weapon(),
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
            return new CombatActionResult(List.of("Target an enemy for " + abilityName(selectedAbilityId) + "."));
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
        statusText = playerMovesRemaining > 0 ? "Move or choose attack." : "Choose your attack.";
        return new CombatActionResult(List.of("Major moves to [" + row + "," + col + "]."));
    }

    public CombatActionResult selectAbility(String abilityId) {
        if (finished) {
            return new CombatActionResult(List.of("The battle is already over."));
        }
        if (turnLocked) {
            return new CombatActionResult(List.of("Turn is locked. Press End Turn."));
        }
        if (!playerAbilities.contains(abilityId)) {
            return new CombatActionResult(List.of("Ability not available."));
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
            return new CombatActionResult(List.of("Choose an ability first."));
        }
        CombatUnit target = enemyAt(row, col);
        if (target == null) {
            return new CombatActionResult(List.of("Select an enemy tile."));
        }
        if (!targetableTiles().contains(new CombatTile(row, col))) {
            return new CombatActionResult(List.of("Target is out of range."));
        }

        List<String> messages = new ArrayList<>();
        applyDamage(player, target, selectedAbilityId, messages);
        selectedAbilityId = null;

        if (target.hp <= 0) {
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
                List.copyOf(playerAbilities),
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
            String abilityId = firstUsableAbility(enemy);
            if (abilityId != null) {
                applyDamage(enemy, player, abilityId, messages);
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
        if (rowDiff != 0 && inBounds(rowStep)) {
            return rowStep;
        }
        CombatDataCatalog.GridPosition colStep = new CombatDataCatalog.GridPosition(from.row(), from.col() + colDiff);
        if (colDiff != 0 && inBounds(colStep)) {
            return colStep;
        }
        return null;
    }

    private String firstUsableAbility(CombatUnit enemy) {
        for (String abilityId : enemy.abilities) {
            CombatDataCatalog.AbilityDefinition ability = catalog.ability(abilityId);
            if (distance(enemy.position, player.position) <= ability.range()) {
                return abilityId;
            }
        }
        return null;
    }

    private void applyDamage(CombatUnit attacker, CombatUnit defender, String abilityId, List<String> messages) {
        CombatDataCatalog.AbilityDefinition ability = catalog.ability(abilityId);
        int weaponBonus = catalog.weapon(attacker.weaponId).damageBonus();
        int damage = Math.max(1, ability.damage() + weaponBonus);
        defender.hp = Math.max(0, defender.hp - damage);
        messages.add(attacker.name + " uses " + ability.name() + " on " + defender.name + " for " + damage + " damage.");
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
        int range = catalog.ability(selectedAbilityId).range();
        Set<CombatTile> tiles = new LinkedHashSet<>();
        for (CombatUnit enemy : livingEnemies()) {
            if (distance(player.position, enemy.position) <= range) {
                tiles.add(new CombatTile(enemy.position.row(), enemy.position.col()));
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

    private CombatUnit enemyAt(int row, int col) {
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

    private String abilityName(String abilityId) {
        return catalog.ability(abilityId).name();
    }

    private static final class CombatUnit {
        private final String id;
        private final String name;
        private int hp;
        private final int maxHp;
        private final int movesPerTurn;
        private CombatDataCatalog.GridPosition position;
        private final List<String> abilities;
        private final String weaponId;
        private final boolean player;

        private CombatUnit(
                String id,
                String name,
                int hp,
                int maxHp,
                int movesPerTurn,
                CombatDataCatalog.GridPosition position,
                List<String> abilities,
                String weaponId,
                boolean player
        ) {
            this.id = id;
            this.name = name;
            this.hp = hp;
            this.maxHp = maxHp;
            this.movesPerTurn = movesPerTurn;
            this.position = position;
            this.abilities = abilities;
            this.weaponId = weaponId;
            this.player = player;
        }
    }
}
