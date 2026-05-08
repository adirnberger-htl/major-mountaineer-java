package org.example.majorgame.game;

import java.util.LinkedHashMap;
import java.util.Map;

final class GameWorld {
    private static final GameTextCatalog TEXTS = GameTextCatalog.defaultCatalog();

    private final Map<String, Room> rooms;
    private final Map<String, Map<Direction, String>> paths;
    private final Map<String, String> initialItemLocations;

    private GameWorld(
            Map<String, Room> rooms,
            Map<String, Map<Direction, String>> paths,
            Map<String, String> initialItemLocations
    ) {
        this.rooms = rooms;
        this.paths = paths;
        this.initialItemLocations = initialItemLocations;
    }

    static GameWorld createDefault() {
        Map<String, Room> rooms = new LinkedHashMap<>();
        addRoom(rooms, "mud_huzz", null);
        addRoom(rooms, "train_station", null);
        addRoom(rooms, "storage_building", null);
        addRoom(rooms, "arm_chair", null);
        addRoom(rooms, "hohe_dirn", null);
        addRoom(rooms, "traunstein", Direction.E);
        addRoom(rooms, "grosser_priel", Direction.E);

        Map<String, Map<Direction, String>> paths = new LinkedHashMap<>();
        addPath(paths, "mud_huzz", Direction.N, "train_station");
        addPath(paths, "train_station", Direction.S, "mud_huzz");
        addPath(paths, "mud_huzz", Direction.E, "storage_building");
        addPath(paths, "storage_building", Direction.W, "mud_huzz");
        addPath(paths, "mud_huzz", Direction.W, "arm_chair");
        addPath(paths, "arm_chair", Direction.E, "mud_huzz");
        addPath(paths, "train_station", Direction.E, "hohe_dirn");
        addPath(paths, "train_station", Direction.N, "traunstein");
        addPath(paths, "train_station", Direction.W, "grosser_priel");
        addPath(paths, "traunstein", Direction.E, "hohe_dirn");
        addPath(paths, "grosser_priel", Direction.E, "hohe_dirn");

        Map<String, String> initialItems = new LinkedHashMap<>();
        initialItems.put("apple_juice", "storage_building");

        return new GameWorld(rooms, paths, initialItems);
    }

    private static void addRoom(Map<String, Room> rooms, String roomId, Direction autoRouteDirection) {
        GameTextCatalog.LocationText locationText = TEXTS.location(roomId);
        rooms.put(roomId, new Room(
                roomId,
                locationText.title(),
                locationText.descriptionLines(),
                locationText.autoRouteMessage(),
                autoRouteDirection
        ));
    }

    private static void addPath(Map<String, Map<Direction, String>> paths, String from, Direction direction, String to) {
        paths.computeIfAbsent(from, key -> new LinkedHashMap<>()).put(direction, to);
    }

    Room room(String roomId) {
        return rooms.get(roomId);
    }

    String nextLocation(String from, Direction direction) {
        return paths.getOrDefault(from, Map.of()).get(direction);
    }

    Map<String, String> initialItemLocations() {
        return new LinkedHashMap<>(initialItemLocations);
    }
}
