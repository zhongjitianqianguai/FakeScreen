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
        this(settings, logger, mode -> setDisplayPowerMode(loader, mode));
    }

    HookImpl(Settings settings, Logger logger, ScreenPowerController.Display display) {
        this.settings = settings;
        this.logger = logger;
        screen = new ScreenPowerController(display);
    }

    boolean onPowerPress() {
        try {
            if (settings.enabled("power")) return screen.toggle();
            screen.restore();
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.error("Display control failed; leaving the power key to Android", e);
        }
        return false;
    }

    boolean keepAwake() {
        return settings.enabled("disable_sleep");
    }

    boolean adjustTouchDimming(boolean requested) {
        return requested && !keepAwake();
    }

    void onConfigurationChanged() {
        try {
            if (!settings.enabled("power")) screen.restore();
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
    private static boolean setDisplayPowerMode(ClassLoader loader, int mode)
            throws ReflectiveOperationException {
        Class<?> surface = Class.forName("android.view.SurfaceControl", false, loader);
        IBinder token;
        if (Build.VERSION.SDK_INT < 29) {
            token = (IBinder) surface.getDeclaredMethod("getBuiltInDisplay", int.class)
                    .invoke(null, 0);
        } else if (Build.VERSION.SDK_INT < 34) {
            token = (IBinder) surface.getDeclaredMethod("getInternalDisplayToken").invoke(null);
        } else {
            Class<?> control = Class.forName("com.android.server.display.DisplayControl", false, loader);
            long[] ids = (long[]) control.getDeclaredMethod("getPhysicalDisplayIds").invoke(null);
            if (ids == null || ids.length == 0) return false;
            token = (IBinder) control.getDeclaredMethod("getPhysicalDisplayToken", long.class)
                    .invoke(null, ids[0]);
        }
        if (token == null) return false;
        surface.getDeclaredMethod("setDisplayPowerMode", IBinder.class, int.class)
                .invoke(null, token, mode);
        return true;
    }
}
