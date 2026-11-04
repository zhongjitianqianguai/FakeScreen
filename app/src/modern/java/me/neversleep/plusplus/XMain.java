package me.neversleep.plusplus;

import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
            Method method = HookImpl.findMethod(param.getClassLoader(),
                    "android.view.SurfaceControl", "setDisplayPowerMode", void.class,
                    IBinder.class, int.class);
            hook(method).intercept(chain -> {
                boolean tracked = hooks.beginDisplayChange(chain.getArg(0));
                boolean succeeded = false;
                try {
                    Object result = chain.proceed();
                    succeeded = true;
                    return result;
                } finally {
                    if (tracked) hooks.endDisplayChange(succeeded);
                }
            });
        } catch (ReflectiveOperationException | RuntimeException e) {
            log(Log.ERROR, TAG, "Display-state tracking unavailable on this ROM", e);
        }
        try {
            for (Method method : HookImpl.findMethods(param.getClassLoader(),
                    "com.android.server.policy.PhoneWindowManager", "powerPress", void.class)) {
                hook(method).intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    if (BuildConfig.DEBUG) {
                        log(Log.INFO, TAG, "Power-key trace: "
                                + PowerKeyPolicy.describe(chain.getThisObject(), method, args)
                                + " power=" + preferences.getBoolean("power", false));
                    }
                    boolean consumed = hooks.onPowerPress(chain.getThisObject(), method, args);
                    if (BuildConfig.DEBUG) log(Log.INFO, TAG, "Power-key consumed=" + consumed);
                    return consumed ? null : chain.proceed();
                });
            }
            log(Log.INFO, TAG, "Power-key hooks installed (API " + getApiVersion() + ")");
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "Power-key hook unavailable on this ROM", e);
        }
        for (String methodName : new String[]{
                "isBeingKeptAwakeLocked", "isBeingKeptFromInattentiveSleepLocked"}) {
            try {
                for (Method method : HookImpl.findMethods(param.getClassLoader(),
                        "com.android.server.power.PowerManagerService", methodName, boolean.class)) {
                    AtomicBoolean traced = new AtomicBoolean();
                    hook(method).intercept(chain -> {
                        Object original = chain.proceed();
                        boolean keepAwake = hooks.keepAwake();
                        if (BuildConfig.DEBUG && keepAwake && Boolean.FALSE.equals(original)
                                && traced.compareAndSet(false, true)) {
                            log(Log.INFO, TAG, "Disable-sleep enforced: " + methodName);
                        }
                        return keepAwake ? true : original;
                    });
                }
                log(Log.INFO, TAG, "Disable-sleep hook installed: " + methodName);
            } catch (ReflectiveOperationException | RuntimeException e) {
                log(Log.ERROR, TAG, "Disable-sleep hook unavailable on this ROM: " + methodName, e);
            }
        }
        try {
            Class<?> powerService = Class.forName("com.android.server.power.PowerManagerService",
                    false, param.getClassLoader());
            List<Method> callers = HookImpl.findSleepPolicyCallers(powerService);
            AtomicBoolean deoptimized = new AtomicBoolean();
            // Wait until the class has completed initialization; keep the original ready path.
            for (Method ready : HookImpl.findMethods(param.getClassLoader(),
                    powerService.getName(), "systemReady", void.class)) {
                hook(ready).intercept(chain -> {
                    Object result = chain.proceed();
                    if (deoptimized.compareAndSet(false, true)) {
                        for (Method caller : callers) {
                            try {
                                boolean success = deoptimize(caller);
                                log(success ? Log.INFO : Log.ERROR, TAG,
                                        "Sleep-policy caller deopt=" + success + ": " + caller.getName());
                            } catch (RuntimeException e) {
                                log(Log.ERROR, TAG, "Sleep-policy caller deopt failed: " + caller.getName(), e);
                            }
                        }
                        if (callers.isEmpty()) log(Log.ERROR, TAG, "No automatic-sleep callers found");
                    }
                    return result;
                });
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log(Log.ERROR, TAG, "Automatic-sleep caller deoptimization unavailable", e);
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
