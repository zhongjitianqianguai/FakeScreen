package me.neversleep.plusplus;

import android.content.Context;
import android.content.SharedPreferences;

final class ModuleConnection {
    static void initialize(Context context) { }

    @SuppressWarnings("deprecation")
    static SharedPreferences openLocal(Context context) {
        try {
            return context.getSharedPreferences("x_conf", Context.MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            // Settings remain available before LSPosed has activated the app-side hook.
            return context.getSharedPreferences("x_conf", Context.MODE_PRIVATE);
        }
    }

    static boolean sync(Context context) {
        return SettingsMigration.exportLegacy(context, openLocal(context));
    }

    static long activeVersion() {
        return MainActivity.getActiveVersion();
    }

    static boolean isPending() { return false; }
}
