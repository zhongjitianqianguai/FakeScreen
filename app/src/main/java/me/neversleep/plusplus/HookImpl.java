package me.neversleep.plusplus;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.IBinder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Shared behavior; framework APIs live only in their own build flavor. */
final class HookImpl {
    interface Settings {
        boolean enabled(String key);
    }

    interface Logger {
        void error(String message, Throwable error);
    }

    private final Settings settings;
    private final Logger logger;
    private final ScreenPowerController screen;

    HookImpl(ClassLoader loader, Settings settings, Logger logger) {
        this(settings, logger, new ScreenPowerController.Display() {
            @Override
            public Object currentToken() throws ReflectiveOperationException {
                return currentDisplayToken(loader);
            }

            @Override
            public boolean setPowerMode(Object token, int mode) throws ReflectiveOperationException {
                return setDisplayPowerMode(loader, (IBinder) token, mode);
            }
        });
    }

    HookImpl(Settings settings, Logger logger, ScreenPowerController.Display display) {
        this.settings = settings;
        this.logger = logger;
        screen = new ScreenPowerController(display);
    }

    boolean onPowerPress() {
        try {
            return screen.toggleIfEnabled(() -> settings.enabled("power"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.error("Display control failed; leaving the power key to Android", e);
        }
        return false;
    }

    boolean onPowerPress(Object policy, Method method, Object[] arguments) {
        try {
            return PowerKeyPolicy.shouldHandle(policy, method, arguments) && onPowerPress();
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.error("Could not read power-key state; leaving this press to Android", e);
            return false;
        }
    }

    boolean beginDisplayChange(Object token) {
        return screen.beginDisplayChange(token);
    }

    void endDisplayChange(boolean succeeded) {
        screen.endDisplayChange(succeeded);
    }

    boolean keepAwake() {
        return settings.enabled("disable_sleep");
    }

    boolean adjustTouchDimming(boolean requested) {
        return requested && !keepAwake();
    }

    void onConfigurationChanged() {
        try {
            screen.restoreIfDisabled(() -> settings.enabled("power"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.error("Could not restore display after disabling FakeScreen", e);
        }
    }

    static List<Method> findMethods(ClassLoader loader, String className,
                                    String methodName, Class<?> returnType)
            throws ClassNotFoundException, NoSuchMethodException {
        Class<?> type = Class.forName(className, false, loader);
        List<Method> result = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getReturnType() == returnType) {
                result.add(method);
            }
        }
        if (result.isEmpty()) throw new NoSuchMethodException(className + "." + methodName);
        return result;
    }

    /** Known direct/transitive callers of the automatic-sleep predicates, across API 31+. */
    static List<Method> findSleepPolicyCallers(Class<?> powerService) {
        List<Method> result = new ArrayList<>();
        for (Method method : powerService.getDeclaredMethods()) {
            switch (method.getName()) {
                case "isItBedTimeYetLocked":
                case "updateWakefulnessLocked":
                case "handleSandman":
                case "onDreamSuppressionChangedLocked":
                case "updateAttentiveStateLocked":
                case "maybeHideInattentiveSleepWarningLocked":
                    result.add(method);
                    break;
                default:
                    break;
            }
        }
        return result;
    }

    static Method findMethod(ClassLoader loader, String className, String methodName,
                             Class<?> returnType, Class<?>... parameterTypes)
            throws ClassNotFoundException, NoSuchMethodException {
        Class<?> type = Class.forName(className, false, loader);
        Method method = type.getDeclaredMethod(methodName, parameterTypes);
        if (method.getReturnType() != returnType) {
            throw new NoSuchMethodException(className + "." + methodName);
        }
        return method;
    }

    // Called exclusively by hooks inside system_server, where platform hidden APIs are available.
    // These interfaces are the feature itself; the app process never invokes this method.
    @SuppressLint({"BlockedPrivateApi", "SoonBlockedPrivateApi"})
    private static IBinder currentDisplayToken(ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> control = Class.forName(Build.VERSION.SDK_INT < 34
                ? "android.view.SurfaceControl" : "com.android.server.display.DisplayControl", false, loader);
        // SurfaceFlinger's first ID can change when one of two lit panels is switched off.
        // Resolve the logical default display instead, so subsequent cycles still control
        // the panel containing the default display's content after native active-display changes.
        Class<?> globalType = Class.forName("android.hardware.display.DisplayManagerGlobal", false, loader);
        Object global = globalType.getMethod("getInstance").invoke(null);
        Object info = globalType.getMethod("getDisplayInfo", int.class).invoke(global, 0);
        if (info == null) return null;
        String uniqueId = (String) info.getClass().getField("uniqueId").get(info);
        if (uniqueId == null || !uniqueId.startsWith("local:")) return null;
        long physicalId = Long.parseLong(uniqueId.substring("local:".length()));
        return (IBinder) control.getDeclaredMethod("getPhysicalDisplayToken", long.class)
                .invoke(null, physicalId);
    }

    @SuppressLint({"BlockedPrivateApi", "SoonBlockedPrivateApi"})
    private static boolean setDisplayPowerMode(ClassLoader loader, IBinder token, int mode)
            throws ReflectiveOperationException {
        if (token == null) return false;
        Class<?> surface = Class.forName("android.view.SurfaceControl", false, loader);
        surface.getDeclaredMethod("setDisplayPowerMode", IBinder.class, int.class)
                .invoke(null, token, mode);
        return true;
    }
}
