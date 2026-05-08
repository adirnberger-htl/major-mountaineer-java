package org.example.majorgame.game.combat;

public record CombatUnitSnapshot(
        String id,
        String name,
        int row,
        int col,
        int hp,
        int maxHp,
        boolean player
) {
}
