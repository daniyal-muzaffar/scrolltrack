package com.dudeyy.scrolltrack.data.repo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Minimal read-only provider that serves files from the cache dir — but only
 * the two names the app itself writes: the exported CSV and a downloaded
 * update APK (which the system installer reads). Anything else is refused.
 */
public class CsvProvider extends ContentProvider {
    @Override
    public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        boolean allowed = "scrolltrack-export.csv".equals(name)
                || (name != null && name.startsWith("scrolltrack-update-")
                        && name.endsWith(".apk"));
        File file = name == null ? null : new File(getContext().getCacheDir(), name);
        if (!allowed || file == null || !file.exists()) {
            throw new FileNotFoundException(uri.toString());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] sa, String so) { return null; }
    @Override public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null && name.endsWith(".apk")) {
            return "application/vnd.android.package-archive";
        }
        return "text/csv";
    }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] sa) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] sa) { return 0; }
}
