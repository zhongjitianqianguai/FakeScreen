package me.neversleep.plusplus;

import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Decodes the platform's old and new powerPress contracts without swallowing wake/multi-press. */
final class PowerKeyPolicy {
    /** Debug-only caller diagnostics; never includes key text or arbitrary argument contents. */
    static String describe(Object policy, Method method, Object[] args) {
        StringBuilder result = new StringBuilder(method.toGenericString()).append(" args=[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) result.append(',');
            Object value = args[i];
            result.append(value == null || value instanceof Number || value instanceof Boolean
                    ? String.valueOf(value) : value.getClass().getSimpleName());
        }
        result.append("] sdk=").append(Build.VERSION.SDK_INT);
        try {
            Object display = field(policy, "mDefaultDisplayPolicy");
            result.append(" awake=").append(call(display, "isAwake"))
                    .append(" early=").append(call(display, "isScreenOnEarly"))
                    .append(" fully=").append(call(display, "isScreenOnFully"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            result.append(" displayPolicy=").append(e.getClass().getSimpleName());
        }
        try {
            result.append(" beganNonInteractive=").append(call(
                    field(policy, "mSingleKeyGestureDetector"), "beganFromNonInteractive"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            result.append(" detector=").append(e.getClass().getSimpleName());
        }
        try {
            result.append(" displayState=").append(call(field(policy, "mDefaultDisplay"), "getState"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            result.append(" displayState=").append(e.getClass().getSimpleName());
        }
        return result.toString();
    }

    static boolean shouldHandle(Object policy, Method method, Object[] args)
            throws ReflectiveOperationException {
        return shouldHandle(policy, method, args, Build.VERSION.SDK_INT);
    }

    static boolean shouldHandle(Object policy, Method method, Object[] args, int sdk)
            throws ReflectiveOperationException {
        Class<?>[] types = method.getParameterTypes();
        if (types.length < 3 || types[0] != long.class) {
            throw new NoSuchMethodException("Unsupported powerPress signature: " + method);
        }
        boolean interactive;
        boolean beganFromNonInteractive;
        if (types[1] == boolean.class && types[2] == int.class) {
            // Android <= 11: powerPress(long, boolean interactive, int count).
            if ((int) args[2] != 1) return false;
            interactive = (boolean) args[1];
            if (!interactive) return false;
            beganFromNonInteractive = (boolean) field(policy, "mBeganFromNonInteractive");
        } else if (types[1] == int.class && (types[2] == boolean.class || types[2] == int.class)) {
            if ((int) args[1] != 1) return false;
            // Android 12 passes the latch; later versions replace it with displayId.
            beganFromNonInteractive = types[2] == boolean.class ? (boolean) args[2]
                    : (boolean) call(field(policy, "mSingleKeyGestureDetector"),
                            "beganFromNonInteractive");
            if (beganFromNonInteractive) return false;
            if (types[2] == boolean.class && sdk < 34) {
                // Android 12/13's Display.isOnState: ON, VR or ON_SUSPEND.
                int state = (int) call(field(policy, "mDefaultDisplay"), "getState");
                interactive = state == 2 || state == 5 || state == 6;
            } else {
                // Android 14 retains the boolean parameter but already uses policy wakefulness.
                interactive = (boolean) call(field(policy, "mDefaultDisplayPolicy"), "isAwake");
            }
        } else {
            throw new NoSuchMethodException("Unsupported powerPress signature: " + method);
        }
        if (!interactive || beganFromNonInteractive) return false;

        boolean screenOnEarly;
        boolean screenOnFully;
        try {
            Object displayPolicy = field(policy, "mDefaultDisplayPolicy");
            screenOnEarly = (boolean) call(displayPolicy, "isScreenOnEarly");
            screenOnFully = (boolean) call(displayPolicy, "isScreenOnFully");
        } catch (NoSuchFieldException e) {
            // Before DisplayPolicy was split out of PhoneWindowManager.
            screenOnEarly = (boolean) field(policy, "mScreenOnEarly");
            screenOnFully = (boolean) field(policy, "mScreenOnFully");
        }
        return !screenOnEarly || screenOnFully;
    }

    private static Object field(Object instance, String name) throws ReflectiveOperationException {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException e) {
                // ROMs may move the same state into a base policy class.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object call(Object instance, String name) throws ReflectiveOperationException {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(instance);
            } catch (NoSuchMethodException e) {
                // See field(): preserve inherited implementations on ROM variants.
            }
        }
        throw new NoSuchMethodException(name);
    }
}
