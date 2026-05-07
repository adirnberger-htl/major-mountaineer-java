package org.example.majorgame.game;

import java.util.List;

public record GameResult(
        List<String> messages,
        GameSnapshot snapshot
) {
}
