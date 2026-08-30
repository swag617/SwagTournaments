package com.swag.tournaments.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private Connection connection;
    private final JavaPlugin plugin;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws SQLException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, "tournaments.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }

        connection = DriverManager.getConnection(url);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA synchronous=NORMAL");
        }

        createTables();
        plugin.getLogger().info("Database initialised at " + dbFile.getPath());
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS tournament_instances (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        template_id TEXT NOT NULL,
                        status TEXT NOT NULL,
                        started_at INTEGER NOT NULL,
                        ended_at INTEGER,
                        winner_uuid TEXT,
                        winner_score REAL,
                        participant_count INTEGER DEFAULT 0,
                        source TEXT DEFAULT 'MANUAL',
                        type TEXT
                    )
                    """);

            // Defensive migration: tournament_instances may already exist (without this
            // column) on servers upgrading from before category filtering was added to
            // /tournament history — see PastTournamentsGUI's filter row and
            // TournamentRepository#getHistory's optional type filter. There's no migration
            // framework in this codebase yet, so ALTER TABLE ADD COLUMN is added directly to
            // CREATE TABLE IF NOT EXISTS above (fresh installs) and attempted unconditionally
            // here too, swallowing the failure when the column already exists.
            try {
                stmt.execute("ALTER TABLE tournament_instances ADD COLUMN type TEXT");
            } catch (SQLException ignored) {
                // Column already exists.
            }

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS tournament_scores (
                        instance_id INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        score REAL DEFAULT 0.0,
                        final_rank INTEGER DEFAULT 0,
                        reward_given INTEGER DEFAULT 0,
                        joined_at INTEGER NOT NULL,
                        PRIMARY KEY (instance_id, player_uuid),
                        FOREIGN KEY (instance_id) REFERENCES tournament_instances(id) ON DELETE CASCADE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_tournament_stats (
                        player_uuid TEXT PRIMARY KEY,
                        player_name TEXT NOT NULL,
                        tournaments_entered INTEGER DEFAULT 0,
                        tournaments_won INTEGER DEFAULT 0,
                        total_first_places INTEGER DEFAULT 0,
                        total_second_places INTEGER DEFAULT 0,
                        total_third_places INTEGER DEFAULT 0,
                        best_score_ever REAL DEFAULT 0.0,
                        last_seen INTEGER,
                        first_seen INTEGER
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_type_stats (
                        player_uuid TEXT NOT NULL,
                        type TEXT NOT NULL,
                        entered INTEGER DEFAULT 0,
                        won INTEGER DEFAULT 0,
                        first_places INTEGER DEFAULT 0,
                        second_places INTEGER DEFAULT 0,
                        third_places INTEGER DEFAULT 0,
                        best_score REAL DEFAULT 0,
                        PRIMARY KEY (player_uuid, type)
                    )
                    """);

            // Feature 2 (Hall of Fame): one all-time-best row per tournament TEMPLATE (not
            // type, not a single global ranking — different templates use different
            // ScoringModes, so a raw score is only meaningful compared against the same
            // template's own history). Net-new table, no migration idiom needed.
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS template_records (
                        template_id TEXT PRIMARY KEY,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        score REAL NOT NULL,
                        instance_id INTEGER,
                        achieved_at INTEGER NOT NULL
                    )
                    """);
        }

        // Migrations: add columns that may not exist on databases created before this feature
        // (Personal Profile Card). Mirrors the try-ALTER/catch-and-swallow idiom used by sibling
        // Swag plugins (e.g. SwagFishing's DatabaseManager) for adding columns to existing tables.
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE player_tournament_stats ADD COLUMN lifetime_rewards_money REAL DEFAULT 0");
        } catch (SQLException ignored) {
            // Column already exists
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing database connection: " + e.getMessage());
            }
        }
    }

    public Connection getConnection() {
        return connection;
    }
}
