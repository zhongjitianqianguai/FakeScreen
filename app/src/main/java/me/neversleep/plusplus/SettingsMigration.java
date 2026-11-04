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
    private static boolean legacyConflict;

    static final class SnapshotConflict extends IOException {
        SnapshotConflict() { super("Legacy snapshot and current settings have no reliable ordering"); }
    }

    static synchronized boolean exportLegacy(Context context, SharedPreferences source,
                                             boolean frameworkConnected) {
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), SNAPSHOT));
        FileOutputStream output = null;
        try {
            Properties properties = snapshot(source, frameworkConnected);
            output = file.startWrite();
            properties.store(output, "FakeScreen legacy settings migration");
            file.finishWrite(output);
            // A later local edit must not be overwritten by our own previous snapshot.
            return source.edit().putString(IMPORTED, properties.getProperty("id")).commit();
        } catch (IOException e) {
            if (output != null) file.failWrite(output);
            Log.e("FakeScreen", "Could not save legacy migration snapshot", e);
            return false;
        }
    }

    static synchronized boolean importLegacy(Context context, SharedPreferences destination) {
        return importSnapshot(context, destination, false, false, 0);
    }

    static synchronized boolean prepareLegacy(Context context, SharedPreferences destination,
                                               boolean frameworkConnected, long destinationModifiedAt) {
        return importSnapshot(context, destination, frameworkConnected, true, destinationModifiedAt);
    }

    private static boolean importSnapshot(Context context, SharedPreferences destination,
                                          boolean preserveFrameworkValues, boolean legacy,
                                          long destinationModifiedAt) {
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), SNAPSHOT));
        if (!file.getBaseFile().exists()
                && !new File(context.getFilesDir(), SNAPSHOT + ".bak").exists()) {
            legacyConflict = false;
            return true;
        }
        try (FileInputStream input = file.openRead()) {
            Properties properties = new Properties();
            properties.load(input);
            boolean imported = legacy
                    ? applyLegacySnapshot(properties, destination, preserveFrameworkValues,
                            file.getBaseFile().lastModified(), destinationModifiedAt)
                    : applySnapshot(properties, destination, preserveFrameworkValues);
            if (imported) legacyConflict = false;
            return imported;
        } catch (SnapshotConflict e) {
            legacyConflict = true;
            return false;
        } catch (IOException e) {
            Log.e("FakeScreen", "Legacy snapshot import failed; keeping existing settings", e);
            return false;
        }
    }

    static synchronized boolean hasLegacyConflict() { return legacyConflict; }

    static synchronized boolean[] legacySnapshotValues(Context context) {
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), SNAPSHOT));
        try (FileInputStream input = file.openRead()) {
            Properties properties = new Properties();
            properties.load(input);
            return new boolean[]{Boolean.parseBoolean(properties.getProperty(KEYS[0])),
                    Boolean.parseBoolean(properties.getProperty(KEYS[1]))};
        } catch (IOException e) {
            Log.e("FakeScreen", "Could not read legacy settings for selection", e);
            return null;
        }
    }

    static synchronized boolean resolveLegacyConflict(Context context, SharedPreferences destination,
                                                      boolean useSnapshot) {
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), SNAPSHOT));
        try (FileInputStream input = file.openRead()) {
            Properties properties = new Properties();
            properties.load(input);
            boolean saved = resolveSnapshot(properties, destination, useSnapshot);
            if (saved) legacyConflict = false;
            return saved;
        } catch (IOException e) {
            Log.e("FakeScreen", "Could not resolve legacy settings", e);
            return false;
        }
    }

    static boolean resolveSnapshot(Properties properties, SharedPreferences destination,
                                    boolean useSnapshot) throws IOException {
        if (useSnapshot) return applySnapshot(properties, destination, false);
        String id = properties.getProperty("id");
        if (id == null) throw new IOException("Missing snapshot identity");
        // A missing key is also part of the user's current choice (default false).
        // Confirming current settings must not fill it from the rejected snapshot.
        return destination.edit().putString(IMPORTED, id).commit();
    }

    static boolean applyLegacySnapshot(Properties properties, SharedPreferences destination,
                                        boolean frameworkConnected, long snapshotModifiedAt,
                                        long destinationModifiedAt) throws IOException {
        if (!properties.containsKey("source")
                && !properties.getProperty("id", "").equals(destination.getString(IMPORTED, null))) {
            boolean hasCurrentSettings = destination.contains(KEYS[0]) || destination.contains(KEYS[1]);
            boolean different = false;
            for (String key : KEYS) {
                String value = properties.getProperty(key);
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new IOException("Invalid snapshot value for " + key);
                }
                different |= destination.getBoolean(key, false) != Boolean.parseBoolean(value);
            }
            if (!hasCurrentSettings) return applySnapshot(properties, destination, false);
            // Old 1.8.2 snapshots have no source marker. Use actual file times, not
            // assumptions about which preference directory the user last edited.
            if (snapshotModifiedAt > 0 && destinationModifiedAt > 0
                    && snapshotModifiedAt != destinationModifiedAt) {
                return resolveSnapshot(properties, destination, snapshotModifiedAt > destinationModifiedAt);
            }
            if (different) throw new SnapshotConflict();
            return resolveSnapshot(properties, destination, false);
        }
        return applySnapshot(properties, destination, frameworkConnected);
    }

    static Properties snapshot(SharedPreferences source, boolean frameworkConnected) {
        Properties properties = new Properties();
        properties.setProperty("id", UUID.randomUUID().toString());
        properties.setProperty("source", frameworkConnected ? "framework" : "local");
        for (String key : KEYS) properties.setProperty(key,
                Boolean.toString(source.getBoolean(key, false)));
        return properties;
    }

    static boolean applySnapshot(Properties properties, SharedPreferences destination,
                                 boolean preserveFrameworkValues) throws IOException {
        String id = properties.getProperty("id");
        if (id == null) throw new IOException("Missing snapshot identity");
        if (id.equals(destination.getString(IMPORTED, null))) return true;
        // Offline user changes win on reconnection. Old/framework snapshots only fill gaps
        // in an already populated framework directory, preserving existing user settings.
        boolean onlyMissing = preserveFrameworkValues
                && !"local".equals(properties.getProperty("source"));
        SharedPreferences.Editor editor = destination.edit();
        for (String key : KEYS) {
            String value = properties.getProperty(key);
            if (!"true".equals(value) && !"false".equals(value)) {
                throw new IOException("Invalid snapshot value for " + key);
            }
            if (!onlyMissing || !destination.contains(key)) {
                editor.putBoolean(key, Boolean.parseBoolean(value));
            }
        }
        return editor.putString(IMPORTED, id).commit();
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
