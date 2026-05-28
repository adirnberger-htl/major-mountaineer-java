package org.example.majorgame.game;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class JdbcGameSaveRepository implements GameSaveRepository {
    private static final int SCHEMA_VERSION = 1;
    private static final String DEFAULT_DIRECTORY = ".major-game";
    private static final String DEFAULT_DB_NAME = "major-game.db";
    private static final String UPSERT_GAME_STATE_SQL =
            "INSERT OR REPLACE INTO game_state (" +
                    "id, location, checkpoint, apple_juice, honor, zero_juice_turns, " +
                    "commands_since_respawn, moves_since_respawn, apple_juices_drunk_since_respawn, death_count" +
                    ") VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final String jdbcUrl;

    private JdbcGameSaveRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initSchema();
    }

    static JdbcGameSaveRepository defaultRepository() {
        return new JdbcGameSaveRepository(resolveJdbcUrl());
    }

    @Override
    public Optional<GameSaveState> load() {
        try (Connection conn = openConnection()) {
            GameSaveState state = loadState(conn);
            return Optional.ofNullable(state);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load game state.", exception);
        }
    }

    @Override
    public void save(GameSaveState state) {
        if (state == null) {
            throw new IllegalArgumentException("state");
        }
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            upsertGameState(conn, state);
            clearCollections(conn);
            insertInventory(conn, state.inventory());
            insertItemLocations(conn, state.itemLocations());
            insertClearedCombatLocations(conn, state.clearedCombatLocations());
            conn.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save game state.", exception);
        }
    }

    private static String resolveJdbcUrl() {
        String override = GamePersistenceConfig.dbPathOverride();
        if (override != null && !override.isBlank()) {
            String trimmed = override.trim();
            if (trimmed.startsWith("jdbc:")) {
                return trimmed;
            }
            if (":memory:".equalsIgnoreCase(trimmed)) {
                return "jdbc:sqlite::memory:";
            }
            Path path = Paths.get(trimmed).toAbsolutePath();
            createParentDirectory(path);
            return "jdbc:sqlite:" + path;
        }
        Path path = Paths.get(System.getProperty("user.home"), DEFAULT_DIRECTORY, DEFAULT_DB_NAME).toAbsolutePath();
        createParentDirectory(path);
        return "jdbc:sqlite:" + path;
    }

    private static void createParentDirectory(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create save directory: " + parent, exception);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void initSchema() {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
                stmt.execute("CREATE TABLE IF NOT EXISTS game_state (" +
                        "id INTEGER PRIMARY KEY CHECK (id = 1)," +
                        "location TEXT NOT NULL," +
                        "checkpoint TEXT NOT NULL," +
                        "apple_juice INTEGER NOT NULL," +
                        "honor INTEGER NOT NULL," +
                        "zero_juice_turns INTEGER NOT NULL," +
                        "commands_since_respawn INTEGER NOT NULL," +
                        "moves_since_respawn INTEGER NOT NULL," +
                        "apple_juices_drunk_since_respawn INTEGER NOT NULL," +
                        "death_count INTEGER NOT NULL" +
                        ")");
                stmt.execute("CREATE TABLE IF NOT EXISTS inventory (" +
                        "item_id TEXT PRIMARY KEY" +
                        ")");
                stmt.execute("CREATE TABLE IF NOT EXISTS item_locations (" +
                        "item_id TEXT PRIMARY KEY," +
                        "location_id TEXT NOT NULL" +
                        ")");
                stmt.execute("CREATE TABLE IF NOT EXISTS cleared_combat_locations (" +
                        "location_id TEXT PRIMARY KEY" +
                        ")");
            }
            int version = readSchemaVersion(conn);
            if (version == 0) {
                insertSchemaVersion(conn);
            } else if (version != SCHEMA_VERSION) {
                throw new IllegalStateException("Unsupported save schema version: " + version);
            }
            conn.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize game database.", exception);
        }
    }

    private int readSchemaVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version")) {
            if (!rs.next()) {
                return 0;
            }
            int version = rs.getInt("version");
            if (rs.next()) {
                throw new IllegalStateException("Multiple schema versions found.");
            }
            return version;
        }
    }

    private void insertSchemaVersion(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO schema_version (version) VALUES (?)")) {
            stmt.setInt(1, SCHEMA_VERSION);
            stmt.executeUpdate();
        }
    }

    private GameSaveState loadState(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT location, checkpoint, apple_juice, honor, zero_juice_turns, " +
                        "commands_since_respawn, moves_since_respawn, apple_juices_drunk_since_respawn, death_count " +
                        "FROM game_state WHERE id = 1");
             ResultSet rs = stmt.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            String location = rs.getString("location");
            String checkpoint = rs.getString("checkpoint");
            int appleJuice = rs.getInt("apple_juice");
            int honor = rs.getInt("honor");
            int zeroJuiceTurns = rs.getInt("zero_juice_turns");
            int commandsSinceRespawn = rs.getInt("commands_since_respawn");
            int movesSinceRespawn = rs.getInt("moves_since_respawn");
            int appleJuicesDrunkSinceRespawn = rs.getInt("apple_juices_drunk_since_respawn");
            int deathCount = rs.getInt("death_count");
            Set<String> inventory = loadInventory(conn);
            Map<String, String> itemLocations = loadItemLocations(conn);
            Set<String> clearedCombatLocations = loadClearedCombatLocations(conn);
            return new GameSaveState(
                    location,
                    checkpoint,
                    appleJuice,
                    honor,
                    zeroJuiceTurns,
                    commandsSinceRespawn,
                    movesSinceRespawn,
                    appleJuicesDrunkSinceRespawn,
                    deathCount,
                    inventory,
                    itemLocations,
                    clearedCombatLocations
            );
        }
    }

    private Set<String> loadInventory(Connection conn) throws SQLException {
        Set<String> inventory = new LinkedHashSet<>();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT item_id FROM inventory ORDER BY item_id");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                inventory.add(rs.getString("item_id"));
            }
        }
        return inventory;
    }

    private Map<String, String> loadItemLocations(Connection conn) throws SQLException {
        Map<String, String> itemLocations = new LinkedHashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT item_id, location_id FROM item_locations ORDER BY item_id");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                itemLocations.put(rs.getString("item_id"), rs.getString("location_id"));
            }
        }
        return itemLocations;
    }

    private Set<String> loadClearedCombatLocations(Connection conn) throws SQLException {
        Set<String> locations = new LinkedHashSet<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT location_id FROM cleared_combat_locations ORDER BY location_id");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                locations.add(rs.getString("location_id"));
            }
        }
        return locations;
    }

    private void upsertGameState(Connection conn, GameSaveState state) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(UPSERT_GAME_STATE_SQL)) {
            stmt.setString(1, state.locationId());
            stmt.setString(2, state.checkpointLocationId());
            stmt.setInt(3, state.appleJuice());
            stmt.setInt(4, state.honor());
            stmt.setInt(5, state.zeroJuiceTurns());
            stmt.setInt(6, state.commandsSinceRespawn());
            stmt.setInt(7, state.movesSinceRespawn());
            stmt.setInt(8, state.appleJuicesDrunkSinceRespawn());
            stmt.setInt(9, state.deathCount());
            stmt.executeUpdate();
        }
    }

    private void clearCollections(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM inventory");
            stmt.executeUpdate("DELETE FROM item_locations");
            stmt.executeUpdate("DELETE FROM cleared_combat_locations");
        }
    }

    private void insertInventory(Connection conn, Set<String> inventory) throws SQLException {
        if (inventory.isEmpty()) {
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO inventory (item_id) VALUES (?)")) {
            for (String itemId : inventory) {
                stmt.setString(1, itemId);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void insertItemLocations(Connection conn, Map<String, String> itemLocations) throws SQLException {
        if (itemLocations.isEmpty()) {
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO item_locations (item_id, location_id) VALUES (?, ?)")) {
            for (Map.Entry<String, String> entry : itemLocations.entrySet()) {
                stmt.setString(1, entry.getKey());
                stmt.setString(2, entry.getValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void insertClearedCombatLocations(Connection conn, Set<String> locations) throws SQLException {
        if (locations.isEmpty()) {
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO cleared_combat_locations (location_id) VALUES (?)")) {
            for (String locationId : locations) {
                stmt.setString(1, locationId);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
}
