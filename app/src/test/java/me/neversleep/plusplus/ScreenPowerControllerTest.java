package me.neversleep.plusplus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static me.neversleep.plusplus.TestDisplays.screenFor;

public class ScreenPowerControllerTest {
    @Test
    public void powerPressAlternatesOffAndOn() throws Exception {
        List<Integer> modes = new ArrayList<>();
        ScreenPowerController screen = screenFor(mode -> modes.add(mode));
        assertTrue(screen.toggle());
        assertTrue(screen.toggle());
        assertTrue(screen.toggle());
        assertEquals(Arrays.asList(0, 2, 0), modes);
    }

    @Test
    public void missingDisplayDoesNotConsumePressOrAdvanceState() throws Exception {
        List<Integer> modes = new ArrayList<>();
        ScreenPowerController screen = screenFor(mode -> {
            modes.add(mode);
            return modes.size() > 1;
        });
        assertFalse(screen.toggle());
        assertTrue(screen.toggle());
        assertEquals(Arrays.asList(0, 0), modes);
    }

    @Test
    public void failedWakeCanBeRetried() throws Exception {
        List<Integer> modes = new ArrayList<>();
        ScreenPowerController screen = screenFor(mode -> {
            modes.add(mode);
            if (modes.size() == 2) throw new ReflectiveOperationException("display disconnected");
            return true;
        });
        assertTrue(screen.toggle());
        assertThrows(ReflectiveOperationException.class, screen::toggle);
        assertTrue(screen.toggle());
        assertEquals(Arrays.asList(0, 2, 2), modes);
    }

    @Test
    public void disablingRestoresDisplayOnceAndNextToggleTurnsItOff() throws Exception {
        List<Integer> modes = new ArrayList<>();
        ScreenPowerController screen = screenFor(mode -> modes.add(mode));
        screen.restore();
        assertTrue(modes.isEmpty());
        screen.toggle();
        screen.restore();
        screen.restore();
        screen.toggle();
        assertEquals(Arrays.asList(0, 2, 0), modes);
    }

    @Test
    public void failedRestoreRetainsPendingWake() throws Exception {
        List<Integer> modes = new ArrayList<>();
        ScreenPowerController screen = screenFor(mode -> {
            modes.add(mode);
            return modes.size() != 2;
        });
        screen.toggle();
        screen.restore();
        screen.restore();
        assertEquals(Arrays.asList(0, 2, 2), modes);
    }

    @Test
    public void disableRacingWithAlreadyReadEnabledRestoresPanel() throws Exception {
        AtomicBoolean enabled = new AtomicBoolean(true);
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        CountDownLatch disabling = new CountDownLatch(1);
        List<Integer> modes = new ArrayList<>();
        ScreenPowerController screen = screenFor(mode -> modes.add(mode));
        FutureTask<Boolean> press = new FutureTask<>(() -> screen.toggleIfEnabled(() -> {
            boolean captured = enabled.get();
            read.countDown();
            try {
                if (!proceed.await(5, TimeUnit.SECONDS)) throw new AssertionError("press timeout");
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            return captured;
        }));
        FutureTask<Void> disable = new FutureTask<>(() -> {
            disabling.countDown();
            screen.restoreIfDisabled(enabled::get);
            return null;
        });
        Thread keyThread = new Thread(press, "test-power-key");
        keyThread.setDaemon(true);
        keyThread.start();
        try {
            assertTrue(read.await(5, TimeUnit.SECONDS));
            enabled.set(false);
            Thread settingsThread = new Thread(disable, "test-settings");
            settingsThread.setDaemon(true);
            settingsThread.start();
            assertTrue(disabling.await(5, TimeUnit.SECONDS));
        } finally {
            proceed.countDown();
        }
        assertTrue(press.get(5, TimeUnit.SECONDS));
        disable.get(5, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(0, 2), modes);
    }

    @Test
    public void externalWakeMakesNextPressTurnPanelOff() throws Exception {
        List<Integer> modes = new ArrayList<>();
        Object token = new Object();
        ScreenPowerController screen = screenFor(token, mode -> modes.add(mode));
        screen.toggle();
        assertTrue(screen.beginDisplayChange(token));
        screen.endDisplayChange(true);
        screen.toggle();
        assertEquals(Arrays.asList(0, 0), modes);
    }

    @Test
    public void systemSleepRelinquishesOwnershipSoDisableDoesNotWakeIt() throws Exception {
        List<Integer> modes = new ArrayList<>();
        Object token = new Object();
        ScreenPowerController screen = screenFor(token, mode -> modes.add(mode));
        screen.toggle();
        assertTrue(screen.beginDisplayChange(token));
        screen.endDisplayChange(true);
        screen.restore();
        assertEquals(Arrays.asList(0), modes);
    }

    @Test
    public void otherDisplaysAndFailedPlatformCallsDoNotResetToggle() throws Exception {
        List<Integer> modes = new ArrayList<>();
        Object token = new Object();
        ScreenPowerController screen = screenFor(token, mode -> modes.add(mode));
        screen.toggle();
        assertFalse(screen.beginDisplayChange(new Object()));
        assertTrue(screen.beginDisplayChange(token));
        screen.endDisplayChange(false);
        screen.toggle();
        assertEquals(Arrays.asList(0, 2), modes);
    }

    @Test
    public void ownReentrantDisplayHooksPreserveOffOnOffSequence() throws Exception {
        List<Integer> modes = new ArrayList<>();
        Object token = new Object();
        ScreenPowerController[] controller = new ScreenPowerController[1];
        controller[0] = screenFor(token, mode -> {
            assertTrue(controller[0].beginDisplayChange(token));
            try {
                modes.add(mode);
            } finally {
                controller[0].endDisplayChange(true);
            }
            return true;
        });
        controller[0].toggle();
        controller[0].toggle();
        controller[0].toggle();
        assertEquals(Arrays.asList(0, 2, 0), modes);
    }

    private static final class SwitchingDisplay implements ScreenPowerController.Display {
        Object selected = "A";
        boolean selectionUnavailable;
        boolean failWake;
        int selections;
        final List<String> writes = new ArrayList<>();

        @Override public Object currentToken() throws ReflectiveOperationException {
            selections++;
            if (selectionUnavailable) throw new ReflectiveOperationException("no current display");
            return selected;
        }

        @Override public boolean setPowerMode(Object token, int mode) {
            writes.add(token + ":" + mode);
            if (mode == 2 && failWake) return false;
            if (mode == 0) selected = "B"; // Native active/default display changes after A is off.
            return true;
        }
    }

    @Test
    public void restoringUsesTheClosedPanelThenNextCycleSelectsAgain() throws Exception {
        SwitchingDisplay display = new SwitchingDisplay();
        ScreenPowerController screen = new ScreenPowerController(display);
        assertTrue(screen.toggle());
        assertEquals("B", display.selected);
        assertTrue(screen.toggle());
        assertEquals(1, display.selections);
        assertTrue(screen.toggle());
        assertTrue(screen.toggle());
        assertEquals(Arrays.asList("A:0", "A:2", "B:0", "B:2"), display.writes);
        assertEquals(2, display.selections);
    }

    @Test
    public void disablingRestoresOriginalPanelEvenIfDefaultDisplayLookupNowFails() throws Exception {
        SwitchingDisplay display = new SwitchingDisplay();
        ScreenPowerController screen = new ScreenPowerController(display);
        screen.toggle();
        display.selectionUnavailable = true;
        screen.restore();
        assertEquals(Arrays.asList("A:0", "A:2"), display.writes);
        assertEquals(1, display.selections);
    }

    @Test
    public void failedWakeRetainsTheOriginalDisplayTokenForRetry() throws Exception {
        SwitchingDisplay display = new SwitchingDisplay();
        ScreenPowerController screen = new ScreenPowerController(display);
        screen.toggle();
        display.failWake = true;
        assertFalse(screen.toggle());
        display.failWake = false;
        assertTrue(screen.toggle());
        assertEquals(Arrays.asList("A:0", "A:2", "A:2"), display.writes);
        assertEquals(1, display.selections);
    }

    @Test
    public void absentSelectedPanelDoesNotSendPowerCommands() throws Exception {
        SwitchingDisplay display = new SwitchingDisplay();
        display.selected = null;
        ScreenPowerController screen = new ScreenPowerController(display);
        assertFalse(screen.toggle());
        screen.restore();
        assertTrue(display.writes.isEmpty());
    }

    @Test
    public void platformTakingOverOriginalPanelClearsOnlyThatPendingRestore() throws Exception {
        SwitchingDisplay display = new SwitchingDisplay();
        ScreenPowerController screen = new ScreenPowerController(display);
        screen.toggle();
        assertFalse(screen.beginDisplayChange("B"));
        assertTrue(screen.beginDisplayChange("A"));
        screen.endDisplayChange(true);
        screen.restore();
        screen.toggle();
        assertEquals(Arrays.asList("A:0", "B:0"), display.writes);
    }
}
