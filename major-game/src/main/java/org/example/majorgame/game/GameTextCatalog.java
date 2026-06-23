package org.example.majorgame.game;

import org.yaml.snakeyaml.Yaml;
import org.example.majorgame.game.combat.CombatDataCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameTextCatalog {
    private static final String RESOURCE_PATH = "/org/example/majorgame/game-text.yml";
    private static final GameTextCatalog DEFAULT = loadFromResource();

    private final Map<String, LocationText> locations;
    private final Map<String, Map<Direction, String>> paths;
    private final Map<String, ItemDefinition> items;
    private final List<ItemPlacement> initialItems;
    private final List<String> instructions;

    private final Map<String, Room> flatRooms;
    private final Map<String, String> roomToScopePath;
    private final Map<String, SectionDefinition> campaignSections;
    private final Map<String, Boolean> globalVariables;

    private GameTextCatalog(
            Map<String, LocationText> locations,
            Map<String, Map<Direction, String>> paths,
            Map<String, ItemDefinition> items,
            List<ItemPlacement> initialItems,
            List<String> instructions,
            Map<String, Room> flatRooms,
            Map<String, String> roomToScopePath,
            Map<String, SectionDefinition> campaignSections,
            Map<String, Boolean> globalVariables
    ) {
        this.locations = locations;
        this.paths = paths;
        this.items = items;
        this.initialItems = initialItems;
        this.instructions = instructions;
        this.flatRooms = flatRooms;
        this.roomToScopePath = roomToScopePath;
        this.campaignSections = campaignSections;
        this.globalVariables = globalVariables;
    }

    public static GameTextCatalog defaultCatalog() {
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

    public Map<String, ItemDefinition> items() {
        return new LinkedHashMap<>(items);
    }

    public List<ItemPlacement> initialItems() {
        return List.copyOf(initialItems);
    }

    public Map<String, Room> flatRooms() {
        return new LinkedHashMap<>(flatRooms);
    }

    public Map<String, String> roomToScopePath() {
        return new LinkedHashMap<>(roomToScopePath);
    }

    public Map<String, SectionDefinition> campaignSections() {
        return new LinkedHashMap<>(campaignSections);
    }

    public Map<String, Boolean> globalVariables() {
        return new LinkedHashMap<>(globalVariables);
    }

    private static GameTextCatalog loadFromResource() {
        InputStream resourceStream = GameTextCatalog.class.getResourceAsStream(RESOURCE_PATH);
        if (resourceStream == null) {
            throw new IllegalStateException("Missing game text resource: " + RESOURCE_PATH);
        }
        try (InputStream inputStream = resourceStream) {
            return load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load game text resource: " + RESOURCE_PATH, exception);
        }
    }

    public static GameTextCatalog load(InputStream inputStream) {
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(inputStream);
        return parse(loaded);
    }

    private static GameTextCatalog parse(Object loaded) {
        Map<String, Object> root = asMap(loaded, "root");

        // Parse global items
        List<Object> itemsNode = asList(root.get("items"), "items");
        Map<String, ItemDefinition> items = new LinkedHashMap<>();
        for (int i = 0; i < itemsNode.size(); i++) {
            Map<String, Object> itemNode = asMap(itemsNode.get(i), "items[" + i + "]");
            String id = asString(itemNode.get("id"), "items[" + i + "].id");
            String name = optionalString(itemNode.get("name"), "items[" + i + "].name");
            String category = optionalString(itemNode.get("category"), "items[" + i + "].category");
            Integer damage = optionalInt(itemNode.get("damage"), "items[" + i + "].damage");
            Integer range = optionalInt(itemNode.get("range"), "items[" + i + "].range");
            items.put(id, new ItemDefinition(id, name, category, damage, range));
        }

        // Parse instructions
        Map<String, Object> commandsNode = asMap(root.get("commands"), "commands");
        List<String> instructions = asStringList(commandsNode.get("instructions"), "commands.instructions");

        Map<String, Room> flatRooms = new LinkedHashMap<>();
        Map<String, String> roomToScopePath = new LinkedHashMap<>();
        Map<String, SectionDefinition> campaignSections = new LinkedHashMap<>();
        Map<String, Boolean> globalVariables = new LinkedHashMap<>();
        List<ItemPlacement> initialItems = new ArrayList<>();

        Map<String, LocationText> locationsCompatibility = new LinkedHashMap<>();
        Map<String, Map<Direction, String>> pathsCompatibility = new LinkedHashMap<>();

        if (root.containsKey("sections")) {
            // New Hierarchical Campaign Mode
            globalVariables.putAll(asBooleanMap(root.get("variables"), "variables"));

            Map<String, Object> sectionsNode = asMap(root.get("sections"), "sections");
            for (Map.Entry<String, Object> entry : sectionsNode.entrySet()) {
                String campaignId = entry.getKey();
                Map<String, Object> campaignNode = asMap(entry.getValue(), "sections." + campaignId);
                SectionDefinition campaign = parseSection(
                        campaignId, campaignNode, "/", flatRooms, roomToScopePath, items, initialItems
                );
                campaignSections.put(campaignId, campaign);
            }

            // Build compatibility maps for legacy components
            for (Map.Entry<String, Room> entry : flatRooms.entrySet()) {
                String roomId = entry.getKey();
                Room room = entry.getValue();
                locationsCompatibility.put(roomId, new LocationText(
                        room.title(), room.descriptionLines(), room.dialogLines(),
                        room.autoRouteMessage(), room.autoRouteDirection()
                ));

                Map<Direction, String> pathStrings = new LinkedHashMap<>();
                for (Map.Entry<Direction, PathDefinition> pathEntry : room.paths().entrySet()) {
                    PathDefinition path = pathEntry.getValue();
                    String pathStr = path.targetLocation();
                    if (path.label() != null && !path.label().isEmpty()) {
                        pathStr += "|" + path.label();
                    }
                    pathStrings.put(pathEntry.getKey(), pathStr);
                }
                pathsCompatibility.put(roomId, pathStrings);
            }
        } else {
            // Flat Mode
            Map<String, Object> locationsNode = asMap(root.get("locations"), "locations");
            for (Map.Entry<String, Object> entry : locationsNode.entrySet()) {
                String locationId = entry.getKey();
                Map<String, Object> locationNode = asMap(entry.getValue(), "locations." + locationId);
                String title = asString(locationNode.get("title"), "locations." + locationId + ".title");
                List<String> description = asStringList(locationNode.get("description"), "locations." + locationId + ".description");
                List<String> dialog = optionalStringList(locationNode.get("dialog"), "locations." + locationId + ".dialog");
                String autoRouteMessage = optionalString(locationNode.get("autoRouteMessage"), "locations." + locationId + ".autoRouteMessage");
                Direction autoRouteDirection = optionalDirection(
                        locationNode.get("autoRouteDirection"),
                        "locations." + locationId + ".autoRouteDirection"
                );

                LocationText locText = new LocationText(title, description, dialog, autoRouteMessage, autoRouteDirection);
                locationsCompatibility.put(locationId, locText);

                Room room = parseRoom(locationId, locationNode, "/", items, initialItems);
                flatRooms.put(locationId, room);
                roomToScopePath.put(locationId, "/");

                // Populate compatibility paths map for this room
                Map<Direction, String> pathStrings = new LinkedHashMap<>();
                for (Map.Entry<Direction, PathDefinition> pathEntry : room.paths().entrySet()) {
                    PathDefinition path = pathEntry.getValue();
                    String pathStr = path.targetLocation();
                    if (path.label() != null && !path.label().isEmpty()) {
                        pathStr += "|" + path.label();
                    }
                    pathStrings.put(pathEntry.getKey(), pathStr);
                }
                pathsCompatibility.put(locationId, pathStrings);
            }
        }

        validateVariables(flatRooms, roomToScopePath, campaignSections, globalVariables);

        return new GameTextCatalog(
                locationsCompatibility, pathsCompatibility, items, initialItems, List.copyOf(instructions),
                flatRooms, roomToScopePath, campaignSections, globalVariables
        );
    }

    private static SectionDefinition parseSection(
            String id,
            Map<String, Object> sectionNode,
            String parentScopePath,
            Map<String, Room> flatRooms,
            Map<String, String> roomToScopePath,
            Map<String, ItemDefinition> items,
            List<ItemPlacement> initialItems
    ) {
        String currentScopePath = parentScopePath + id + "/";
        String title = optionalString(sectionNode.get("title"), "sections." + id + ".title");
        Map<String, Boolean> variables = asBooleanMap(sectionNode.get("variables"), "sections." + id + ".variables");

        Map<String, SectionDefinition> subSections = new LinkedHashMap<>();
        if (sectionNode.containsKey("sections")) {
            Map<String, Object> subSectionsNode = asMap(sectionNode.get("sections"), "sections." + id + ".sections");
            for (Map.Entry<String, Object> entry : subSectionsNode.entrySet()) {
                String subId = entry.getKey();
                Map<String, Object> subSectionNode = asMap(entry.getValue(), "sections." + id + ".sections." + subId);
                SectionDefinition subSection = parseSection(
                        subId, subSectionNode, currentScopePath, flatRooms, roomToScopePath, items, initialItems
                );
                subSections.put(subId, subSection);
            }
        }

        Map<String, Room> locations = new LinkedHashMap<>();
        if (sectionNode.containsKey("locations")) {
            Map<String, Object> locationsNode = asMap(sectionNode.get("locations"), "sections." + id + ".locations");
            for (Map.Entry<String, Object> entry : locationsNode.entrySet()) {
                String roomId = entry.getKey();
                Map<String, Object> locationNode = asMap(entry.getValue(), "sections." + id + ".locations." + roomId);
                Room room = parseRoom(roomId, locationNode, currentScopePath, items, initialItems);
                locations.put(roomId, room);
                flatRooms.put(roomId, room);
                roomToScopePath.put(roomId, currentScopePath);
            }
        }

        return new SectionDefinition(id, title, variables, subSections, locations);
    }

    private static Room parseRoom(
            String roomId,
            Map<String, Object> locationNode,
            String scopePath,
            Map<String, ItemDefinition> items,
            List<ItemPlacement> initialItems
    ) {
        String title = asString(locationNode.get("title"), "locations." + roomId + ".title");
        Boolean checkpoint = optionalBool(locationNode.get("checkpoint"), "locations." + roomId + ".checkpoint");
        Map<String, Boolean> variables = asBooleanMap(locationNode.get("variables"), "locations." + roomId + ".variables");
        List<String> description = asStringList(locationNode.get("description"), "locations." + roomId + ".description");
        List<String> dialog = optionalStringList(locationNode.get("dialog"), "locations." + roomId + ".dialog");
        String autoRouteMessage = optionalString(locationNode.get("autoRouteMessage"), "locations." + roomId + ".autoRouteMessage");
        Direction autoRouteDirection = optionalDirection(locationNode.get("autoRouteDirection"), "locations." + roomId + ".autoRouteDirection");

        List<String> locationItems = optionalStringList(locationNode.get("items"), "locations." + roomId + ".items");
        for (String itemId : locationItems) {
            if (!items.containsKey(itemId)) {
                throw new IllegalStateException("Location '" + roomId + "' references undefined item: " + itemId);
            }
            initialItems.add(new ItemPlacement(itemId, roomId));
        }

        Map<Direction, PathDefinition> paths = new LinkedHashMap<>();
        if (locationNode.containsKey("paths")) {
            Map<String, Object> pathsNode = asMap(locationNode.get("paths"), "locations." + roomId + ".paths");
            for (Map.Entry<String, Object> entry : pathsNode.entrySet()) {
                Direction dir = parseDirection(entry.getKey(), "locations." + roomId + ".paths");
                paths.put(dir, parsePathDefinition(entry.getValue(), "locations." + roomId + ".paths." + entry.getKey()));
            }
        }

        DialogueDefinition dialogue = null;
        if (locationNode.containsKey("dialogue")) {
            dialogue = parseDialogue(asMap(locationNode.get("dialogue"), "locations." + roomId + ".dialogue"), "locations." + roomId + ".dialogue");
        }

        CombatDataCatalog.EncounterDefinition encounter = null;
        if (locationNode.containsKey("encounter")) {
            encounter = parseEncounter(roomId, asMap(locationNode.get("encounter"), "locations." + roomId + ".encounter"), "locations." + roomId + ".encounter");
        }

        ChallengeDefinition challenge = null;
        if (locationNode.containsKey("challenge")) {
            challenge = parseChallenge(asMap(locationNode.get("challenge"), "locations." + roomId + ".challenge"), "locations." + roomId + ".challenge");
        }

        return new Room(
                roomId, title, checkpoint, variables, description, dialog,
                autoRouteMessage, autoRouteDirection, paths, dialogue, encounter, challenge
        );
    }

    private static PathDefinition parsePathDefinition(Object value, String fieldPath) {
        if (value instanceof String str) {
            String target;
            String label = "";
            int pipeIdx = str.indexOf('|');
            if (pipeIdx >= 0) {
                target = str.substring(0, pipeIdx);
                label = str.substring(pipeIdx + 1);
            } else {
                target = str;
            }
            return new PathDefinition(target, label, List.of(), null, 0, 0, null);
        } else if (value instanceof Map<?, ?> map) {
            String target = asString(map.get("target"), fieldPath + ".target");
            String label = optionalString(map.get("label"), fieldPath + ".label");
            List<String> requireFlags = optionalStringList(map.get("requireFlags"), fieldPath + ".requireFlags");
            String failMessage = optionalString(map.get("failMessage"), fieldPath + ".failMessage");
            Integer juiceCost = optionalInt(map.get("juiceCost"), fieldPath + ".juiceCost");
            Integer honorGain = optionalInt(map.get("honorGain"), fieldPath + ".honorGain");
            String itemGain = optionalString(map.get("itemGain"), fieldPath + ".itemGain");
            return new PathDefinition(target, label, requireFlags, failMessage, juiceCost, honorGain, itemGain);
        }
        throw new IllegalStateException("Expected string or map for path at '" + fieldPath + "'.");
    }

    private static DialogueDefinition parseDialogue(Map<String, Object> node, String fieldPath) {
        String startNode = asString(node.get("startNode"), fieldPath + ".startNode");
        Map<String, Object> nodesNode = asMap(node.get("nodes"), fieldPath + ".nodes");
        Map<String, DialogueDefinition.DialogueNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : nodesNode.entrySet()) {
            String nodeKey = entry.getKey();
            Map<String, Object> dialogueNode = asMap(entry.getValue(), fieldPath + ".nodes." + nodeKey);
            String speaker = asString(dialogueNode.get("speaker"), fieldPath + ".nodes." + nodeKey + ".speaker");
            String text = asString(dialogueNode.get("text"), fieldPath + ".nodes." + nodeKey + ".text");
            Boolean endDialogue = optionalBool(dialogueNode.get("endDialogue"), fieldPath + ".nodes." + nodeKey + ".endDialogue");
            Boolean befriended = optionalBool(dialogueNode.get("befriended"), fieldPath + ".nodes." + nodeKey + ".befriended");
            Boolean startCombat = optionalBool(dialogueNode.get("startCombat"), fieldPath + ".nodes." + nodeKey + ".startCombat");
            String itemGain = optionalString(dialogueNode.get("itemGain"), fieldPath + ".nodes." + nodeKey + ".itemGain");
            Integer honorGain = optionalInt(dialogueNode.get("honorGain"), fieldPath + ".nodes." + nodeKey + ".honorGain");
            Map<String, Boolean> setFlags = asBooleanMap(dialogueNode.get("setFlags"), fieldPath + ".nodes." + nodeKey + ".setFlags");

            List<DialogueDefinition.DialogueChoice> choices = new ArrayList<>();
            if (dialogueNode.containsKey("choices")) {
                List<Object> choicesNode = asList(dialogueNode.get("choices"), fieldPath + ".nodes." + nodeKey + ".choices");
                for (int i = 0; i < choicesNode.size(); i++) {
                    Map<String, Object> choiceNode = asMap(choicesNode.get(i), fieldPath + ".nodes." + nodeKey + ".choices[" + i + "]");
                    String choiceText = asString(choiceNode.get("text"), fieldPath + ".nodes." + nodeKey + ".choices[" + i + "].text");
                    String targetNode = asString(choiceNode.get("targetNode"), fieldPath + ".nodes." + nodeKey + ".choices[" + i + "].targetNode");
                    Integer requiredHonor = optionalInt(choiceNode.get("requiredHonor"), fieldPath + ".nodes." + nodeKey + ".choices[" + i + "].requiredHonor");
                    String requiredItem = optionalString(choiceNode.get("requiredItem"), fieldPath + ".nodes." + nodeKey + ".choices[" + i + "].requiredItem");
                    String failMessage = optionalString(choiceNode.get("failMessage"), fieldPath + ".nodes." + nodeKey + ".choices[" + i + "].failMessage");
                    Map<String, Boolean> choiceSetFlags = asBooleanMap(choiceNode.get("setFlags"), fieldPath + ".nodes." + nodeKey + ".choices[" + i + "].setFlags");
                    choices.add(new DialogueDefinition.DialogueChoice(choiceText, targetNode, requiredHonor, requiredItem, failMessage, choiceSetFlags));
                }
            }

            nodes.put(nodeKey, new DialogueDefinition.DialogueNode(speaker, text, choices, endDialogue, befriended, startCombat, itemGain, honorGain, setFlags));
        }
        return new DialogueDefinition(startNode, nodes);
    }

    private static CombatDataCatalog.EncounterDefinition parseEncounter(String locationId, Map<String, Object> encounterNode, String fieldPath) {
        int rows = asInt(encounterNode.get("rows"), fieldPath + ".rows");
        int cols = asInt(encounterNode.get("cols"), fieldPath + ".cols");
        Map<String, Object> playerStartNode = asMap(encounterNode.get("playerStart"), fieldPath + ".playerStart");
        CombatDataCatalog.GridPosition playerStart = new CombatDataCatalog.GridPosition(
                asInt(playerStartNode.get("row"), fieldPath + ".playerStart.row"),
                asInt(playerStartNode.get("col"), fieldPath + ".playerStart.col")
        );
        List<Object> enemiesNode = asList(encounterNode.get("enemies"), fieldPath + ".enemies");
        List<CombatDataCatalog.EnemyDefinition> enemies = new ArrayList<>();
        for (int index = 0; index < enemiesNode.size(); index++) {
            Map<String, Object> enemyNode = asMap(enemiesNode.get(index), fieldPath + ".enemies[" + index + "]");
            String enemyId = asString(enemyNode.get("id"), fieldPath + ".enemies[" + index + "].id");
            String name = asString(enemyNode.get("name"), fieldPath + ".enemies[" + index + "].name");
            int maxHp = asInt(enemyNode.get("maxHp"), fieldPath + ".enemies[" + index + "].maxHp");
            Integer movesPerTurn = optionalInt(enemyNode.get("movesPerTurn"), fieldPath + ".enemies[" + index + "].movesPerTurn");
            if (movesPerTurn == null) {
                movesPerTurn = 1;
            }
            List<String> weapons = optionalStringList(enemyNode.get("weapons"), fieldPath + ".enemies[" + index + "].weapons");
            int row = asInt(enemyNode.get("row"), fieldPath + ".enemies[" + index + "].row");
            int col = asInt(enemyNode.get("col"), fieldPath + ".enemies[" + index + "].col");

            List<Object> abilitiesNode = optionalList(enemyNode.get("abilities"), fieldPath + ".enemies[" + index + "].abilities");
            List<CombatDataCatalog.AbilityDefinition> abilities = new ArrayList<>();
            for (int abIdx = 0; abIdx < abilitiesNode.size(); abIdx++) {
                Map<String, Object> abilityNode = asMap(abilitiesNode.get(abIdx), fieldPath + ".enemies[" + index + "].abilities[" + abIdx + "]");
                String abId = asString(abilityNode.get("id"), fieldPath + ".enemies[" + index + "].abilities[" + abIdx + "].id");
                String abName = asString(abilityNode.get("name"), fieldPath + ".enemies[" + index + "].abilities[" + abIdx + "].name");
                int abDamage = asInt(abilityNode.get("damage"), fieldPath + ".enemies[" + index + "].abilities[" + abIdx + "].damage");
                int abRange = asInt(abilityNode.get("range"), fieldPath + ".enemies[" + index + "].abilities[" + abIdx + "].range");
                abilities.add(new CombatDataCatalog.AbilityDefinition(abId, abName, abDamage, abRange));
            }

            enemies.add(new CombatDataCatalog.EnemyDefinition(
                    enemyId,
                    name,
                    maxHp,
                    movesPerTurn,
                    List.copyOf(weapons),
                    new CombatDataCatalog.GridPosition(row, col),
                    List.copyOf(abilities)
            ));
        }
        return new CombatDataCatalog.EncounterDefinition("encounter_" + locationId, locationId, rows, cols, playerStart, List.copyOf(enemies));
    }

    private static ChallengeDefinition parseChallenge(Map<String, Object> node, String fieldPath) {
        String type = asString(node.get("type"), fieldPath + ".type");
        String difficulty = optionalString(node.get("difficulty"), fieldPath + ".difficulty");
        ChallengeDefinition.ChallengeResultDefinition onSuccess = parseChallengeResult(asMap(node.get("onSuccess"), fieldPath + ".onSuccess"), fieldPath + ".onSuccess");
        ChallengeDefinition.ChallengeResultDefinition onFailure = parseChallengeResult(asMap(node.get("onFailure"), fieldPath + ".onFailure"), fieldPath + ".onFailure");
        return new ChallengeDefinition(type, difficulty, onSuccess, onFailure);
    }

    private static ChallengeDefinition.ChallengeResultDefinition parseChallengeResult(Map<String, Object> node, String fieldPath) {
        Map<String, Boolean> setFlags = asBooleanMap(node.get("setFlags"), fieldPath + ".setFlags");
        String itemGain = optionalString(node.get("itemGain"), fieldPath + ".itemGain");
        Integer honorGain = optionalInt(node.get("honorGain"), fieldPath + ".honorGain");
        Integer honorCost = optionalInt(node.get("honorCost"), fieldPath + ".honorCost");
        Integer juiceCost = optionalInt(node.get("juiceCost"), fieldPath + ".juiceCost");
        Integer damage = optionalInt(node.get("damage"), fieldPath + ".damage");
        String failTarget = optionalString(node.get("failTarget"), fieldPath + ".failTarget");
        String message = optionalString(node.get("message"), fieldPath + ".message");
        return new ChallengeDefinition.ChallengeResultDefinition(setFlags, itemGain, honorGain, honorCost, juiceCost, damage, failTarget, message);
    }

    private static boolean isDeclared(
            String varName,
            String scopePath,
            Map<String, SectionDefinition> campaignSections,
            Map<String, Boolean> globalVariables
    ) {
        if (varName.startsWith("!")) {
            varName = varName.substring(1);
        }
        if (globalVariables.containsKey(varName)) {
            return true;
        }
        if (scopePath == null || "/".equals(scopePath)) {
            return false;
        }
        String[] parts = scopePath.split("/");
        SectionDefinition current = null;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (current == null) {
                current = campaignSections.get(part);
            } else {
                current = current.subSections().get(part);
            }
            if (current != null && current.variables().containsKey(varName)) {
                return true;
            }
        }
        return false;
    }

    private static void validateVariables(
            Map<String, Room> flatRooms,
            Map<String, String> roomToScopePath,
            Map<String, SectionDefinition> campaignSections,
            Map<String, Boolean> globalVariables
    ) {
        for (Map.Entry<String, Room> entry : flatRooms.entrySet()) {
            String roomId = entry.getKey();
            Room room = entry.getValue();
            String scopePath = roomToScopePath.get(roomId);

            for (PathDefinition path : room.paths().values()) {
                for (String flag : path.requireFlags()) {
                    String cleanFlag = flag.startsWith("!") ? flag.substring(1) : flag;
                    if (!room.variables().containsKey(cleanFlag) && !isDeclared(cleanFlag, scopePath, campaignSections, globalVariables)) {
                        System.err.println("WARNING: Room '" + roomId + "' path references undeclared variable: " + flag);
                    }
                }
            }

            if (room.dialogue() != null) {
                for (Map.Entry<String, DialogueDefinition.DialogueNode> nodeEntry : room.dialogue().nodes().entrySet()) {
                    DialogueDefinition.DialogueNode node = nodeEntry.getValue();
                    for (String flag : node.setFlags().keySet()) {
                        if (!room.variables().containsKey(flag) && !isDeclared(flag, scopePath, campaignSections, globalVariables)) {
                            System.err.println("WARNING: Room '" + roomId + "' dialogue node '" + nodeEntry.getKey() + "' sets undeclared variable: " + flag);
                        }
                    }
                    for (DialogueDefinition.DialogueChoice choice : node.choices()) {
                        for (String flag : choice.setFlags().keySet()) {
                            if (!room.variables().containsKey(flag) && !isDeclared(flag, scopePath, campaignSections, globalVariables)) {
                                System.err.println("WARNING: Room '" + roomId + "' dialogue choice sets undeclared variable: " + flag);
                            }
                        }
                    }
                }
            }

            if (room.challenge() != null) {
                ChallengeDefinition challenge = room.challenge();
                if (challenge.onSuccess() != null) {
                    for (String flag : challenge.onSuccess().setFlags().keySet()) {
                        if (!room.variables().containsKey(flag) && !isDeclared(flag, scopePath, campaignSections, globalVariables)) {
                            System.err.println("WARNING: Room '" + roomId + "' challenge onSuccess sets undeclared variable: " + flag);
                        }
                    }
                }
                if (challenge.onFailure() != null) {
                    for (String flag : challenge.onFailure().setFlags().keySet()) {
                        if (!room.variables().containsKey(flag) && !isDeclared(flag, scopePath, campaignSections, globalVariables)) {
                            System.err.println("WARNING: Room '" + roomId + "' challenge onFailure sets undeclared variable: " + flag);
                        }
                    }
                }
            }
        }
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

    private static String optionalString(Object value, String fieldPath) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalStateException("Expected string at '" + fieldPath + "'.");
    }

    private static Integer optionalInt(Object value, String fieldPath) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalStateException("Expected integer at '" + fieldPath + "'.");
    }

    private static List<String> asStringList(Object value, String fieldPath) {
        if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
            return List.copyOf(list.stream().map(String.class::cast).toList());
        }
        throw new IllegalStateException("Expected string list at '" + fieldPath + "'.");
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

    private static Boolean optionalBool(Object value, String fieldPath) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalStateException("Expected boolean at '" + fieldPath + "'.");
    }

    private static Map<String, Boolean> asBooleanMap(Object value, String fieldPath) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Boolean> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String k = entry.getKey().toString();
                Object v = entry.getValue();
                if (v instanceof Boolean b) {
                    result.put(k, b);
                } else {
                    throw new IllegalStateException("Expected boolean value for key '" + k + "' in map at '" + fieldPath + "'.");
                }
            }
            return result;
        }
        throw new IllegalStateException("Expected map at '" + fieldPath + "'.");
    }

    record LocationText(
            String title,
            List<String> descriptionLines,
            List<String> dialogLines,
            String autoRouteMessage,
            Direction autoRouteDirection
    ) {
    }

    public record ItemDefinition(
            String id,
            String name,
            String category,
            Integer damage,
            Integer range
    ) {
        public boolean isWeapon() {
            return "weapon".equals(category);
        }
        public int getDamage() {
            return damage != null ? damage : 0;
        }
        public int getRange() {
            return range != null ? range : 1;
        }
    }

    public record ItemPlacement(
            String itemId,
            String locationId
    ) {
    }
}
