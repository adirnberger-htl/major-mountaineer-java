package org.example.majorgame.game.combat;

import java.util.List;

public final class CombatDataCatalog {
    private CombatDataCatalog() {}

    public record AbilityDefinition(
            String id,
            String name,
            int damage,
            int range
    ) {}

    public record EnemyDefinition(
            String id,
            String name,
            int maxHp,
            int movesPerTurn,
            List<String> weaponIds,
            GridPosition position,
            List<AbilityDefinition> abilities
    ) {}

    public record EncounterDefinition(
            String id,
            String location,
            int rows,
            int cols,
            GridPosition playerStart,
            List<EnemyDefinition> enemies
    ) {}

    public record GridPosition(
            int row,
            int col
    ) {}
}
