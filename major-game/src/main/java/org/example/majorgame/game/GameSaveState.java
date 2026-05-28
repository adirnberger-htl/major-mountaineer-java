package org.example.majorgame.game;

import java.util.Map;
import java.util.Set;

record GameSaveState(
        String locationId,
        String checkpointLocationId,
        int appleJuice,
        int honor,
        int zeroJuiceTurns,
        int commandsSinceRespawn,
        int movesSinceRespawn,
        int appleJuicesDrunkSinceRespawn,
        int deathCount,
        Set<String> inventory,
        Map<String, String> itemLocations,
        Set<String> clearedCombatLocations
) {
}
