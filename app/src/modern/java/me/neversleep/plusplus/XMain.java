package me.neversleep.plusplus;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

public final class XMain extends XposedModule {
    private static final String TAG = "FakeScreen";
    private HookImpl hooks;
    // Remote preferences keep listeners weakly; retain this while the module is loaded.
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        SharedPreferences preferences = getRemotePreferences("x_conf");
        hooks = new HookImpl(param.getClassLoader(),
                key -> preferences.getBoolean(key, false),
                (message, error) -> log(Log.ERROR, TAG, message, error));
        preferenceListener = (prefs, key) -> hooks.onConfigurationChanged();
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        try {
            for (Method method : HookImpl.findMethods(param.getClassLoader(),
                    "com.android.server.policy.PhoneWindowManager", "powerPress", void.class)) {
                hook(method).intercept(chain -> hooks.onPowerPress() ? null : chain.proceed());
            }
            log(Log.INFO, TAG, "Power-key hooks installed (API " + getApiVersion() + ")");
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "Power-key hook unavailable on this ROM", e);
        }
        try {
            for (Method method : HookImpl.findMethods(param.getClassLoader(),
                    "com.android.server.power.PowerManagerService", "isBeingKeptAwakeLocked", boolean.class)) {
                hook(method).intercept(chain -> {
                    Object original = chain.proceed();
                    return hooks.keepAwake() ? true : original;
                });
            }
            log(Log.INFO, TAG, "Disable-sleep hooks installed");
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "Disable-sleep hook unavailable on this ROM", e);
        }
        try {
            Method method = HookImpl.findMethod(param.getClassLoader(),
                    "com.android.server.power.PowerManagerServiceImpl", "setTouchInteractive",
                    void.class, boolean.class);
            hook(method).intercept(chain -> chain.proceed(new Object[]{
                    hooks.adjustTouchDimming((boolean) chain.getArg(0))}));
            log(Log.INFO, TAG, "Touch-dimming hook installed");
        } catch (ReflectiveOperationException | RuntimeException e) {
            log(Log.INFO, TAG, "Touch-dimming hook unavailable on this ROM", e);
        }
    }
}
