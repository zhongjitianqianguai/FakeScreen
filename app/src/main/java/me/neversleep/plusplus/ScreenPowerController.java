package me.neversleep.plusplus;

import java.util.concurrent.locks.ReentrantLock;

/** Keeps the toggle in step with successful display operations. */
final class ScreenPowerController {
    interface Enabled {
        boolean get();
    }
    interface Display {
        Object currentToken() throws ReflectiveOperationException;
        boolean setPowerMode(Object token, int mode) throws ReflectiveOperationException;
    }

    private final Display display;
    private final ReentrantLock lock = new ReentrantLock();
    private boolean screenOff;
    private Object displayToken;
    private int moduleWrites;

    ScreenPowerController(Display display) {
        this.display = display;
    }

    boolean toggle() throws ReflectiveOperationException {
        return toggleIfEnabled(() -> true);
    }

    boolean toggleIfEnabled(Enabled enabled) throws ReflectiveOperationException {
        lock.lock();
        try {
            // The setting read and its display operation belong to the same transaction.
            if (!enabled.get()) {
                restoreLocked();
                return false;
            }
            if (screenOff) return restoreLocked();
            Object token = display.currentToken();
            if (token == null) return false;
            displayToken = token;
            boolean succeeded = false;
            try {
                succeeded = writePowerMode(0);
                screenOff = succeeded;
                return succeeded;
            } finally {
                if (!succeeded) displayToken = null;
            }
        } finally {
            lock.unlock();
        }
    }

    void restore() throws ReflectiveOperationException {
        restoreIfDisabled(() -> false);
    }

    void restoreIfDisabled(Enabled enabled) throws ReflectiveOperationException {
        lock.lock();
        try {
            if (!enabled.get()) restoreLocked();
        } finally {
            lock.unlock();
        }
    }

    private boolean restoreLocked() throws ReflectiveOperationException {
        if (!screenOff) return true;
        // Restore the panel we actually turned off, even if the native active/default
        // display changed as a consequence of that OFF operation.
        if (!writePowerMode(2)) return false;
        screenOff = false;
        displayToken = null;
        return true;
    }

    private boolean writePowerMode(int mode) throws ReflectiveOperationException {
        moduleWrites++;
        try {
            return display.setPowerMode(displayToken, mode);
        } finally {
            moduleWrites--;
        }
    }

    // Hold the same lock across the original platform call, including its after-hook.
    boolean beginDisplayChange(Object token) {
        lock.lock();
        if (token != null && token.equals(displayToken)) return true;
        lock.unlock();
        return false;
    }

    void endDisplayChange(boolean succeeded) {
        try {
            // Android has taken over this panel. A future press starts from normal state.
            // Our own reentrant calls must retain the toggle/restore bookkeeping.
            if (succeeded && moduleWrites == 0) {
                screenOff = false;
                displayToken = null;
            }
        } finally {
            lock.unlock();
        }
    }
}
