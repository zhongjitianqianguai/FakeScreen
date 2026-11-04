package me.neversleep.plusplus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static me.neversleep.plusplus.TestDisplays.modes;

public class HookBehaviorTest {
    @Test
    public void displayFailureReturnsControlToAndroidAndCanBeRetried() {
        List<Integer> modes = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        HookImpl hooks = new HookImpl(key -> true, (message, error) -> errors.add(error), modes(mode -> {
            modes.add(mode);
            if (modes.size() == 1) throw new ReflectiveOperationException("no display");
            return true;
        }));
        assertFalse(hooks.onPowerPress());
        assertEquals(1, errors.size());
        assertTrue(hooks.onPowerPress());
        assertEquals(Arrays.asList(0, 0), modes);
    }

    @Test
    public void missingTokenDoesNotConsumePowerKey() {
        HookImpl hooks = new HookImpl(key -> true, (message, error) -> fail(message), modes(mode -> false));
        assertFalse(hooks.onPowerPress());
    }

    @Test
    public void disablingRestoresPanelAndLeavesFuturePressToAndroid() {
        boolean[] enabled = {true};
        List<Integer> modes = new ArrayList<>();
        HookImpl hooks = new HookImpl(key -> enabled[0],
                (message, error) -> fail(message), modes(mode -> modes.add(mode)));
        assertTrue(hooks.onPowerPress());
        enabled[0] = false;
        hooks.onConfigurationChanged();
        assertFalse(hooks.onPowerPress());
        assertEquals(Arrays.asList(0, 2), modes);
    }

    @Test
    public void disableSleepRemainsIndependentOfPowerToggle() {
        HookImpl hooks = new HookImpl(key -> "disable_sleep".equals(key),
                (message, error) -> fail(message), modes(mode -> { fail("Unexpected display operation"); return false; }));
        assertTrue(hooks.keepAwake());
        assertFalse(hooks.onPowerPress());
    }

    @Test
    public void disableSleepSuppressesTouchDimmingRequest() {
        HookImpl hooks = new HookImpl(key -> "disable_sleep".equals(key),
                (message, error) -> fail(message), modes(mode -> true));
        assertFalse(hooks.adjustTouchDimming(true));
        assertFalse(hooks.adjustTouchDimming(false));
    }

    @Test
    public void touchDimmingPassesThroughWhenDisableSleepIsOff() {
        HookImpl hooks = new HookImpl(key -> false,
                (message, error) -> fail(message), modes(mode -> true));
        assertTrue(hooks.adjustTouchDimming(true));
        assertFalse(hooks.adjustTouchDimming(false));
    }

    @Test
    public void powerToggleSettingDoesNotSuppressTouchDimming() {
        HookImpl hooks = new HookImpl(key -> "power".equals(key),
                (message, error) -> fail(message), modes(mode -> true));
        assertTrue(hooks.adjustTouchDimming(true));
        assertFalse(hooks.adjustTouchDimming(false));
    }
}
