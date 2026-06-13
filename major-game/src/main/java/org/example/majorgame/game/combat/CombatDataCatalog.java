package org.example.majorgame.game.combat;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CombatDataCatalog {
    private static final String RESOURCE_PATH = "/org/example/majorgame/combat-data.yml";
    private static final CombatDataCatalog DEFAULT = loadFromResource();

    private final Map<String, EncounterDefinition> encountersByLocation;

    private CombatDataCatalog(Map<String, EncounterDefinition> encountersByLocation) {
        this.encountersByLocation = encountersByLocation;
    }

    public static CombatDataCatalog defaultCatalog() {
        return DEFAULT;
    }

    public EncounterDefinition encounterForLocation(String locationId) {
        return encountersByLocation.get(locationId);
    }

    private static CombatDataCatalog loadFromResource() {
        InputStream resourceStream = CombatDataCatalog.class.getResourceAsStream(RESOURCE_PATH);
        if (resourceStream == null) {
            throw new IllegalStateException("Missing combat data resource: " + RESOURCE_PATH);
        }
        Object loaded;
        try (InputStream inputStream = resourceStream) {
            loaded = new Yaml().load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load combat data: " + RESOURCE_PATH, exception);
        }

        Map<String, Object> root = asMap(loaded, "root");
        Map<String, EncounterDefinition> encountersByLocation = loadEncounters(
                asMap(root.get("encounters"), "encounters")
        );
        return new CombatDataCatalog(encountersByLocation);
    }

    private static Map<String, EncounterDefinition> loadEncounters(Map<String, Object> node) {
        Map<String, EncounterDefinition> encountersByLocation = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> encounterNode = asMap(entry.getValue(), "encounters." + id);
            String location = asString(encounterNode.get("location"), "encounters." + id + ".location");
            int rows = asInt(encounterNode.get("rows"), "encounters." + id + ".rows");
            int cols = asInt(encounterNode.get("cols"), "encounters." + id + ".cols");
            Map<String, Object> playerStartNode = asMap(encounterNode.get("playerStart"), "encounters." + id + ".playerStart");
            GridPosition playerStart = new GridPosition(
                    asInt(playerStartNode.get("row"), "encounters." + id + ".playerStart.row"),
                    asInt(playerStartNode.get("col"), "encounters." + id + ".playerStart.col")
            );
            List<Object> enemiesNode = asList(encounterNode.get("enemies"), "encounters." + id + ".enemies");
            List<EnemyDefinition> enemies = new ArrayList<>();
            for (int index = 0; index < enemiesNode.size(); index++) {
                Map<String, Object> enemyNode = asMap(enemiesNode.get(index), "encounters." + id + ".enemies[" + index + "]");
                String enemyId = asString(enemyNode.get("id"), "encounters." + id + ".enemies[" + index + "].id");
                String name = asString(enemyNode.get("name"), "encounters." + id + ".enemies[" + index + "].name");
                int maxHp = asInt(enemyNode.get("maxHp"), "encounters." + id + ".enemies[" + index + "].maxHp");
                int movesPerTurn = asInt(enemyNode.get("movesPerTurn"), "encounters." + id + ".enemies[" + index + "].movesPerTurn");
                List<String> weapons = optionalStringList(enemyNode.get("weapons"), "encounters." + id + ".enemies[" + index + "].weapons");
                int row = asInt(enemyNode.get("row"), "encounters." + id + ".enemies[" + index + "].row");
                int col = asInt(enemyNode.get("col"), "encounters." + id + ".enemies[" + index + "].col");

                List<Object> abilitiesNode = optionalList(enemyNode.get("abilities"), "encounters." + id + ".enemies[" + index + "].abilities");
                List<AbilityDefinition> abilities = new ArrayList<>();
                for (int abIdx = 0; abIdx < abilitiesNode.size(); abIdx++) {
                    Map<String, Object> abilityNode = asMap(abilitiesNode.get(abIdx), "encounters." + id + ".enemies[" + index + "].abilities[" + abIdx + "]");
                    String abId = asString(abilityNode.get("id"), "encounters." + id + ".enemies[" + index + "].abilities[" + abIdx + "].id");
                    String abName = asString(abilityNode.get("name"), "encounters." + id + ".enemies[" + index + "].abilities[" + abIdx + "].name");
                    int abDamage = asInt(abilityNode.get("damage"), "encounters." + id + ".enemies[" + index + "].abilities[" + abIdx + "].damage");
                    int abRange = asInt(abilityNode.get("range"), "encounters." + id + ".enemies[" + index + "].abilities[" + abIdx + "].range");
                    abilities.add(new AbilityDefinition(abId, abName, abDamage, abRange));
                }

                enemies.add(new EnemyDefinition(
                        enemyId,
                        name,
                        maxHp,
                        movesPerTurn,
                        List.copyOf(weapons),
                        new GridPosition(row, col),
                        List.copyOf(abilities)
                ));
            }
            if (encountersByLocation.containsKey(location)) {
                throw new IllegalStateException("Multiple encounters configured for location: " + location);
            }
            encountersByLocation.put(location, new EncounterDefinition(id, location, rows, cols, playerStart, List.copyOf(enemies)));
        }
        return encountersByLocation;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value, String fieldPath) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalStateException("Expected map at '" + fieldPath + "'.");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value, String fieldPath) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalStateException("Expected list at '" + fieldPath + "'.");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> optionalList(Object value, String fieldPath) {
        if (value == null) {
            return List.of();
        }
        return asList(value, fieldPath);
    }

    private static String asString(Object value, String fieldPath) {
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalStateException("Expected string at '" + fieldPath + "'.");
    }

    private static List<String> asStringList(Object value, String fieldPath) {
        List<Object> list = asList(value, fieldPath);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof String text)) {
                throw new IllegalStateException("Expected string at '" + fieldPath + "[" + i + "]'.");
            }
            result.add(text);
        }
        return result;
    }

    private static List<String> optionalStringList(Object value, String fieldPath) {
        if (value == null) {
            return List.of();
        }
        return asStringList(value, fieldPath);
    }

    private static int asInt(Object value, String fieldPath) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalStateException("Expected integer at '" + fieldPath + "'.");
    }

    public record AbilityDefinition(
            String id,
            String name,
            int damage,
            int range
    ) {
    }

    public record EnemyDefinition(
            String id,
            String name,
            int maxHp,
            int movesPerTurn,
            List<String> weaponIds,
            GridPosition position,
            List<AbilityDefinition> abilities
    ) {
    }

    public record EncounterDefinition(
            String id,
            String location,
            int rows,
            int cols,
            GridPosition playerStart,
            List<EnemyDefinition> enemies
    ) {
    }

    public record GridPosition(
            int row,
            int col
    ) {
    }
}
