package me.neversleep.plusplus;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

public class HookMethodDiscoveryTest {
    public static class RomPolicy {
        private void powerPress(long time, boolean interactive, int count) { }
        private void powerPress(long time, int count, int displayId) { }
        private int powerPress() { return 0; }
    }

    @Test
    public void matchesOldAndNewRomSignaturesWithExpectedReturnType() throws Exception {
        List<Method> methods = HookImpl.findMethods(getClass().getClassLoader(),
                RomPolicy.class.getName(), "powerPress", void.class);
        assertEquals(2, methods.size());
        for (Method method : methods) assertEquals(void.class, method.getReturnType());
    }

    @Test
    public void absentMethodIsReportedRatherThanSilentlyClaimedInstalled() {
        assertThrows(NoSuchMethodException.class, () -> HookImpl.findMethods(
                getClass().getClassLoader(), RomPolicy.class.getName(), "missing", void.class));
    }
}
