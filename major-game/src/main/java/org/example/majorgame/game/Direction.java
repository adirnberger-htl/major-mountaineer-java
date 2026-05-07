package org.example.majorgame.game;

public enum Direction {
    N, S, E, W;

    public static Direction fromToken(String token) {
        return switch (token.toLowerCase()) {
            case "n" -> N;
            case "s" -> S;
            case "e" -> E;
            case "w" -> W;
            default -> null;
        };
    }
}
