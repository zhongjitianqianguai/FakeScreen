package me.neversleep.plusplus;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static me.neversleep.plusplus.TestDisplays.modes;

public class PowerKeyPolicyTest {
    static class OldPolicy {
        private boolean mBeganFromNonInteractive;
        private boolean mScreenOnEarly = true;
        private boolean mScreenOnFully = true;
        private void powerPress(long time, boolean interactive, int count) { }
    }

    static class DisplayPolicy {
        boolean awake = true, early = true, fully = true;
        private boolean isAwake() { return awake; }
        private boolean isScreenOnEarly() { return early; }
        private boolean isScreenOnFully() { return fully; }
    }

    static class Detector {
        boolean began;
        private boolean beganFromNonInteractive() { return began; }
    }

    static class PlatformDisplay {
        int state = 2;
        public int getState() { return state; }
    }

    static class NewPolicy {
        private final DisplayPolicy mDefaultDisplayPolicy = new DisplayPolicy();
        private final PlatformDisplay mDefaultDisplay = new PlatformDisplay();
        private final Detector mSingleKeyGestureDetector = new Detector();
        private void powerPress(long time, int count, boolean beganFromNonInteractive) { }
        private void powerPress(long time, int count, int displayId) { }
    }

    private static Method oldMethod() throws Exception {
        return OldPolicy.class.getDeclaredMethod("powerPress", long.class, boolean.class, int.class);
    }

    private static Method newMethod(Class<?> last) throws Exception {
        return NewPolicy.class.getDeclaredMethod("powerPress", long.class, int.class, last);
    }

    @Test
    public void oldInteractiveFlagAndWakeLatchBothPassNormalWakeToAndroid() throws Exception {
        OldPolicy policy = new OldPolicy();
        assertFalse(PowerKeyPolicy.shouldHandle(policy, oldMethod(), new Object[]{1L, false, 1}));
        policy.mBeganFromNonInteractive = true;
        assertFalse(PowerKeyPolicy.shouldHandle(policy, oldMethod(), new Object[]{1L, true, 1}));
        policy.mBeganFromNonInteractive = false;
        assertTrue(PowerKeyPolicy.shouldHandle(policy, oldMethod(), new Object[]{1L, true, 1}));
    }

    @Test
    public void android12BooleanMeansBeganFromNonInteractiveNotInteractive() throws Exception {
        NewPolicy policy = new NewPolicy();
        assertFalse(PowerKeyPolicy.shouldHandle(policy, newMethod(boolean.class), new Object[]{1L, 1, true}));
        assertTrue(PowerKeyPolicy.shouldHandle(policy, newMethod(boolean.class), new Object[]{1L, 1, false}));
        policy.mDefaultDisplay.state = 1;
        assertFalse(PowerKeyPolicy.shouldHandle(policy, newMethod(boolean.class), new Object[]{1L, 1, false}));
    }

    @Test
    public void android12PreservesVrAndOnSuspendDisplaySemantics() throws Exception {
        NewPolicy policy = new NewPolicy();
        policy.mDefaultDisplayPolicy.awake = false;
        for (int state : new int[]{2, 5, 6}) {
            policy.mDefaultDisplay.state = state;
            assertTrue(PowerKeyPolicy.shouldHandle(policy, newMethod(boolean.class), new Object[]{1L, 1, false}));
        }
        policy.mDefaultDisplay.state = 3;
        assertFalse(PowerKeyPolicy.shouldHandle(policy, newMethod(boolean.class), new Object[]{1L, 1, false}));
    }

    @Test
    public void android14BooleanSignatureUsesPolicyWakefulnessInsteadOfDisplayState() throws Exception {
        NewPolicy policy = new NewPolicy();
        policy.mDefaultDisplayPolicy.awake = false;
        policy.mDefaultDisplay.state = 2;
        assertTrue(PowerKeyPolicy.shouldHandle(policy, newMethod(boolean.class), new Object[]{1L, 1, false}, 33));
        assertFalse(PowerKeyPolicy.shouldHandle(policy, newMethod(boolean.class), new Object[]{1L, 1, false}, 34));
        policy.mDefaultDisplayPolicy.awake = true;
        policy.mDefaultDisplay.state = 1;
        assertTrue(PowerKeyPolicy.shouldHandle(policy, newMethod(boolean.class), new Object[]{1L, 1, false}, 34));
        assertFalse(PowerKeyPolicy.shouldHandle(policy, newMethod(boolean.class), new Object[]{1L, 1, true}, 34));
    }

    @Test
    public void currentSignatureReadsGestureDetectorAndPreservesMultiPress() throws Exception {
        NewPolicy policy = new NewPolicy();
        Method method = newMethod(int.class);
        policy.mSingleKeyGestureDetector.began = true;
        assertFalse(PowerKeyPolicy.shouldHandle(policy, method, new Object[]{1L, 1, 0}));
        policy.mSingleKeyGestureDetector.began = false;
        assertTrue(PowerKeyPolicy.shouldHandle(policy, method, new Object[]{1L, 1, 0}));
        assertFalse(PowerKeyPolicy.shouldHandle(policy, method, new Object[]{1L, 2, 0}));
        assertFalse(PowerKeyPolicy.shouldHandle(policy, method, new Object[]{1L, 3, 0}));
        assertFalse(PowerKeyPolicy.shouldHandle(new OldPolicy(), oldMethod(), new Object[]{1L, true, 2}));
        assertFalse(PowerKeyPolicy.shouldHandle(policy, newMethod(boolean.class), new Object[]{1L, 3, false}));
    }

    @Test
    public void screenTurningOnPassesThroughForBothPolicyLayouts() throws Exception {
        OldPolicy old = new OldPolicy();
        old.mScreenOnFully = false;
        assertFalse(PowerKeyPolicy.shouldHandle(old, oldMethod(), new Object[]{1L, true, 1}));
        NewPolicy modern = new NewPolicy();
        modern.mDefaultDisplayPolicy.fully = false;
        assertFalse(PowerKeyPolicy.shouldHandle(modern, newMethod(int.class), new Object[]{1L, 1, 0}));
    }

    @Test
    public void hookOnlyWritesForEligibleSinglePress() throws Exception {
        List<Integer> modes = new ArrayList<>();
        HookImpl hook = new HookImpl(key -> true, (m, e) -> fail(m), modes(mode -> modes.add(mode)));
        NewPolicy policy = new NewPolicy();
        Method method = newMethod(int.class);
        policy.mSingleKeyGestureDetector.began = true;
        assertFalse(hook.onPowerPress(policy, method, new Object[]{1L, 1, 0}));
        policy.mSingleKeyGestureDetector.began = false;
        assertFalse(hook.onPowerPress(policy, method, new Object[]{1L, 2, 0}));
        assertTrue(modes.isEmpty());
        assertTrue(hook.onPowerPress(policy, method, new Object[]{1L, 1, 0}));
        assertTrue(hook.onPowerPress(policy, method, new Object[]{1L, 1, 0}));
        assertEquals(Arrays.asList(0, 2), modes);
    }
}
