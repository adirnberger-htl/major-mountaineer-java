package org.example.majorgame.game;

import org.example.majorgame.game.combat.CombatSnapshot;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record GameSnapshot(
        String locationId,
        String locationTitle,
        List<String> locationDescription,
        List<String> locationDialog,
        Map<Direction, String> availableDirections,
        int appleJuice,
        int honor,
        int zeroJuiceTurns,
        List<String> inventory,
        List<String> visibleItems,
        boolean freshRespawn,
        boolean inCombat,
        CombatSnapshot combatSnapshot
) {
    public String locationPrompt() {
       return locationTitle + " " + String.join(" ", locationDescription);
    }
}
