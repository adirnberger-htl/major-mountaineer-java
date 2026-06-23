package org.example.majorgame.game;

import java.util.List;
import java.util.Map;

public record DialogueDefinition(
        String startNode,
        Map<String, DialogueNode> nodes
) {
    public record DialogueChoice(
            String text,
            String targetNode,
            int requiredHonor,
            String requiredItem,
            String failMessage,
            Map<String, Boolean> setFlags
    ) {
        public DialogueChoice(
                String text,
                String targetNode,
                Integer requiredHonor,
                String requiredItem,
                String failMessage,
                Map<String, Boolean> setFlags
        ) {
            this(
                    text,
                    targetNode,
                    requiredHonor != null ? requiredHonor : 0,
                    requiredItem,
                    failMessage,
                    setFlags != null ? Map.copyOf(setFlags) : Map.of()
            );
        }
    }

    public record DialogueNode(
            String speaker,
            String text,
            List<DialogueChoice> choices,
            boolean endDialogue,
            boolean befriended,
            boolean startCombat,
            String itemGain,
            int honorGain,
            Map<String, Boolean> setFlags
    ) {
        public DialogueNode(
                String speaker,
                String text,
                List<DialogueChoice> choices,
                Boolean endDialogue,
                Boolean befriended,
                Boolean startCombat,
                String itemGain,
                Integer honorGain,
                Map<String, Boolean> setFlags
        ) {
            this(
                    speaker,
                    text,
                    choices != null ? List.copyOf(choices) : List.of(),
                    endDialogue != null ? endDialogue : false,
                    befriended != null ? befriended : false,
                    startCombat != null ? startCombat : false,
                    itemGain,
                    honorGain != null ? honorGain : 0,
                    setFlags != null ? Map.copyOf(setFlags) : Map.of()
            );
        }
    }
}
