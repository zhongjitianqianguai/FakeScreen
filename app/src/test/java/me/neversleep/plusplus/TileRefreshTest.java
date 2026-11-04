package me.neversleep.plusplus;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class TileRefreshTest {
    @Test public void refreshIsRequestedOnceWhenAvailable() {
        AtomicInteger calls = new AtomicInteger();
        assertTrue(AppSettings.requestTileRefresh(calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test public void android13NonCurrentUserRejectionDoesNotEscapeToSettingWriter() {
        assertFalse(AppSettings.requestTileRefresh(() -> {
            throw new IllegalArgumentException("User 999 is not the current user");
        }));
    }

    @Test public void unrelatedProgrammingErrorsAreNotHidden() {
        assertThrows(IllegalStateException.class, () -> AppSettings.requestTileRefresh(() -> {
            throw new IllegalStateException("unexpected failure");
        }));
    }
}
