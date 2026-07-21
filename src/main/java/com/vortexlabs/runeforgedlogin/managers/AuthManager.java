package com.vortexlabs.runeforgedlogin.managers;

import com.vortexlabs.runeforgedlogin.RuneForgedLogin;
import com.vortexlabs.runeforgedlogin.storage.DatabaseManager;
import com.vortexlabs.runeforgedlogin.storage.PlayerData;
import com.vortexlabs.runeforgedlogin.utils.PasswordUtil;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Core authentication state machine. Tracks which players are authenticated,
 * which are trapped in Limbo, and which have been identified as premium Mojang
 * accounts. Owns credential verification, registration, and the async Mojang
 * profile lookup used for the auto-login bypass.
 *
 * @author Gaurav @ VortexLabs | Development
 */
public class AuthManager {

    private final RuneForgedLogin plugin;
    private final DatabaseManager db;
    private final SessionManager sessionManager;

    private final ConcurrentHashMap<UUID, Boolean> authenticated = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> unauthenticated = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> premium = new ConcurrentHashMap<>();

    public AuthManager(RuneForgedLogin plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        this.sessionManager = plugin.getSessionManager();
    }

    public void init() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, premium::clear, 60_000L, 60_000L);
    }

    // ----- State queries --------------------------------------------------

    public boolean isAuthenticated(UUID uuid) {
        return authenticated.containsKey(uuid);
    }

    public boolean isUnauthenticated(UUID uuid) {
        return unauthenticated.containsKey(uuid);
    }

    public boolean isPremium(UUID uuid) {
        return premium.getOrDefault(uuid, false);
    }

    public void setAuthenticated(UUID uuid) {
        authenticated.put(uuid, true);
        unauthenticated.remove(uuid);
    }

    public void setUnauthenticated(UUID uuid) {
        unauthenticated.put(uuid, true);
        authenticated.remove(uuid);
    }

    public void setPremium(UUID uuid, boolean value) {
        premium.put(uuid, value);
    }

    public void clear(UUID uuid) {
        authenticated.remove(uuid);
        unauthenticated.remove(uuid);
        premium.remove(uuid);
    }

    // ----- Credential operations -----------------------------------------

    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> db.loadPlayer(uuid));
    }

    public CompletableFuture<Boolean> isRegistered(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData d = db.loadPlayer(uuid);
            return d != null && d.isRegistered() && d.getPasswordHash() != null;
        });
    }

    public CompletableFuture<Void> register(UUID uuid, String username, String plain) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData data = db.loadPlayer(uuid);
            if (data == null) {
                data = new PlayerData(uuid.toString(), username);
            }
            String salt = PasswordUtil.generateSalt();
            data.setSalt(salt);
            data.setPasswordHash(PasswordUtil.hash(plain, salt));
            data.setRegistered(true);
            data.setUsername(username);
            db.savePlayer(data);
            return null;
        });
    }

    public CompletableFuture<Boolean> updatePassword(UUID uuid, String username, String oldPlain, String newPlain) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData data = db.loadPlayer(uuid);
            if (data == null) {
                return false;
            }
            if (!PasswordUtil.verify(oldPlain, data.getSalt(), data.getPasswordHash())) {
                return false;
            }
            String salt = PasswordUtil.generateSalt();
            data.setSalt(salt);
            data.setPasswordHash(PasswordUtil.hash(newPlain, salt));
            data.setUsername(username);
            db.savePlayer(data);
            return true;
        });
    }

    public boolean verifyPassword(String plain, PlayerData data) {
        if (plain == null || data == null || data.getSalt() == null || data.getPasswordHash() == null) {
            return false;
        }
        return PasswordUtil.verify(plain, data.getSalt(), data.getPasswordHash());
    }

    // ----- Premium (Mojang) check ----------------------------------------

    /**
     * Asynchronously determines whether a username corresponds to an officially
     * verified, premium Mojang account. When the server is in online-mode every
     * connecting player is implicitly premium; otherwise the Mojang profile API
     * is consulted.
     */
    public CompletableFuture<Boolean> checkPremium(String username) {
        return CompletableFuture.supplyAsync(() -> {
            if (Bukkit.getOnlineMode()) {
                return true;
            }
            HttpURLConnection conn = null;
            try {
                String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
                URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + encoded);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                int code = conn.getResponseCode();
                if (code == 200) {
                    try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
                        String body = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                        return body.contains("\"id\"");
                    }
                }
                return false;
            } catch (IOException e) {
                return false;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).completeOnTimeout(false, 5, TimeUnit.SECONDS);
    }

    public boolean autoLoginEnabled() {
        return plugin.getConfig().getBoolean("settings.mojang-auto-login", true);
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }
}