package com.vortexlabs.runeforgedlogin.storage;

import com.vortexlabs.runeforgedlogin.RuneForgedLogin;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Persistent storage layer for RuneForgedLogin. Supports SQLite (local file) and
 * MySQL (external database) backends.
 *
 * All query helpers execute synchronously here; callers wrap them in
 * {@link CompletableFuture#supplyAsync} to keep them off the main thread.
 *
 * @author Gaurav @ VortexLabs | Development
 */
public class DatabaseManager {

    private final RuneForgedLogin plugin;
    private final boolean useMySQL;
    private Connection connection;

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS rfl_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "username VARCHAR(64), " +
                    "password_hash VARCHAR(512), " +
                    "salt VARCHAR(64), " +
                    "registered BOOLEAN DEFAULT FALSE, " +
                    "last_world VARCHAR(128), " +
                    "last_x DOUBLE, last_y DOUBLE, last_z DOUBLE, " +
                    "last_yaw FLOAT, last_pitch FLOAT, " +
                    "last_ip VARCHAR(64), " +
                    "last_login BIGINT DEFAULT 0, " +
                    "is_premium BOOLEAN DEFAULT FALSE" +
                    ")";

    private static final String INSERT_OR_REPLACE =
            "INSERT OR REPLACE INTO rfl_players " +
                    "(uuid,username,password_hash,salt,registered,last_world,last_x,last_y,last_z," +
                    "last_yaw,last_pitch,last_ip,last_login,is_premium) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String MYSQL_UPSERT =
            "INSERT INTO rfl_players " +
                    "(uuid,username,password_hash,salt,registered,last_world,last_x,last_y,last_z," +
                    "last_yaw,last_pitch,last_ip,last_login,is_premium) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "username=VALUES(username),password_hash=VALUES(password_hash)," +
                    "salt=VALUES(salt),registered=VALUES(registered),last_world=VALUES(last_world)," +
                    "last_x=VALUES(last_x),last_y=VALUES(last_y),last_z=VALUES(last_z)," +
                    "last_yaw=VALUES(last_yaw),last_pitch=VALUES(last_pitch)," +
                    "last_ip=VALUES(last_ip),last_login=VALUES(last_login),is_premium=VALUES(is_premium)";

    private static final String SELECT_BY_UUID =
            "SELECT * FROM rfl_players WHERE uuid = ?";

    private static final String UPDATE_LOCATION =
            "UPDATE rfl_players SET last_world=?,last_x=?,last_y=?,last_z=?,last_yaw=?,last_pitch=? WHERE uuid=?";

    private static final String UPDATE_LOGIN =
            "UPDATE rfl_players SET last_ip=?,last_login=?,username=? WHERE uuid=?";

    private static final String UPDATE_PREMIUM =
            "UPDATE rfl_players SET is_premium=? WHERE uuid=?";

    private static final String DELETE_PLAYER =
            "DELETE FROM rfl_players WHERE uuid=?";

    public DatabaseManager(RuneForgedLogin plugin) {
        this.plugin = plugin;
        this.useMySQL = "MYSQL".equalsIgnoreCase(plugin.getConfig().getString("settings.database-type", "SQLITE"));
    }

    public void init() {
        try {
            ensureConnection();
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(CREATE_TABLE);
            }
            plugin.getLogger().info("Database initialised (" + (useMySQL ? "MySQL" : "SQLite") + ").");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialise database", e);
        }
    }

    public synchronized Connection ensureConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            try (Statement probe = connection.createStatement()) {
                probe.execute("SELECT 1");
                return connection;
            } catch (SQLException ignored) {
                // fall through and reconnect
            }
        }
        if (useMySQL) {
            String host = plugin.getConfig().getString("settings.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("settings.mysql.port", 3306);
            String name = plugin.getConfig().getString("settings.mysql.name", "runeforged");
            String user = plugin.getConfig().getString("settings.mysql.user", "root");
            String pass = plugin.getConfig().getString("settings.mysql.password", "password");
            String url = "jdbc:mysql://" + host + ":" + port + "/" + name
                    + "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8";
            connection = DriverManager.getConnection(url, user, pass);
        } else {
            String path = plugin.getDataFolder().getAbsolutePath() + "/data.db";
            connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        }
        return connection;
    }

    public synchronized PlayerData loadPlayer(UUID uuid) {
        try (PreparedStatement ps = ensureConnection().prepareStatement(SELECT_BY_UUID)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                PlayerData data = new PlayerData(rs.getString("uuid"), rs.getString("username"));
                data.setPasswordHash(rs.getString("password_hash"));
                data.setSalt(rs.getString("salt"));
                data.setRegistered(rs.getBoolean("registered"));
                data.setLastWorld(rs.getString("last_world"));
                data.setLastX(rs.getDouble("last_x"));
                data.setLastY(rs.getDouble("last_y"));
                data.setLastZ(rs.getDouble("last_z"));
                data.setLastYaw(rs.getFloat("last_yaw"));
                data.setLastPitch(rs.getFloat("last_pitch"));
                data.setLastIp(rs.getString("last_ip"));
                data.setLastLogin(rs.getLong("last_login"));
                data.setPremium(rs.getBoolean("is_premium"));
                return data;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player " + uuid, e);
            return null;
        }
    }

    private void bindFull(PreparedStatement ps, PlayerData data) throws SQLException {
        int i = 1;
        ps.setString(i++, data.getUuid());
        ps.setString(i++, data.getUsername());
        ps.setString(i++, data.getPasswordHash());
        ps.setString(i++, data.getSalt());
        ps.setBoolean(i++, data.isRegistered());
        if (data.getLastWorld() != null) {
            ps.setString(i++, data.getLastWorld());
            ps.setDouble(i++, data.getLastX());
            ps.setDouble(i++, data.getLastY());
            ps.setDouble(i++, data.getLastZ());
            ps.setFloat(i++, data.getLastYaw());
            ps.setFloat(i++, data.getLastPitch());
        } else {
            ps.setString(i++, null);
            ps.setDouble(i++, 0);
            ps.setDouble(i++, 0);
            ps.setDouble(i++, 0);
            ps.setFloat(i++, 0f);
            ps.setFloat(i++, 0f);
        }
        ps.setString(i++, data.getLastIp());
        ps.setLong(i++, data.getLastLogin());
        ps.setBoolean(i, data.isPremium());
    }

    public synchronized void savePlayer(PlayerData data) {
        String sql = useMySQL ? MYSQL_UPSERT : INSERT_OR_REPLACE;
        try (PreparedStatement ps = ensureConnection().prepareStatement(sql)) {
            bindFull(ps, data);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player " + data.getUuid(), e);
        }
    }

    public synchronized void saveLocation(UUID uuid, Location location) {
        if (location == null) {
            return;
        }
        try (PreparedStatement ps = ensureConnection().prepareStatement(UPDATE_LOCATION)) {
            ps.setString(1, location.getWorld() != null ? location.getWorld().getName() : null);
            ps.setDouble(2, location.getX());
            ps.setDouble(3, location.getY());
            ps.setDouble(4, location.getZ());
            ps.setFloat(5, location.getYaw());
            ps.setFloat(6, location.getPitch());
            ps.setString(7, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save location for " + uuid, e);
        }
    }

    public synchronized void updateLogin(UUID uuid, String username, String ip, long loginTime) {
        try (PreparedStatement ps = ensureConnection().prepareStatement(UPDATE_LOGIN)) {
            ps.setString(1, ip);
            ps.setLong(2, loginTime);
            ps.setString(3, username);
            ps.setString(4, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update login for " + uuid, e);
        }
    }

    public synchronized void setPremium(UUID uuid, boolean premium) {
        try (PreparedStatement ps = ensureConnection().prepareStatement(UPDATE_PREMIUM)) {
            ps.setBoolean(1, premium);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update premium flag for " + uuid, e);
        }
    }

    public synchronized boolean unregisterPlayer(UUID uuid) {
        try (PreparedStatement ps = ensureConnection().prepareStatement(DELETE_PLAYER)) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to unregister player " + uuid, e);
            return false;
        }
    }

    public synchronized Location toLocation(PlayerData data) {
        if (data == null || !data.hasLocation()) {
            return null;
        }
        World world = plugin.getServer().getWorld(data.getLastWorld());
        if (world == null) {
            return null;
        }
        return new Location(world, data.getLastX(), data.getLastY(), data.getLastZ(),
                data.getLastYaw(), data.getLastPitch());
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing database connection", e);
        }
    }
}