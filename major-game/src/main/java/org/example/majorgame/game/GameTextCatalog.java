package org.example.majorgame.game;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GameTextCatalog {
    private static final String RESOURCE_PATH = "/org/example/majorgame/game-text.yml";
    private static final GameTextCatalog DEFAULT = loadFromResource();

    private final Map<String, LocationText> locations;
    private final Map<String, Map<Direction, String>> paths;
    private final Map<String, String> initialItems;
    private final List<String> instructions;

    private GameTextCatalog(
            Map<String, LocationText> locations,
            Map<String, Map<Direction, String>> paths,
            Map<String, String> initialItems,
            List<String> instructions
    ) {
        this.locations = locations;
        this.paths = paths;
        this.initialItems = initialItems;
        this.instructions = instructions;
    }

    static GameTextCatalog defaultCatalog() {
        return DEFAULT;
    }

    LocationText location(String locationId) {
        LocationText locationText = locations.get(locationId);
        if (locationText == null) {
            throw new IllegalStateException("Missing location text for id: " + locationId);
        }
        return locationText;
    }

    List<String> instructions() {
        return instructions;
    }

    Map<String, LocationText> locations() {
        return new LinkedHashMap<>(locations);
    }

    Map<String, Map<Direction, String>> paths() {
        Map<String, Map<Direction, String>> copiedPaths = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Direction, String>> entry : paths.entrySet()) {
            copiedPaths.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copiedPaths;
    }

    Map<String, String> initialItems() {
        return new LinkedHashMap<>(initialItems);
    }

    private static GameTextCatalog loadFromResource() {
        InputStream resourceStream = GameTextCatalog.class.getResourceAsStream(RESOURCE_PATH);
        if (resourceStream == null) {
            throw new IllegalStateException("Missing game text resource: " + RESOURCE_PATH);
        }
        Object loaded;
        try (InputStream inputStream = resourceStream) {
            Yaml yaml = new Yaml();
            loaded = yaml.load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load game text resource: " + RESOURCE_PATH, exception);
        }
        Map<String, Object> root = asMap(loaded, "root");

        Map<String, Object> locationsNode = asMap(root.get("locations"), "locations");
        Map<String, LocationText> locations = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : locationsNode.entrySet()) {
            String locationId = entry.getKey();
            Map<String, Object> locationNode = asMap(entry.getValue(), "locations." + locationId);
            String title = asString(locationNode.get("title"), "locations." + locationId + ".title");
            List<String> description = asStringList(locationNode.get("description"), "locations." + locationId + ".description");
            String autoRouteMessage = optionalString(locationNode.get("autoRouteMessage"), "locations." + locationId + ".autoRouteMessage");
            Direction autoRouteDirection = optionalDirection(
                    locationNode.get("autoRouteDirection"),
                    "locations." + locationId + ".autoRouteDirection"
            );
            locations.put(locationId, new LocationText(title, description, autoRouteMessage, autoRouteDirection));
        }

        Map<String, Object> pathsNode = asMap(root.get("paths"), "paths");
        Map<String, Map<Direction, String>> paths = new LinkedHashMap<>();
        for (Map.Entry<String, Object> fromEntry : pathsNode.entrySet()) {
            String fromLocation = fromEntry.getKey();
            Map<String, Object> directionNode = asMap(fromEntry.getValue(), "paths." + fromLocation);
            Map<Direction, String> directionMappings = new LinkedHashMap<>();
            for (Map.Entry<String, Object> directionEntry : directionNode.entrySet()) {
                Direction direction = parseDirection(directionEntry.getKey(), "paths." + fromLocation);
                String targetLocation = asString(directionEntry.getValue(), "paths." + fromLocation + "." + directionEntry.getKey());
                directionMappings.put(direction, targetLocation);
            }
            paths.put(fromLocation, directionMappings);
        }

        Map<String, Object> itemsNode = asMap(root.get("items"), "items");
        Map<String, String> initialItems = new LinkedHashMap<>();
        for (Map.Entry<String, Object> itemEntry : itemsNode.entrySet()) {
            String itemId = itemEntry.getKey();
            String locationId = asString(itemEntry.getValue(), "items." + itemId);
            initialItems.put(itemId, locationId);
        }

        Map<String, Object> commandsNode = asMap(root.get("commands"), "commands");
        List<String> instructions = asStringList(commandsNode.get("instructions"), "commands.instructions");
        return new GameTextCatalog(locations, paths, initialItems, List.copyOf(instructions));
    }

    private static Direction parseDirection(String token, String fieldPath) {
        Direction direction = Direction.fromToken(token);
        if (direction == null) {
            throw new IllegalStateException("Unknown direction '" + token + "' at '" + fieldPath + "'.");
        }
        return direction;
    }

    private static Direction optionalDirection(Object value, String fieldPath) {
        if (value == null) {
            return null;
        }
        if (value instanceof String token) {
            return parseDirection(token, fieldPath);
        }
        throw new IllegalStateException("Expected direction string at '" + fieldPath + "'.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value, String fieldPath) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalStateException("Expected map at '" + fieldPath + "'.");
    }

    private static String asString(Object value, String fieldPath) {
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalStateException("Expected string at '" + fieldPath + "'.");
    }

    private static String optionalString(Object value, String fieldPath) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalStateException("Expected string at '" + fieldPath + "'.");
    }

    private static List<String> asStringList(Object value, String fieldPath) {
        if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
            return List.copyOf(list.stream().map(String.class::cast).toList());
        }
        throw new IllegalStateException("Expected string list at '" + fieldPath + "'.");
    }

    record LocationText(
            String title,
            List<String> descriptionLines,
            String autoRouteMessage,
            Direction autoRouteDirection
    ) {
    }
}
