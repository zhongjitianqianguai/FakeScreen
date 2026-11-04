package me.neversleep.plusplus;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

final class ModuleConnection {
    private static volatile boolean pending = true;

    static void initialize(Context context) {
        LegacyModuleStatus.refresh(context);
    }

    /** Self-hooked only to identify the legacy preference environment, not system activation. */
    static boolean frameworkConnected() { return false; }

    /** The self-hook returns the real redirected file when the framework is connected. */
    static File preferencesFile(Context context, SharedPreferences preferences) {
        return new File(context.getApplicationInfo().dataDir, "shared_prefs/x_conf.xml");
    }

    private static long modifiedAt(Context context, SharedPreferences preferences) {
        preferences.getAll(); // Wait for SharedPreferences' initial load / backup recovery.
        File file = preferencesFile(context, preferences);
        return file == null ? 0 : file.lastModified();
    }

    static SharedPreferences openLocal(Context context) {
        synchronized (SettingsMigration.class) {
            SharedPreferences preferences = rawLocal(context);
            if (!SettingsMigration.prepareLegacy(context, preferences, frameworkConnected(),
                    modifiedAt(context, preferences))) {
                pending = true;
            }
            return preferences;
        }
    }

    @SuppressWarnings("deprecation")
    private static SharedPreferences rawLocal(Context context) {
        try {
            return context.getSharedPreferences("x_conf", Context.MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            // Settings remain available before LSPosed has activated the app-side hook.
            return context.getSharedPreferences("x_conf", Context.MODE_PRIVATE);
        }
    }

    static boolean sync(Context context) {
        boolean saved;
        synchronized (SettingsMigration.class) {
            SharedPreferences preferences = rawLocal(context);
            boolean connected = frameworkConnected();
            saved = SettingsMigration.prepareLegacy(context, preferences, connected,
                    modifiedAt(context, preferences))
                    && SettingsMigration.exportLegacy(context, preferences, connected);
            pending = !saved || !connected;
        }
        LegacyModuleStatus.refresh(context);
        return saved;
    }

    static long activeVersion() {
        return LegacyModuleStatus.activeVersion();
    }

    static boolean isPending() { return pending; }
}
