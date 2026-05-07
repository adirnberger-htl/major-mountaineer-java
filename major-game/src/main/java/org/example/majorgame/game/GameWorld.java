package org.example.majorgame.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GameWorld {
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
        rooms.put("mud_huzz", new Room(
                "mud_huzz",
                "Mud Huzz",
                List.of(
                        "You are in your mud huzz, your home.",
                        "EAST there is your storage building with your chest.",
                        "NORTH the train station from which you will start your adventures is located."
                ),
                null,
                null
        ));
        rooms.put("train_station", new Room(
                "train_station",
                "Train Station",
                List.of(
                        "You are at the trusty ol train station. Here only one train leaves and arrives evrery day.",
                        "EAST you can attempt the HOHE DIRN hike",
                        "NORTH you try your luck in at the TRAUNSTEIN.",
                        "WEST you can do the hardest tour, the GROSSE PRIEL.",
                        "SOUTH you can return to your mud huzz."
                ),
                null,
                null
        ));
        rooms.put("storage_building", new Room(
                "storage_building",
                "Storage Building",
                List.of(
                        "You are in your storage building where your supplies are kept.",
                        "WEST you can return to your mud huzz."
                ),
                null,
                null
        ));
        rooms.put("arm_chair", new Room(
                "arm_chair",
                "The Living Room",
                List.of(
                        "You look at the empty arm chair where your wife used to sit.",
                        "Its lonely ever since she died, you didnt dare touch the chair after the day of her funeral.",
                        "You hope that maybe some day youll find someone wholl be as perfect as she was.",
                        "EAST to return to the Mudd Huzz."
                ),
                null,
                null
        ));
        rooms.put("hohe_dirn", new Room(
                "hohe_dirn",
                "HOHE DIRN",
                List.of(
                        "Welcome the the HOHE DIRN hike.",
                        "This is the beginner hike, lets hope you are well stocked in apple juice.",
                        "NORTH awaits the start of your journy. Be careful of the local population, they might be dangerous."
                ),
                null,
                null
        ));
        rooms.put("traunstein", new Room(
                "traunstein",
                "TRAUNSTEIN",
                List.of(),
                "Oops, seems like you fell asleep, you are now at ... HOHE DIRN?!",
                Direction.E
        ));
        rooms.put("grosser_priel", new Room(
                "grosser_priel",
                "GROSSER PRIEL",
                List.of(),
                "zzzzZZZzzz ... what? .. where am I? ... HOHE DIRN?!",
                Direction.E
        ));

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
