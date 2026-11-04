package me.neversleep.plusplus;

import org.junit.Test;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class SleepPolicyDiscoveryTest {
    static class Android12Policy {
        private boolean isItBedTimeYetLocked(int groupId) { return true; }
        private boolean updateWakefulnessLocked(int dirty) { return true; }
        private void handleSandman(int groupId) { }
        private void goToSleepInternal() { }
        private void reboot() { }
    }

    static class NewPolicy {
        private boolean isItBedTimeYetLocked(Object group) { return true; }
        private boolean updateWakefulnessLocked(int dirty) { return true; }
        private void handleSandman(int groupId) { }
        private void updateAttentiveStateLocked(long now, int dirty) { }
        private boolean maybeHideInattentiveSleepWarningLocked(long now, long warning) { return false; }
        private void onDreamSuppressionChangedLocked(boolean suppressed) { }
        private void shutdown() { }
    }

    @Test public void olderSignaturesAreFoundWithoutSelectingExplicitSleepOrReboot() {
        Set<String> names = names(Android12Policy.class);
        assertEquals(Set.of("isItBedTimeYetLocked", "updateWakefulnessLocked", "handleSandman"), names);
    }

    @Test public void newerWarningAndDreamCallersAreIncludedRegardlessOfReturnType() {
        Set<String> names = names(NewPolicy.class);
        assertEquals(Set.of("isItBedTimeYetLocked", "updateWakefulnessLocked", "handleSandman",
                "updateAttentiveStateLocked", "maybeHideInattentiveSleepWarningLocked",
                "onDreamSuppressionChangedLocked"), names);
    }

    private static Set<String> names(Class<?> type) {
        Set<String> result = new HashSet<>();
        for (Method method : HookImpl.findSleepPolicyCallers(type)) result.add(method.getName());
        return result;
    }
}
