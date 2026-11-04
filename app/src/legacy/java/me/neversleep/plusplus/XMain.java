package me.neversleep.plusplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.IBinder;

import java.lang.reflect.Method;
import java.lang.reflect.Member;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class XMain implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) throws Throwable {
        if (BuildConfig.APPLICATION_ID.equals(param.packageName)) {
            Class<?> connection = Class.forName(ModuleConnection.class.getName(), false, param.classLoader);
            XposedBridge.hookMethod(connection.getDeclaredMethod("frameworkConnected"), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam result) {
                    result.setResult(true);
                }
            });
            XposedBridge.hookMethod(connection.getDeclaredMethod("preferencesFile",
                    Context.class, SharedPreferences.class), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam result) {
                    try {
                        result.setResult((File) XposedHelpers.getObjectField(result.args[1], "mFile"));
                    } catch (RuntimeException | NoSuchFieldError e) {
                        XposedBridge.log(e);
                        // Unknown ordering must be resolved by the user, not guessed from
                        // the ordinary private path while preferences are redirected.
                        result.setResult(null);
                    }
                }
            });
        }
        if (!"android".equals(param.packageName) || !"android".equals(param.processName)) return;
        XSharedPreferences preferences = new XSharedPreferences(BuildConfig.APPLICATION_ID, "x_conf");
        HookImpl hooks = new HookImpl(param.classLoader, key -> {
            preferences.reload();
            return preferences.getBoolean(key, false);
        }, (message, error) -> {
            XposedBridge.log("FakeScreen: " + message);
            XposedBridge.log(error);
        });
        try {
            for (Method method : HookImpl.findMethods(param.classLoader,
                    "com.android.server.policy.PhoneWindowManager", "init", void.class)) {
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 0 || types[0] != Context.class) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam result) {
                        if (!result.hasThrowable()) LegacyModuleStatus.register((Context) result.args[0]);
                    }
                });
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            XposedBridge.log(e);
        }
        try {
            Method method = HookImpl.findMethod(param.classLoader,
                    "android.view.SurfaceControl", "setDisplayPowerMode", void.class,
                    IBinder.class, int.class);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                private final String lockKey = BuildConfig.APPLICATION_ID + ".displayLock";

                @Override
                protected void beforeHookedMethod(MethodHookParam result) {
                    result.setObjectExtra(lockKey, hooks.beginDisplayChange(result.args[0]));
                }

                @Override
                protected void afterHookedMethod(MethodHookParam result) {
                    if (Boolean.TRUE.equals(result.getObjectExtra(lockKey))) {
                        hooks.endDisplayChange(!result.hasThrowable());
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException e) {
            XposedBridge.log(e);
        }
        try {
            for (Method method : HookImpl.findMethods(param.classLoader,
                    "com.android.server.policy.PhoneWindowManager", "powerPress", void.class)) {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam result) {
                        if (hooks.onPowerPress(result.thisObject, method, result.args)) result.setResult(null);
                    }
                });
            }
        } catch (ReflectiveOperationException e) {
            XposedBridge.log(e);
        }
        for (String methodName : new String[]{
                "isBeingKeptAwakeLocked", "isBeingKeptFromInattentiveSleepLocked"}) {
            try {
                for (Method method : HookImpl.findMethods(param.classLoader,
                        "com.android.server.power.PowerManagerService", methodName, boolean.class)) {
                    AtomicBoolean traced = new AtomicBoolean();
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam result) {
                            if (!result.hasThrowable() && hooks.keepAwake()) {
                                if (BuildConfig.DEBUG && Boolean.FALSE.equals(result.getResult())
                                        && traced.compareAndSet(false, true)) {
                                    XposedBridge.log("FakeScreen: Disable-sleep enforced: " + methodName);
                                }
                                result.setResult(true);
                            }
                        }
                    });
                }
                XposedBridge.log("FakeScreen: Disable-sleep hook installed: " + methodName);
            } catch (ReflectiveOperationException | RuntimeException e) {
                XposedBridge.log("FakeScreen: Disable-sleep hook unavailable on this ROM: "
                        + methodName + ": " + e);
                XposedBridge.log(e);
            }
        }
        try {
            Class<?> powerService = Class.forName("com.android.server.power.PowerManagerService",
                    false, param.classLoader);
            List<Method> callers = HookImpl.findSleepPolicyCallers(powerService);
            // LSPosed extends the legacy bridge; API 82 compile stubs intentionally omit it.
            Method deoptimize = XposedBridge.class.getMethod("deoptimizeMethod", Member.class);
            AtomicBoolean deoptimized = new AtomicBoolean();
            for (Method ready : HookImpl.findMethods(param.classLoader,
                    powerService.getName(), "systemReady", void.class)) {
                XposedBridge.hookMethod(ready, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam result) {
                        if (result.hasThrowable() || !deoptimized.compareAndSet(false, true)) return;
                        for (Method caller : callers) {
                            try {
                                deoptimize.invoke(null, caller);
                                XposedBridge.log("FakeScreen: Sleep-policy caller deopt requested: " + caller.getName());
                            } catch (ReflectiveOperationException | RuntimeException e) {
                                XposedBridge.log("FakeScreen: Sleep-policy caller deopt failed: " + caller.getName());
                                XposedBridge.log(e);
                            }
                        }
                        if (callers.isEmpty()) XposedBridge.log("FakeScreen: No automatic-sleep callers found");
                    }
                });
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            XposedBridge.log("FakeScreen: Automatic-sleep caller deoptimization unavailable");
            XposedBridge.log(e);
        }
        try {
            Method method = HookImpl.findMethod(param.classLoader,
                    "com.android.server.power.PowerManagerServiceImpl", "setTouchInteractive",
                    void.class, boolean.class);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam result) {
                    result.args[0] = hooks.adjustTouchDimming((boolean) result.args[0]);
                }
            });
        } catch (ReflectiveOperationException | RuntimeException e) {
            XposedBridge.log("FakeScreen: Touch-dimming hook unavailable on this ROM: " + e);
        }
    }
}
