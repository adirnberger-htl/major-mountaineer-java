package org.example.majorgame;

import javafx.scene.image.Image;

import java.util.Map;

/**
 * Shared UI utility methods used across view controllers.
 */
public final class UIUtils {
    private static final String ICON_PATH_PREFIX = "/org/example/majorgame/icons/";

    private UIUtils() {
    }

    /**
     * Converts an underscore_separated item ID into a human-readable Title Case name.
     */
    public static String formatItemName(String itemId) {
        String[] words = itemId.split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.toString();
    }

    /**
     * Loads an item icon from resources, using the provided cache to avoid reloading.
     * Returns null if the icon resource does not exist.
     */
    public static Image loadItemIcon(String itemId, Map<String, Image> iconCache) {
        return iconCache.computeIfAbsent(itemId, key -> {
            var iconResource = UIUtils.class.getResource(ICON_PATH_PREFIX + key + ".png");
            if (iconResource == null) return null;
            return new Image(iconResource.toExternalForm());
        });
    }
}
