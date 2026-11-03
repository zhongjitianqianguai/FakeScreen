package me.neversleep.plusplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;
import android.util.Log;

import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

final class ModuleConnection {
    private static volatile XposedService service;
    private static volatile boolean pending = true;

    static void initialize(Context context) {
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService connected) {
                service = connected;
                // The provider can bind before unlock; don't open credential storage then.
                if (context.getSystemService(UserManager.class).isUserUnlocked()) sync(context);
                AppSettings.notifyChanged();
            }

            @Override
            public void onServiceDied(XposedService disconnected) {
                if (service == disconnected) {
                    service = null;
                    pending = true;
                    AppSettings.notifyChanged();
                }
            }
        });
    }

    static SharedPreferences openLocal(Context context) {
        // Keep the old file and keys for in-place upgrades from legacy builds.
        SharedPreferences preferences = context.getSharedPreferences("x_conf", Context.MODE_PRIVATE);
        // Import before the first UI/Shell/tile read or write, so the snapshot cannot overwrite
        // the first action made after upgrading.
        SettingsMigration.importLegacy(context, preferences);
        return preferences;
    }

    static synchronized boolean sync(Context context) {
        XposedService connected = service;
        pending = true;
        try {
            SharedPreferences source = openLocal(context);
            if (!SettingsMigration.importLegacy(context, source)) return false;
            if (connected == null) return false;
            SharedPreferences remote = connected.getRemotePreferences("x_conf");
            if (!SettingsMigration.importMissingRemote(source, remote)) return false;
            pending = !remote.edit()
                    .putBoolean("power", source.getBoolean("power", false))
                    .putBoolean("disable_sleep", source.getBoolean("disable_sleep", false)).commit();
            return !pending;
        } catch (RuntimeException e) {
            Log.e("FakeScreen", "Settings saved locally; framework sync failed", e);
            return false;
        }
    }

    static long activeVersion() {
        XposedService connected = service;
        if (connected == null) return 0;
        try {
            for (HookedTarget target : connected.getRunningTargets()) {
                if (BuildConfig.DEBUG) {
                    Log.d("FakeScreen", "Hooked target: " + target.getProcessName()
                            + ", uid=" + target.getUid() + ", version=" + target.getLoadedVersionCode());
                }
                if (target.getUid() == 1000 && ("system".equals(target.getProcessName())
                        || "system_server".equals(target.getProcessName())
                        || "android".equals(target.getProcessName()))) {
                    return target.getLoadedVersionCode();
                }
            }
        } catch (RuntimeException e) {
            Log.e("FakeScreen", "Could not query system_server module version", e);
        }
        return 0;
    }

    static boolean isPending() { return pending; }
}
