package org.example.majorgame.game;

import java.util.Map;

public record ChallengeDefinition(
        String type,
        String difficulty,
        ChallengeResultDefinition onSuccess,
        ChallengeResultDefinition onFailure
) {
    public record ChallengeResultDefinition(
            Map<String, Boolean> setFlags,
            String itemGain,
            int honorGain,
            int honorCost,
            int juiceCost,
            int damage,
            String failTarget,
            String message
    ) {
        public ChallengeResultDefinition(
                Map<String, Boolean> setFlags,
                String itemGain,
                Integer honorGain,
                Integer honorCost,
                Integer juiceCost,
                Integer damage,
                String failTarget,
                String message
        ) {
            this(
                    setFlags != null ? Map.copyOf(setFlags) : Map.of(),
                    itemGain,
                    honorGain != null ? honorGain : 0,
                    honorCost != null ? honorCost : 0,
                    juiceCost != null ? juiceCost : 0,
                    damage != null ? damage : 0,
                    failTarget,
                    message
            );
        }
    }
}
