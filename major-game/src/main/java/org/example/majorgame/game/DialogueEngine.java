package org.example.majorgame.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DialogueEngine {
    private final DialogueDefinition definition;
    private final GameEngine game;
    private String currentNodeId;

    public DialogueEngine(DialogueDefinition definition, GameEngine game) {
        this.definition = definition;
        this.game = game;
        this.currentNodeId = definition.startNode();
        applyNodeEffects(getCurrentNode());
    }

    public DialogueDefinition.DialogueNode getCurrentNode() {
        return definition.nodes().get(currentNodeId);
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public List<DialogueChoiceStatus> getChoicesStatus() {
        DialogueDefinition.DialogueNode node = getCurrentNode();
        if (node == null || node.choices() == null) {
            return List.of();
        }
        List<DialogueChoiceStatus> result = new ArrayList<>();
        for (DialogueDefinition.DialogueChoice choice : node.choices()) {
            boolean allowed = true;
            String reason = null;
            if (choice.requiredHonor() > 0 && game.getHonor() < choice.requiredHonor()) {
                allowed = false;
                reason = choice.failMessage() != null ? choice.failMessage() : "Requires " + choice.requiredHonor() + " honor.";
            } else if (choice.requiredItem() != null && !choice.requiredItem().isEmpty() && !game.getInventory().contains(choice.requiredItem())) {
                allowed = false;
                reason = choice.failMessage() != null ? choice.failMessage() : "Requires item: " + choice.requiredItem();
            }
            result.add(new DialogueChoiceStatus(choice, allowed, reason));
        }
        return result;
    }

    public DialogueChoiceResult choose(int index, List<String> messages) {
        List<DialogueChoiceStatus> statuses = getChoicesStatus();
        if (index < 0 || index >= statuses.size()) {
            return new DialogueChoiceResult(false, "Invalid choice index.", false);
        }
        DialogueChoiceStatus status = statuses.get(index);
        if (!status.allowed()) {
            return new DialogueChoiceResult(false, status.failReason(), false);
        }

        DialogueDefinition.DialogueChoice choice = status.choice();
        
        // Apply choice setFlags
        for (Map.Entry<String, Boolean> entry : choice.setFlags().entrySet()) {
            game.setStoryFlag(entry.getKey(), entry.getValue());
        }

        currentNodeId = choice.targetNode();
        DialogueDefinition.DialogueNode node = getCurrentNode();
        if (node == null) {
            return new DialogueChoiceResult(true, "Dialogue ended abruptly.", true);
        }

        applyNodeEffects(node);

        if (node.endDialogue()) {
            if (node.befriended()) {
                game.markLocationCombatCleared();
                messages.add("You befriended the local population. Combat cleared.");
            }
            if (node.startCombat()) {
                game.triggerCombatFromDialogue(messages);
            }
            return new DialogueChoiceResult(true, node.text(), true);
        }

        return new DialogueChoiceResult(true, node.text(), false);
    }

    private void applyNodeEffects(DialogueDefinition.DialogueNode node) {
        if (node == null) return;
        if (node.honorGain() != 0) {
            game.addHonor(node.honorGain());
        }
        if (node.itemGain() != null && !node.itemGain().isEmpty()) {
            game.gainItem(node.itemGain());
        }
        for (Map.Entry<String, Boolean> entry : node.setFlags().entrySet()) {
            game.setStoryFlag(entry.getKey(), entry.getValue());
        }
    }

    public record DialogueChoiceStatus(
            DialogueDefinition.DialogueChoice choice,
            boolean allowed,
            String failReason
    ) {}

    public record DialogueChoiceResult(
            boolean success,
            String text,
            boolean ended
    ) {}
}
