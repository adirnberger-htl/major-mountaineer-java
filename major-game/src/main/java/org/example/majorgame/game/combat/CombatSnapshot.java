package org.example.majorgame.game.combat;

import java.util.List;
import java.util.Set;

public record CombatSnapshot(
        int rows,
        int cols,
        List<CombatUnitSnapshot> units,
        boolean playerTurn,
        int playerMovesRemaining,
        List<String> playerAbilityIds,
        String selectedAbilityId,
        Set<CombatTile> reachableTiles,
        Set<CombatTile> targetableTiles,
        String statusText
) {
}
