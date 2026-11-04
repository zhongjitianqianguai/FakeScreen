package me.neversleep.plusplus;

/** Small platform stand-in for the mode-only cases; identity-switch tests supply their own display. */
final class TestDisplays {
    interface ModeWriter {
        boolean write(int mode) throws ReflectiveOperationException;
    }

    static ScreenPowerController.Display modes(ModeWriter writer) {
        return modes(new Object(), writer);
    }

    static ScreenPowerController.Display modes(Object token, ModeWriter writer) {
        return new ScreenPowerController.Display() {
            @Override public Object currentToken() { return token; }
            @Override public boolean setPowerMode(Object target, int mode) throws ReflectiveOperationException {
                if (target != token) throw new AssertionError("Unexpected display identity");
                return writer.write(mode);
            }
        };
    }

    static ScreenPowerController screenFor(ModeWriter writer) {
        return new ScreenPowerController(modes(writer));
    }

    static ScreenPowerController screenFor(Object token, ModeWriter writer) {
        return new ScreenPowerController(modes(token, writer));
    }
}
