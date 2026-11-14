import com.dudeyy.scrolltrack.util.UpdateCheck;
import com.dudeyy.scrolltrack.util.UpdateCheck.UpdateInfo;

/**
 * JVM tests for the updater's pure logic: version.json parsing and the
 * newer-version comparison. The network, download and install steps are
 * Android-only and are covered by the manual checklist instead.
 * Run with run_tests.sh. Exits non-zero on the first failure.
 */
public class TestAppUpdater {

    private static int failures = 0;

    private static void check(String name, boolean cond) {
        if (!cond) {
            System.out.println("FAIL " + name);
            failures++;
        } else {
            System.out.println("ok   " + name);
        }
    }

    public static void main(String[] args) {
        // 1. A well-formed version file parses fully.
        UpdateInfo v = UpdateCheck.parseVersionJson(
                "{\"versionCode\": 17, \"versionName\": \"1.4.0\", "
                + "\"apkUrl\": \"https://example.com/ScrollTrack.apk\", "
                + "\"notes\": \"In-app updates are here.\"}");
        check("valid json parses", v != null);
        check("versionCode", v != null && v.versionCode == 17);
        check("versionName", v != null && "1.4.0".equals(v.versionName));
        check("apkUrl", v != null && "https://example.com/ScrollTrack.apk".equals(v.apkUrl));
        check("notes", v != null && "In-app updates are here.".equals(v.notes));

        // 2. Notes are optional.
        UpdateInfo v2 = UpdateCheck.parseVersionJson(
                "{\"versionCode\":18,\"versionName\":\"1.4.1\",\"apkUrl\":\"https://x/y.apk\"}");
        check("notes optional", v2 != null && "".equals(v2.notes));

        // 3. Whitespace, key order and extra fields do not matter.
        UpdateInfo v3 = UpdateCheck.parseVersionJson(
                "{\n  \"notes\" : \"hi\",\n  \"apkUrl\" : \"https://x/y.apk\",\n"
                + "  \"extra\": true,\n  \"versionName\" : \"1.5.0\",\n  \"versionCode\" : 20\n}");
        check("reordered + extra fields", v3 != null && v3.versionCode == 20
                && "hi".equals(v3.notes));

        // 4. Escapes inside strings decode.
        UpdateInfo v4 = UpdateCheck.parseVersionJson(
                "{\"versionCode\": 17, \"versionName\": \"1.4.0\", "
                + "\"apkUrl\": \"https://x/y.apk\", "
                + "\"notes\": \"Line one\\nLine \\\"two\\\"\"}");
        check("escapes decode", v4 != null
                && "Line one\nLine \"two\"".equals(v4.notes));

        // 5. Malformed input is rejected, never half-parsed.
        check("null json", UpdateCheck.parseVersionJson(null) == null);
        check("empty json", UpdateCheck.parseVersionJson("") == null);
        check("not json", UpdateCheck.parseVersionJson("hello") == null);
        check("missing versionCode",
                UpdateCheck.parseVersionJson(
                        "{\"versionName\":\"1.4.0\",\"apkUrl\":\"https://x/y.apk\"}") == null);
        check("missing apkUrl",
                UpdateCheck.parseVersionJson(
                        "{\"versionCode\":17,\"versionName\":\"1.4.0\"}") == null);
        check("versionCode zero",
                UpdateCheck.parseVersionJson(
                        "{\"versionCode\":0,\"versionName\":\"1.4.0\",\"apkUrl\":\"https://x/y.apk\"}") == null);
        check("versionCode not a number",
                UpdateCheck.parseVersionJson(
                        "{\"versionCode\":\"17\",\"versionName\":\"1.4.0\",\"apkUrl\":\"https://x/y.apk\"}") == null);
        check("unterminated string",
                UpdateCheck.parseVersionJson(
                        "{\"versionCode\":17,\"versionName\":\"1.4.0\",\"apkUrl\":\"https://x/y.apk}") == null);
        check("truncated json",
                UpdateCheck.parseVersionJson("{\"versionCode\": 17, \"versionName\"") == null);

        // 6. Newer-version comparison.
        check("17 > 16 is newer", UpdateCheck.isNewer(v, 16));
        check("17 > 17 is not newer", !UpdateCheck.isNewer(v, 17));
        check("17 > 18 is not newer", !UpdateCheck.isNewer(v, 18));
        check("null info is not newer", !UpdateCheck.isNewer(null, 16));

        if (failures > 0) {
            System.out.println("APP UPDATER TESTS FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("APP UPDATER TESTS PASSED");
    }
}
