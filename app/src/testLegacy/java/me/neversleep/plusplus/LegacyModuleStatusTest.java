package me.neversleep.plusplus;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.UserHandle;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyModuleStatusTest {
    public static class Android12Context {
        public void registerReceiverAsUser(BroadcastReceiver receiver, UserHandle user,
                                           IntentFilter filter, String permission, Handler handler) { }
    }
    public static class Android13Context extends Android12Context {
        public void registerReceiverAsUser(BroadcastReceiver receiver, UserHandle user,
                                           IntentFilter filter, String permission, Handler handler,
                                           int flags) { }
    }
    @Test public void android12UsesTheAvailableFiveArgumentPlatformEntry() throws Exception {
        assertEquals(5, LegacyModuleStatus.registrationMethod(Android12Context.class, 31).getParameterCount());
        assertEquals(5, LegacyModuleStatus.registrationMethod(Android12Context.class, 32).getParameterCount());
    }
    @Test public void android13AndLaterSelectTheExplicitExportFlagsEntry() throws Exception {
        assertEquals(6, LegacyModuleStatus.registrationMethod(Android13Context.class, 33).getParameterCount());
        assertEquals(6, LegacyModuleStatus.registrationMethod(Android13Context.class, 37).getParameterCount());
    }
}
