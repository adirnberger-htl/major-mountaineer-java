package org.example.majorgame.game;

import java.util.List;

record Room(
        String id,
        String title,
        List<String> descriptionLines,
        String autoRouteMessage,
        Direction autoRouteDirection
) {
}
