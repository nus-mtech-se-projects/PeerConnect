package mtech.swe5006.peerconnect.api;

/**
 * Shared parsing/coercion helpers used by multiple controllers.
 * Kept package-accessible so callers can use {@code import static}.
 */
final class ControllerUtils {

    private ControllerUtils() {}

    static String asString(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    static Short asShort(Object value, Short defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.shortValue();
        try {
            return Short.parseShort(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    static boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return null;
    }
}
