package com.dudeyy.scrolltrack.util;

/**
 * Pure, JVM-testable core of the in-app updater: parsing version.json and
 * deciding whether it names a newer build. No Android imports — the
 * network, download and install steps live in {@link AppUpdater}.
 *
 * version.json format (written by whoever publishes a release):
 *   {"versionCode":17,"versionName":"1.4.0",
 *    "apkUrl":"https://...","notes":"..."}
 * Only versionCode, versionName and apkUrl are required; notes is optional.
 */
public final class UpdateCheck {
    private UpdateCheck() {}

    public static final class UpdateInfo {
        public final int versionCode;
        public final String versionName;
        public final String apkUrl;
        public final String notes;
        public UpdateInfo(int versionCode, String versionName, String apkUrl, String notes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.notes = notes != null ? notes : "";
        }
    }

    /** Parses version.json. Returns null when malformed or missing required fields. */
    public static UpdateInfo parseVersionJson(String json) {
        if (json == null) return null;
        Integer code = extractInt(json, "versionCode");
        String name = extractString(json, "versionName");
        String url = extractString(json, "apkUrl");
        if (code == null || code <= 0 || name == null || name.isEmpty()
                || url == null || url.isEmpty()) return null;
        String notes = extractString(json, "notes");
        return new UpdateInfo(code, name, url, notes != null ? notes : "");
    }

    public static boolean isNewer(UpdateInfo info, int currentCode) {
        return info != null && info.versionCode > currentCode;
    }

    /** Finds "key" : <digits> allowing whitespace. */
    static Integer extractInt(String json, String key) {
        int i = keyValueStart(json, key);
        if (i < 0) return null;
        int j = i;
        while (j < json.length() && Character.isDigit(json.charAt(j))) j++;
        if (j == i) return null;
        try { return Integer.parseInt(json.substring(i, j)); }
        catch (NumberFormatException e) { return null; }
    }

    /** Finds "key" : "..." allowing whitespace; decodes common escapes. */
    static String extractString(String json, String key) {
        int i = keyValueStart(json, key);
        if (i < 0 || i >= json.length() || json.charAt(i) != '"') return null;
        StringBuilder sb = new StringBuilder();
        int j = i + 1;
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c == '"') return sb.toString();
            if (c == '\\' && j + 1 < json.length()) {
                char e = json.charAt(j + 1);
                switch (e) {
                    case '"': sb.append('"'); j += 2; continue;
                    case '\\': sb.append('\\'); j += 2; continue;
                    case '/': sb.append('/'); j += 2; continue;
                    case 'n': sb.append('\n'); j += 2; continue;
                    case 'r': sb.append('\r'); j += 2; continue;
                    case 't': sb.append('\t'); j += 2; continue;
                    case 'u':
                        if (j + 5 < json.length()) {
                            try {
                                sb.append((char) Integer.parseInt(json.substring(j + 2, j + 6), 16));
                                j += 6;
                                continue;
                            } catch (NumberFormatException ignored) { }
                        }
                        return null;
                    default: return null;
                }
            }
            sb.append(c);
            j++;
        }
        return null; // unterminated string
    }

    /** Index just past `"key" :` (whitespace-tolerant), or -1. */
    private static int keyValueStart(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return -1;
        int i = k + key.length() + 2;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != ':') return -1;
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        return i < json.length() ? i : -1;
    }
}
