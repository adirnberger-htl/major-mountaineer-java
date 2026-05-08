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

    private final Map<String, AbilityDefinition> abilities;
    private final Map<String, WeaponDefinition> weapons;
    private final Map<String, EnemyTypeDefinition> enemyTypes;
    private final Map<String, EncounterDefinition> encountersByLocation;

    private CombatDataCatalog(
            Map<String, AbilityDefinition> abilities,
            Map<String, WeaponDefinition> weapons,
            Map<String, EnemyTypeDefinition> enemyTypes,
            Map<String, EncounterDefinition> encountersByLocation
    ) {
        this.abilities = abilities;
        this.weapons = weapons;
        this.enemyTypes = enemyTypes;
        this.encountersByLocation = encountersByLocation;
    }

    public static CombatDataCatalog defaultCatalog() {
        return DEFAULT;
    }

    public AbilityDefinition ability(String abilityId) {
        AbilityDefinition ability = abilities.get(abilityId);
        if (ability == null) {
            throw new IllegalStateException("Unknown ability id: " + abilityId);
        }
        return ability;
    }

    public WeaponDefinition weapon(String weaponId) {
        WeaponDefinition weapon = weapons.get(weaponId);
        if (weapon == null) {
            throw new IllegalStateException("Unknown weapon id: " + weaponId);
        }
        return weapon;
    }

    public EnemyTypeDefinition enemyType(String enemyTypeId) {
        EnemyTypeDefinition enemyType = enemyTypes.get(enemyTypeId);
        if (enemyType == null) {
            throw new IllegalStateException("Unknown enemy type id: " + enemyTypeId);
        }
        return enemyType;
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
        Map<String, AbilityDefinition> abilities = loadAbilities(asMap(root.get("abilities"), "abilities"));
        Map<String, WeaponDefinition> weapons = loadWeapons(asMap(root.get("weapons"), "weapons"));
        Map<String, EnemyTypeDefinition> enemyTypes = loadEnemyTypes(
                asMap(root.get("enemyTypes"), "enemyTypes"),
                abilities,
                weapons
        );
        Map<String, EncounterDefinition> encountersByLocation = loadEncounters(
                asMap(root.get("encounters"), "encounters"),
                enemyTypes
        );
        return new CombatDataCatalog(abilities, weapons, enemyTypes, encountersByLocation);
    }

    private static Map<String, AbilityDefinition> loadAbilities(Map<String, Object> node) {
        Map<String, AbilityDefinition> abilities = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> abilityNode = asMap(entry.getValue(), "abilities." + id);
            abilities.put(id, new AbilityDefinition(
                    id,
                    asString(abilityNode.get("name"), "abilities." + id + ".name"),
                    asInt(abilityNode.get("damage"), "abilities." + id + ".damage"),
                    asInt(abilityNode.get("range"), "abilities." + id + ".range")
            ));
        }
        return abilities;
    }

    private static Map<String, WeaponDefinition> loadWeapons(Map<String, Object> node) {
        Map<String, WeaponDefinition> weapons = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> weaponNode = asMap(entry.getValue(), "weapons." + id);
            weapons.put(id, new WeaponDefinition(
                    id,
                    asString(weaponNode.get("name"), "weapons." + id + ".name"),
                    asInt(weaponNode.get("damageBonus"), "weapons." + id + ".damageBonus")
            ));
        }
        return weapons;
    }

    private static Map<String, EnemyTypeDefinition> loadEnemyTypes(
            Map<String, Object> node,
            Map<String, AbilityDefinition> abilities,
            Map<String, WeaponDefinition> weapons
    ) {
        Map<String, EnemyTypeDefinition> enemyTypes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> enemyNode = asMap(entry.getValue(), "enemyTypes." + id);
            List<String> abilityIds = asStringList(enemyNode.get("abilities"), "enemyTypes." + id + ".abilities");
            for (String abilityId : abilityIds) {
                if (!abilities.containsKey(abilityId)) {
                    throw new IllegalStateException("Enemy type '" + id + "' references unknown ability: " + abilityId);
                }
            }
            String weaponId = asString(enemyNode.get("weapon"), "enemyTypes." + id + ".weapon");
            if (!weapons.containsKey(weaponId)) {
                throw new IllegalStateException("Enemy type '" + id + "' references unknown weapon: " + weaponId);
            }
            enemyTypes.put(id, new EnemyTypeDefinition(
                    id,
                    asString(enemyNode.get("name"), "enemyTypes." + id + ".name"),
                    asInt(enemyNode.get("maxHp"), "enemyTypes." + id + ".maxHp"),
                    asInt(enemyNode.get("movesPerTurn"), "enemyTypes." + id + ".movesPerTurn"),
                    List.copyOf(abilityIds),
                    weaponId
            ));
        }
        return enemyTypes;
    }

    private static Map<String, EncounterDefinition> loadEncounters(
            Map<String, Object> node,
            Map<String, EnemyTypeDefinition> enemyTypes
    ) {
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
            List<EnemySpawn> enemies = new ArrayList<>();
            for (int index = 0; index < enemiesNode.size(); index++) {
                Map<String, Object> enemyNode = asMap(enemiesNode.get(index), "encounters." + id + ".enemies[" + index + "]");
                String enemyType = asString(enemyNode.get("type"), "encounters." + id + ".enemies[" + index + "].type");
                if (!enemyTypes.containsKey(enemyType)) {
                    throw new IllegalStateException("Encounter '" + id + "' references unknown enemy type: " + enemyType);
                }
                enemies.add(new EnemySpawn(
                        asString(enemyNode.get("id"), "encounters." + id + ".enemies[" + index + "].id"),
                        enemyType,
                        new GridPosition(
                                asInt(enemyNode.get("row"), "encounters." + id + ".enemies[" + index + "].row"),
                                asInt(enemyNode.get("col"), "encounters." + id + ".enemies[" + index + "].col")
                        )
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

    public record WeaponDefinition(
            String id,
            String name,
            int damageBonus
    ) {
    }

    public record EnemyTypeDefinition(
            String id,
            String name,
            int maxHp,
            int movesPerTurn,
            List<String> abilities,
            String weapon
    ) {
    }

    public record EncounterDefinition(
            String id,
            String location,
            int rows,
            int cols,
            GridPosition playerStart,
            List<EnemySpawn> enemies
    ) {
    }

    public record EnemySpawn(
            String id,
            String type,
            GridPosition position
    ) {
    }

    public record GridPosition(
            int row,
            int col
    ) {
    }
}
