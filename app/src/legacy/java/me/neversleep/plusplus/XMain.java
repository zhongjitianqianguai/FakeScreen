package me.neversleep.plusplus;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class XMain implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) throws Throwable {
        if (BuildConfig.APPLICATION_ID.equals(param.packageName)) {
            Class<?> activity = Class.forName(MainActivity.class.getName(), false, param.classLoader);
            XposedBridge.hookMethod(activity.getDeclaredMethod("getActiveVersion"), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam result) {
                    result.setResult(BuildConfig.VERSION_CODE);
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
                    "com.android.server.policy.PhoneWindowManager", "powerPress", void.class)) {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam result) {
                        if (hooks.onPowerPress()) result.setResult(null);
                    }
                });
            }
        } catch (ReflectiveOperationException e) {
            XposedBridge.log(e);
        }
        try {
            for (Method method : HookImpl.findMethods(param.classLoader,
                    "com.android.server.power.PowerManagerService", "isBeingKeptAwakeLocked", boolean.class)) {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam result) {
                        if (!result.hasThrowable() && hooks.keepAwake()) result.setResult(true);
                    }
                });
            }
        } catch (ReflectiveOperationException e) {
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
