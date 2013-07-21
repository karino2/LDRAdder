package org.jarx.android.livedoor.reader;

import java.text.DateFormat;
import java.util.Date;

public class Utils {

    private Utils() {
    }

    public static int asInt(Object value) {
        return asInt(value, 0);
    }

    public static int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long asLong(Object value) {
        return asLong(value, 0);
    }

    public static long asLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static String asString(Object value) {
        return asString(value, null);
    }

    public static String asString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return String.valueOf(value).trim();
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static String stripWhitespaces(String value) {
        if (value == null || value.length() == 0) {
            return value;
        }
        return value.replaceAll("[\\s\u3000]+", " ").trim();
    }

    public static String htmlAsPlainText(String value) {
        if (value == null || value.length() == 0) {
            return value;
        }
        value = value.replaceAll("\\s+", " ");
        value = value.replaceAll("<br\\s?/?>", "\n");
        value = value.replaceAll("<.*?>", " ");

        // NOTE: some html entities
        value = value.replaceAll("&lt;", "<");
        value = value.replaceAll("&gt;", ">");
        value = value.replaceAll("&quot;", "\"");
        value = value.replaceAll("&apos;", "\'");
        value = value.replaceAll("&nbsp;", " ");
        value = value.replaceAll("&amp;", "&");

        value = value.replaceAll("  +", " ");
        return value;
    }

    public static String htmlEscape(String value) {
        if (value == null || value.length() == 0) {
            return value;
        }
        value = value.replaceAll("&", "&amp;");
        value = value.replaceAll("<", "&lt;");
        value = value.replaceAll(">", "&gt;");
        value = value.replaceAll("\"", "&quot;");
        return value;
    }

    public static String formatTimeAgo(long time) {
        long diff = (System.currentTimeMillis() / 1000) - time;
        if (diff < (7 * 24 * 60 * 60)) {
            if (diff < (60 * 60)) {
                return (diff / 60) + " min ago";
            } else if (diff < (24 * 60 * 60)) {
                return (diff / 60 / 60) + " hours ago";
            } else {
                return (diff / 24 / 60 / 60) + " days ago";
            }
        }
        return DateFormat.getDateInstance().format(new Date(time * 1000));
    }
}
