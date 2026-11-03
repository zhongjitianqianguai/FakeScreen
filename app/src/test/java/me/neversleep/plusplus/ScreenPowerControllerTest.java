package me.neversleep.plusplus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ScreenPowerControllerTest {
    @Test
    public void powerPressAlternatesOffAndOn() throws Exception {
        List<Integer> modes = new ArrayList<>();
        ScreenPowerController screen = new ScreenPowerController(mode -> modes.add(mode));
        assertTrue(screen.toggle());
        assertTrue(screen.toggle());
        assertTrue(screen.toggle());
        assertEquals(Arrays.asList(0, 2, 0), modes);
    }

    @Test
    public void missingDisplayDoesNotConsumePressOrAdvanceState() throws Exception {
        List<Integer> modes = new ArrayList<>();
        ScreenPowerController screen = new ScreenPowerController(mode -> {
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
        ScreenPowerController screen = new ScreenPowerController(mode -> {
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
        ScreenPowerController screen = new ScreenPowerController(mode -> modes.add(mode));
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
        ScreenPowerController screen = new ScreenPowerController(mode -> {
            modes.add(mode);
            return modes.size() != 2;
        });
        screen.toggle();
        screen.restore();
        screen.restore();
        assertEquals(Arrays.asList(0, 2, 2), modes);
    }
}
