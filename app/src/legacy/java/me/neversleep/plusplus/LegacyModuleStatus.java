package me.neversleep.plusplus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.UserHandle;
import android.util.Log;

import java.lang.reflect.Method;

/** A live response from the system process; never persists an activation claim across boots. */
final class LegacyModuleStatus {
    private static final String ACTION = BuildConfig.APPLICATION_ID + ".QUERY_ACTIVE_VERSION";
    private static BroadcastReceiver systemReceiver;
    private static volatile long version;
    private static boolean querying;

    static synchronized void register(Context systemContext) {
        if (systemReceiver != null) return;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION.equals(intent.getAction()) && isOrderedBroadcast()) {
                    setResultCode(BuildConfig.VERSION_CODE);
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION);
        try {
            // This method is called only by the system_server hook, which already holds
            // INTERACT_ACROSS_USERS_FULL. App processes only send the read-only query.
            UserHandle allUsers = (UserHandle) UserHandle.class.getField("ALL").get(null);
            Method method = registrationMethod(Context.class, Build.VERSION.SDK_INT);
            if (Build.VERSION.SDK_INT >= 33) {
                method.invoke(systemContext, receiver, allUsers, filter, null, null,
                        Context.RECEIVER_EXPORTED);
            } else {
                method.invoke(systemContext, receiver, allUsers, filter, null, null);
            }
            systemReceiver = receiver;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.e("FakeScreen", "Could not register system module query for all users", e);
        }
    }

    static Method registrationMethod(Class<?> contextType, int sdk) throws NoSuchMethodException {
        if (sdk >= 33) {
            return contextType.getMethod("registerReceiverAsUser", BroadcastReceiver.class,
                    UserHandle.class, IntentFilter.class, String.class, Handler.class, int.class);
        }
        return contextType.getMethod("registerReceiverAsUser", BroadcastReceiver.class,
                UserHandle.class, IntentFilter.class, String.class, Handler.class);
    }

    static synchronized void refresh(Context context) {
        if (querying) return;
        querying = true;
        try {
            // Only receivers owned by Android may answer; third-party apps cannot spoof it.
            context.sendOrderedBroadcast(new Intent(ACTION).setPackage("android"), null,
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            synchronized (LegacyModuleStatus.class) {
                                version = getResultCode();
                                querying = false;
                            }
                            AppSettings.notifyChanged();
                        }
                    }, null, 0, null, null);
        } catch (RuntimeException e) {
            version = 0;
            querying = false;
            Log.e("FakeScreen", "Could not query legacy system module", e);
        }
    }

    static long activeVersion() { return version; }
}
