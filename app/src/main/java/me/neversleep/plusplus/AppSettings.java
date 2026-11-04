package me.neversleep.plusplus;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

final class AppSettings {
    private static final Set<Runnable> listeners = new CopyOnWriteArraySet<>();

    static SharedPreferences local(Context context) {
        return ModuleConnection.openLocal(context);
    }

    static boolean enabled(Context context, String key) {
        return local(context).getBoolean(key, false);
    }

    static boolean set(Context context, String key, boolean value) {
        // Serialize the first user action with migration on the framework Binder thread.
        synchronized (SettingsMigration.class) {
            SharedPreferences preferences = local(context);
            if (SettingsMigration.hasLegacyConflict()) return false;
            if (!preferences.edit().putBoolean(key, value).commit()) return false;
        }
        settingsChanged(context);
        return true;
    }

    static void settingsChanged(Context context) {
        ModuleConnection.sync(context);
        notifyChanged();
        if (Build.VERSION.SDK_INT >= 24) {
            if (!requestTileRefresh(() -> TileService.requestListeningState(context,
                    new ComponentName(context, QuickStartService.class)))) {
                Log.w("FakeScreen", "Settings saved; tile refresh unavailable for this user");
            }
        }
    }

    static boolean requestTileRefresh(Runnable request) {
        try {
            request.run();
            return true;
        } catch (IllegalArgumentException e) {
            // Android 13 rejects non-current users here; Android 14+ treats them as a no-op.
            // The tile reads the persisted state when SystemUI next starts listening.
            return false;
        }
    }

    static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    static void notifyChanged() {
        new Handler(Looper.getMainLooper()).post(() -> {
            for (Runnable listener : listeners) listener.run();
        });
    }
}
