package org.example.majorgame.game;

public enum ChallengeType {
    BALANCE_BAR;

    public static ChallengeType fromString(String type) {
        if (type == null) return null;
        if ("balance_bar".equalsIgnoreCase(type)) {
            return BALANCE_BAR;
        }
        return null;
    }
}
