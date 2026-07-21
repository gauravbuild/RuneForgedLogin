package com.vortexlabs.runeforgedlogin.utils;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local colour pipeline. Supports legacy '&' codes and the modern '&#RRGGBB' hex
 * structures used throughout RuneForgedLogin.
 *
 * @author Gaurav @ VortexLabs | Development
 */
public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {
    }

    /**
     * Translate a message containing legacy and/or hex colour codes into a
     * fully decorated string suitable for sending via {@code sendMessage}.
     */
    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        StringBuffer buffer = new StringBuffer();
        Matcher matcher = HEX_PATTERN.matcher(message);
        while (matcher.find()) {
            String hex = matcher.group(1);
            try {
                matcher.appendReplacement(buffer, ChatColor.of("#" + hex).toString());
            } catch (Throwable ignored) {
                matcher.appendReplacement(buffer, "");
            }
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}