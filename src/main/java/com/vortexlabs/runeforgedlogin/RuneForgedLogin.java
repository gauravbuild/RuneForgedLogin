package com.vortexlabs.runeforgedlogin;

import com.vortexlabs.runeforgedlogin.managers.AuthManager;
import com.vortexlabs.runeforgedlogin.managers.LimboManager;
import com.vortexlabs.runeforgedlogin.managers.SessionManager;
import com.vortexlabs.runeforgedlogin.storage.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RuneForgedLogin — Premium Account Security & Astral Limbo Authentication Gateway.
 *
 * Developed by Gaurav @ VortexLabs | Development.
 */
public final class RuneForgedLogin extends JavaPlugin {

    private static RuneForgedLogin instance;

    private DatabaseManager databaseManager;
    private AuthManager authManager;
    private LimboManager limboManager;
    private SessionManager sessionManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save the default configuration if it does not exist.
        saveDefaultConfig();

        // Initialise the storage layer first — everything depends on it.
        this.databaseManager = new DatabaseManager(this);
        databaseManager.init();

        // Core managers.
        this.sessionManager = new SessionManager(this);
        this.limboManager = new LimboManager(this);
        this.authManager = new AuthManager(this);

        sessionManager.init();
        limboManager.init();
        authManager.init();

        // >>> Listeners & commands are wired in the next slice. <<<

        getLogger().info("RuneForgedLogin by VortexLabs | Development has been enabled.");
    }

    @Override
    public void onDisable() {
        if (limboManager != null) {
            limboManager.cleanup();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("RuneForgedLogin has been disabled.");
    }

    /**
     * Live hot-reload of config.yml and manager re-initialisation.
     */
    public void reload() {
        reloadConfig();
        if (limboManager != null) {
            limboManager.reload();
        }
        if (sessionManager != null) {
            sessionManager.reload();
        }
    }

    public static RuneForgedLogin getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public LimboManager getLimboManager() {
        return limboManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }
}