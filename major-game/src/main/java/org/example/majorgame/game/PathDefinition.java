package org.example.majorgame.game;

import java.util.List;

public record PathDefinition(
        String targetLocation,
        String label,
        List<String> requireFlags,
        String failMessage,
        int juiceCost,
        int honorGain,
        String itemGain
) {
    public PathDefinition(
            String targetLocation,
            String label,
            List<String> requireFlags,
            String failMessage,
            Integer juiceCost,
            Integer honorGain,
            String itemGain
    ) {
        this(
                targetLocation,
                label,
                requireFlags != null ? List.copyOf(requireFlags) : List.of(),
                failMessage,
                juiceCost != null ? juiceCost : 0,
                honorGain != null ? honorGain : 0,
                itemGain
        );
    }
}
