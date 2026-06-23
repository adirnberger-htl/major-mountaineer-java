package org.example.majorgame.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GameWorld {

    private final Map<String, Room> rooms;
    private final Map<String, Map<Direction, String>> paths;
    private final List<GameTextCatalog.ItemPlacement> initialItemLocations;

    private GameWorld(
            Map<String, Room> rooms,
            Map<String, Map<Direction, String>> paths,
            List<GameTextCatalog.ItemPlacement> initialItemLocations
    ) {
        this.rooms = rooms;
        this.paths = paths;
        this.initialItemLocations = initialItemLocations;
    }

    static GameWorld createDefault() {
        return create(GameTextCatalog.defaultCatalog());
    }

    static GameWorld create(GameTextCatalog catalog) {
        Map<String, Room> rooms = catalog.flatRooms();

        Map<String, Map<Direction, String>> paths = catalog.paths();
        // Programmatically inject missing return paths to avoid touching yml config files.
        if (rooms.containsKey("hd_homeless")) {
            paths.computeIfAbsent("hd_homeless", k -> new LinkedHashMap<>()).put(Direction.S, "hd_slums|back to the slums.");
        }
        if (rooms.containsKey("hd_official_start")) {
            paths.computeIfAbsent("hd_official_start", k -> new LinkedHashMap<>()).put(Direction.S, "hd_wood_shed|back to the wood shed.");
        }
        if (rooms.containsKey("hohe_dirn")) {
            paths.computeIfAbsent("hohe_dirn", k -> new LinkedHashMap<>()).put(Direction.W, "train_station|back to the train station.");
        }
        if (rooms.containsKey("hd_slums")) {
            paths.computeIfAbsent("hd_slums", k -> new LinkedHashMap<>()).put(Direction.S, "hohe_dirn|back to the HOHE DIRN base.");
        }

        validatePaths(rooms, paths);

        List<GameTextCatalog.ItemPlacement> initialItems = catalog.initialItems();
        validateInitialItems(rooms, initialItems);

        return new GameWorld(rooms, paths, initialItems);
    }

    private static void validatePaths(Map<String, Room> rooms, Map<String, Map<Direction, String>> paths) {
        for (Map.Entry<String, Map<Direction, String>> fromEntry : paths.entrySet()) {
            String from = fromEntry.getKey();
            if (!rooms.containsKey(from)) {
                throw new IllegalStateException("Path source room is unknown: " + from);
            }
            for (String to : fromEntry.getValue().values()) {
                String targetRoomId = to.split("\\|", 2)[0];
                if (!rooms.containsKey(targetRoomId)) {
                    throw new IllegalStateException("Path target room is unknown: " + targetRoomId);
                }
            }
        }
    }

    private static void validateInitialItems(Map<String, Room> rooms, List<GameTextCatalog.ItemPlacement> initialItems) {
        for (GameTextCatalog.ItemPlacement placement : initialItems) {
            String itemId = placement.itemId();
            String location = placement.locationId();
            if (!rooms.containsKey(location)) {
                throw new IllegalStateException("Item '" + itemId + "' references unknown room: " + location);
            }
        }
    }

    Room room(String roomId) {
        return rooms.get(roomId);
    }

    String nextLocation(String from, Direction direction) {
        String rawTarget = paths.getOrDefault(from, Map.of()).get(direction);
        if (rawTarget == null) return null;
        return rawTarget.split("\\|", 2)[0];
    }

    Map<Direction, String> availableExits(String roomId) {
        Map<Direction, String> roomPaths = paths.getOrDefault(roomId, Map.of());
        Map<Direction, String> exits = new LinkedHashMap<>();
        for (Map.Entry<Direction, String> entry : roomPaths.entrySet()) {
            String rawTarget = entry.getValue();
            String[] parts = rawTarget.split("\\|", 2);
            Room dest = room(parts[0]);
            if (dest != null) {
                String desc = parts.length > 1 ? " - " + parts[1] : "";
                exits.put(entry.getKey(), dest.title() + desc);
            }
        }
        return exits;
    }

    List<GameTextCatalog.ItemPlacement> initialItemLocations() {
        return List.copyOf(initialItemLocations);
    }

    void appendDialog(String roomId, String message) {
        Room current = rooms.get(roomId);
        if (current != null) {
            java.util.List<String> newDialogs = new java.util.ArrayList<>(current.dialogLines());
            newDialogs.add(message);
            rooms.put(roomId, new Room(
                    current.id(),
                    current.title(),
                    current.descriptionLines(),
                    java.util.List.copyOf(newDialogs),
                    current.autoRouteMessage(),
                    current.autoRouteDirection()
            ));
        }
    }
}
