package me.neversleep.plusplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

/** Bridges the legacy framework's redirected preferences through ordinary private app files. */
final class SettingsMigration {
    private static final String SNAPSHOT = "x_conf-legacy.properties";
    private static final String IMPORTED = "legacy_snapshot_imported";
    private static final String[] KEYS = {"power", "disable_sleep"};

    static synchronized boolean exportLegacy(Context context, SharedPreferences source) {
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), SNAPSHOT));
        FileOutputStream output = null;
        try {
            Properties properties = new Properties();
            properties.setProperty("id", UUID.randomUUID().toString());
            for (String key : KEYS) properties.setProperty(key,
                    Boolean.toString(source.getBoolean(key, false)));
            output = file.startWrite();
            properties.store(output, "FakeScreen legacy settings migration");
            file.finishWrite(output);
            return true;
        } catch (IOException e) {
            if (output != null) file.failWrite(output);
            Log.e("FakeScreen", "Could not save legacy migration snapshot", e);
            return false;
        }
    }

    static synchronized boolean importLegacy(Context context, SharedPreferences destination) {
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), SNAPSHOT));
        if (!file.getBaseFile().exists()
                && !new File(context.getFilesDir(), SNAPSHOT + ".bak").exists()) return true;
        try (FileInputStream input = file.openRead()) {
            Properties properties = new Properties();
            properties.load(input);
            String id = properties.getProperty("id");
            if (id == null) throw new IOException("Missing snapshot identity");
            if (id.equals(destination.getString(IMPORTED, null))) return true;
            SharedPreferences.Editor editor = destination.edit();
            for (String key : KEYS) {
                String value = properties.getProperty(key);
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new IOException("Invalid snapshot value for " + key);
                }
                editor.putBoolean(key, Boolean.parseBoolean(value));
            }
            return editor.putString(IMPORTED, id).commit();
        } catch (IOException e) {
            Log.e("FakeScreen", "Legacy snapshot import failed; keeping existing settings", e);
            return false;
        }
    }

    static synchronized boolean importMissingRemote(SharedPreferences local, SharedPreferences remote) {
        SharedPreferences.Editor editor = local.edit();
        for (String key : KEYS) {
            if (!local.contains(key) && remote.contains(key)) {
                editor.putBoolean(key, remote.getBoolean(key, false));
            }
        }
        return editor.commit();
    }
}
