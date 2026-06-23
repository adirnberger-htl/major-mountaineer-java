package org.example.majorgame.game;

import java.util.List;
import java.util.Map;
import org.example.majorgame.game.combat.CombatDataCatalog.EncounterDefinition;

public record Room(
        String id,
        String title,
        boolean checkpoint,
        Map<String, Boolean> variables,
        List<String> descriptionLines,
        List<String> dialogLines,
        String autoRouteMessage,
        Direction autoRouteDirection,
        Map<Direction, PathDefinition> paths,
        DialogueDefinition dialogue,
        EncounterDefinition encounter,
        ChallengeDefinition challenge
) {
    public Room(
            String id,
            String title,
            Boolean checkpoint,
            Map<String, Boolean> variables,
            List<String> descriptionLines,
            List<String> dialogLines,
            String autoRouteMessage,
            Direction autoRouteDirection,
            Map<Direction, PathDefinition> paths,
            DialogueDefinition dialogue,
            EncounterDefinition encounter,
            ChallengeDefinition challenge
    ) {
        this(
                id,
                title,
                checkpoint != null ? checkpoint : false,
                variables != null ? Map.copyOf(variables) : Map.of(),
                descriptionLines != null ? List.copyOf(descriptionLines) : List.of(),
                dialogLines != null ? List.copyOf(dialogLines) : List.of(),
                autoRouteMessage,
                autoRouteDirection,
                paths != null ? Map.copyOf(paths) : Map.of(),
                dialogue,
                encounter,
                challenge
        );
    }

    // Legacy constructor for backward compatibility
    public Room(
            String id,
            String title,
            List<String> descriptionLines,
            List<String> dialogLines,
            String autoRouteMessage,
            Direction autoRouteDirection
    ) {
        this(
                id,
                title,
                false,
                Map.of(),
                descriptionLines,
                dialogLines,
                autoRouteMessage,
                autoRouteDirection,
                Map.of(),
                null,
                null,
                null
        );
    }
}
