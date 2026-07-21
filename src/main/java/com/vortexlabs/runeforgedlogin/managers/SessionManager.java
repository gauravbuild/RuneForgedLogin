package com.vortexlabs.runeforgedlogin.managers;

import com.vortexlabs.runeforgedlogin.RuneForgedLogin;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages IP session locks, failed-attempt tracking, and IP ban/lockout state.
 * All state lives in memory (resets on restart) — IP session validity is also
 * cross-referenced against persisted last-login data.
 *
 * @author Gaurav @ VortexLabs | Development
 */
public class SessionManager {

    private final RuneForgedLogin plugin;

    // IP -> expiry timestamp (millis) for both brute-force lockouts and manual bans.
    private final ConcurrentHashMap<String, Long> lockedIps = new ConcurrentHashMap<>();

    // IP -> consecutive failed attempts count.
    private final ConcurrentHashMap<String, Integer> failedAttempts = new ConcurrentHashMap<>();

    // Active session records (created on successful auth), keyed by player UUID.
    private final ConcurrentHashMap<UUID, SessionRecord> activeSessions = new ConcurrentHashMap<>();

    // Reverse lookup: username -> UUID, populated for /rfl sessions display.
    private final ConcurrentHashMap<UUID, String> sessionNames = new ConcurrentHashMap<>();

    public SessionManager(RuneForgedLogin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::purgeExpired, 200L, 200L);
    }

    public void reload() {
        // Drop transient lockout state so a reload reflects the fresh config.
        lockedIps.clear();
        failedAttempts.clear();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        lockedIps.entrySet().removeIf(e -> e.getValue() <= now);
        activeSessions.entrySet().removeIf(e -> e.getValue().expiresAt <= now);
    }

    // ----- Config helpers -------------------------------------------------

    private int maxFailed() {
        return plugin.getConfig().getInt("security.max-failed-attempts", 3);
    }

    private long lockoutMillis() {
        return plugin.getConfig().getLong("security.ip-lockout-time", 10) * 60_000L;
    }

    private long lockDurationMillis() {
        return plugin.getConfig().getLong("settings.session.ip-lock-duration", 15) * 60_000L;
    }

    // ----- IP lock / ban state --------------------------------------------

    public boolean isIpLocked(String ip) {
        if (ip == null) {
            return false;
        }
        Long expiry = lockedIps.get(ip);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    public long getLockExpiry(String ip) {
        Long expiry = lockedIps.get(ip);
        return expiry == null ? 0 : expiry;
    }

    public void banIp(String ip) {
        if (ip == null) {
            return;
        }
        lockedIps.put(ip, Long.MAX_VALUE);
    }

    public boolean unbanIp(String ip) {
        return lockedIps.remove(ip) != null;
    }

    // ----- Brute-force tracking -------------------------------------------

    /**
     * Records a failed login attempt for the given IP. Returns the remaining
     * attempts before the IP is locked, or -1 if the IP has just been locked
     * by this call.
     */
    public int recordFailedAttempt(String ip) {
        if (ip == null) {
            return 0;
        }
        int attempts = failedAttempts.merge(ip, 1, Integer::sum);
        if (attempts >= maxFailed()) {
            lockBruteForce(ip);
            failedAttempts.remove(ip);
            return -1;
        }
        return maxFailed() - attempts;
    }

    private void lockBruteForce(String ip) {
        lockedIps.put(ip, System.currentTimeMillis() + lockoutMillis());
    }

    public void clearFailedAttempts(String ip) {
        if (ip != null) {
            failedAttempts.remove(ip);
        }
    }

    // ----- Active sessions ------------------------------------------------

    public static final class SessionRecord {
        public final String ip;
        public final String username;
        public final long createdAt;
        public final long expiresAt;

        SessionRecord(String ip, String username, long createdAt, long expiresAt) {
            this.ip = ip;
            this.username = username;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }
    }

    public void createSession(Player player) {
        long now = System.currentTimeMillis();
        String ip = ipOf(player);
        SessionRecord rec = new SessionRecord(ip, player.getName(), now, now + lockDurationMillis());
        activeSessions.put(player.getUniqueId(), rec);
        sessionNames.put(player.getUniqueId(), player.getName());
    }

    public void removeSession(UUID uuid) {
        activeSessions.remove(uuid);
        sessionNames.remove(uuid);
    }

    /**
     * Returns true if a valid session (same IP, not expired) exists for the player.
     */
    public boolean isSessionValid(UUID uuid, String ip) {
        SessionRecord rec = activeSessions.get(uuid);
        if (rec == null) {
            return false;
        }
        if (rec.expiresAt <= System.currentTimeMillis()) {
            activeSessions.remove(uuid);
            return false;
        }
        return ip != null && ip.equals(rec.ip);
    }

    public List<SessionRecord> getActiveSessions() {
        long now = System.currentTimeMillis();
        List<SessionRecord> live = new ArrayList<>();
        for (SessionRecord rec : activeSessions.values()) {
            if (rec.expiresAt > now) {
                live.add(rec);
            }
        }
        return live;
    }

    private String ipOf(Player player) {
        try {
            return player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        } catch (Exception e) {
            return null;
        }
    }
}