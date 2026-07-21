package com.vortexlabs.runeforgedlogin.utils;

import com.vortexlabs.runeforgedlogin.RuneForgedLogin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Message dispatch helper. Reads configured messages from config.yml and applies
 * the RuneForged prefix and colour pipeline automatically.
 *
 * @author Gaurav @ VortexLabs | Development
 */
public final class MessageUtil {

    private MessageUtil() {
    }

    private static String prefix() {
        String raw = RuneForgedLogin.getInstance().getConfig().getString("messages.prefix", "");
        return ColorUtil.colorize(raw);
    }

    /**
     * Resolve a configured message path, colourize it, but do NOT prefix it.
     */
    public static String raw(String path, String def) {
        return ColorUtil.colorize(RuneForgedLogin.getInstance().getConfig().getString(path, def));
    }

    /**
     * Resolve a configured message, replace the provided placeholder tokens, colourize
     * and apply the configured prefix.
     */
    public static String build(String path, String def, Object... pairs) {
        String msg = RuneForgedLogin.getInstance().getConfig().getString(path, def);
        return buildString(msg, pairs);
    }

    /**
     * Take a raw message string, replace tokens, colourize and apply the prefix.
     */
    public static String buildString(String msg, Object... pairs) {
        if (msg == null) {
            msg = "";
        }
        if (pairs != null) {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                msg = msg.replace("%" + pairs[i] + "%", String.valueOf(pairs[i + 1]));
            }
        }
        return prefix() + ColorUtil.colorize(msg);
    }

    public static void send(CommandSender sender, String path, String def, Object... pairs) {
        sender.sendMessage(build(path, def, pairs));
    }

    public static void sendRaw(CommandSender sender, String message, Object... pairs) {
        sender.sendMessage(buildString(message, pairs));
    }
}