package org.example.majorgame.game;

import java.util.Optional;

interface GameSaveRepository {
    Optional<GameSaveState> load();

    void save(GameSaveState state);

    static GameSaveRepository createDefault() {
        if (!GamePersistenceConfig.isEnabled()) {
            return new NoOpGameSaveRepository();
        }
        return JdbcGameSaveRepository.defaultRepository();
    }
}

final class GamePersistenceConfig {
    static final String PERSISTENCE_PROPERTY = "majorgame.persistence";
    static final String DB_PATH_PROPERTY = "majorgame.db.path";

    private GamePersistenceConfig() {
    }

    static boolean isEnabled() {
        String value = System.getProperty(PERSISTENCE_PROPERTY);
        return value == null || !value.trim().equalsIgnoreCase("disabled");
    }

    static String dbPathOverride() {
        return System.getProperty(DB_PATH_PROPERTY);
    }
}

final class NoOpGameSaveRepository implements GameSaveRepository {
    @Override
    public Optional<GameSaveState> load() {
        return Optional.empty();
    }

    @Override
    public void save(GameSaveState state) {
        // Persistence disabled by configuration.
    }
}
