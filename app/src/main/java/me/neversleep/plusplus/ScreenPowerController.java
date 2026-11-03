package me.neversleep.plusplus;

/** Keeps the toggle in step with successful display operations. */
final class ScreenPowerController {
    interface Display {
        boolean setPowerMode(int mode) throws ReflectiveOperationException;
    }

    private final Display display;
    private boolean screenOff;

    ScreenPowerController(Display display) {
        this.display = display;
    }

    synchronized boolean toggle() throws ReflectiveOperationException {
        if (!display.setPowerMode(screenOff ? 2 : 0)) return false;
        screenOff = !screenOff;
        return true;
    }

    synchronized void restore() throws ReflectiveOperationException {
        if (screenOff && display.setPowerMode(2)) screenOff = false;
    }
}
