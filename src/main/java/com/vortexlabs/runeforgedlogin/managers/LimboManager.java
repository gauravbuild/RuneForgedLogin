package com.vortexlabs.runeforgedlogin.managers;

import com.vortexlabs.runeforgedlogin.RuneForgedLogin;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

/**
 * The Virtual Limbo world. Generates a lightweight all-air void dimension and
 * suspends unauthenticated players inside it with a cinematic blindness veil.
 *
 * Supports an admin-configured register-spawn override that, when set, replaces
 * the void world as the holding location for unauthenticated players.
 *
 * @author Gaurav @ VortexLabs | Development
 */
public class LimboManager {

    public static final String LIMBO_WORLD_NAME = "rfl_limbo";
    private static final double LIMBO_X = 0.5;
    private static final double LIMBO_Y = 64.0;
    private static final double LIMBO_Z = 0.5;

    private final RuneForgedLogin plugin;
    private World limboWorld;
    private Location registerSpawn;

    public LimboManager(RuneForgedLogin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (plugin.getServer().getWorld(LIMBO_WORLD_NAME) == null) {
            WorldCreator creator = WorldCreator.name(LIMBO_WORLD_NAME)
                    .environment(World.Environment.THE_END)
                    .type(WorldType.FLAT)
                    .generateStructures(false)
                    .generator(voidGenerator());
            limboWorld = creator.createWorld();
        } else {
            limboWorld = plugin.getServer().getWorld(LIMBO_WORLD_NAME);
        }

        if (limboWorld != null) {
            limboWorld.setSpawnLocation(0, (int) LIMBO_Y, 0);
            limboWorld.setDifficulty(Difficulty.PEACEFUL);
            limboWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            try {
                limboWorld.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
            } catch (Throwable ignored) {
            }
        }

        registerSpawn = loadRegisterSpawn();
    }

    private ChunkGenerator voidGenerator() {
        return new ChunkGenerator() {
            @Override
            public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
                return createChunkData(world);
            }
        };
    }

    // ----- Register spawn override ---------------------------------------

    private Location loadRegisterSpawn() {
        String raw = plugin.getConfig().getString("settings.register-spawn", null);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split(";");
        if (parts.length < 6) {
            return null;
        }
        try {
            World w = Bukkit.getWorld(parts[0]);
            if (w == null) {
                return null;
            }
            return new Location(w,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]),
                    Float.parseFloat(parts[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void setRegisterSpawn(Location location) {
        this.registerSpawn = location.clone();
        String serialised = location.getWorld().getName() + ";"
                + location.getX() + ";" + location.getY() + ";" + location.getZ() + ";"
                + location.getYaw() + ";" + location.getPitch();
        plugin.getConfig().set("settings.register-spawn", serialised);
        plugin.saveConfig();
    }

    public boolean hasRegisterSpawn() {
        return registerSpawn != null;
    }

    // ----- Limbo entry / exit --------------------------------------------

    /**
     * The location unauthenticated players are held at: the admin register spawn
     * if set, otherwise the centre of the void Limbo world.
     */
    public Location getLimboLocation() {
        if (registerSpawn != null) {
            return registerSpawn.clone();
        }
        if (limboWorld != null) {
            return new Location(limboWorld, LIMBO_X, LIMBO_Y, LIMBO_Z);
        }
        // Extremely defensive fallback: first world's spawn.
        World fallback = plugin.getServer().getWorlds().get(0);
        return fallback.getSpawnLocation();
    }

    public boolean isInLimboWorld(Player player) {
        return player.getWorld() != null && LIMBO_WORLD_NAME.equals(player.getWorld().getName());
    }

    public void enterLimbo(Player player) {
        player.teleport(getLimboLocation());
        applyBlindness(player);
        player.setFallDistance(0f);
        player.setHealth(Math.max(1.0, player.getHealth())); // defensive
    }

    public void applyBlindness(Player player) {
        if (!plugin.getConfig().getBoolean("limbo-effects.blindness-veil", true)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 0, false, false, false));
    }

    public void removeBlindness(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOW);
    }

    public void sendMessage(String s) {
        // placeholder retained for compatibility
    }

    /**
     * Hot-reload: re-read the register-spawn override from the freshly-saved config.
     */
    public void reload() {
        registerSpawn = loadRegisterSpawn();
    }

    public void cleanup() {
        // Do not delete the world — preserve it across restarts.
    }
}