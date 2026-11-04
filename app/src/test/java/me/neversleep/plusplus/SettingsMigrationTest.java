package me.neversleep.plusplus;

import android.content.SharedPreferences;

import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.*;

public class SettingsMigrationTest {
    @Test
    public void firstActivationImportsOfflineChoicesIntoEmptyFrameworkDirectory() throws Exception {
        MemoryPreferences offline = values(true, true);
        MemoryPreferences framework = new MemoryPreferences();
        assertTrue(SettingsMigration.applySnapshot(SettingsMigration.snapshot(offline, false), framework, true));
        assertTrue(framework.getBoolean("power", false));
        assertTrue(framework.getBoolean("disable_sleep", false));
    }

    @Test
    public void offlineChangesOverrideOldFrameworkValuesOnReactivation() throws Exception {
        MemoryPreferences framework = values(true, false);
        MemoryPreferences offline = values(false, true);
        assertTrue(SettingsMigration.applySnapshot(SettingsMigration.snapshot(offline, false), framework, true));
        assertFalse(framework.getBoolean("power", true));
        assertTrue(framework.getBoolean("disable_sleep", false));
    }

    @Test
    public void frameworkSnapshotMovesBackToPrivateStorageWhenDisabled() throws Exception {
        MemoryPreferences offline = values(false, false);
        assertTrue(SettingsMigration.applySnapshot(SettingsMigration.snapshot(values(true, true), true), offline, false));
        assertTrue(offline.getBoolean("power", false));
        assertTrue(offline.getBoolean("disable_sleep", false));
    }

    @Test
    public void olderUntypedSnapshotCannotOverwriteNewerFrameworkKeys() throws Exception {
        Properties old = SettingsMigration.snapshot(values(false, false), true);
        old.remove("source"); // Snapshot written by 1.8.2.
        MemoryPreferences framework = values(true, true);
        assertTrue(SettingsMigration.applyLegacySnapshot(old, framework, true, 100, 200));
        assertTrue(framework.getBoolean("power", false));
        assertTrue(framework.getBoolean("disable_sleep", false));
        MemoryPreferences empty = new MemoryPreferences();
        assertTrue(SettingsMigration.applyLegacySnapshot(old, empty, true, 100, 0));
        assertTrue(empty.contains("power"));
        assertFalse(empty.getBoolean("power", true));
    }

    @Test
    public void newerOldFormatOfflineSnapshotOverridesPreviouslyUsedFrameworkDirectory() throws Exception {
        Properties offline = SettingsMigration.snapshot(values(true, true), false);
        offline.remove("source");
        MemoryPreferences framework = values(false, false);
        assertTrue(SettingsMigration.applyLegacySnapshot(offline, framework, true, 300, 100));
        assertTrue(framework.getBoolean("power", false));
        assertTrue(framework.getBoolean("disable_sleep", false));
    }

    @Test
    public void oldSnapshotAlsoPreservesNewerPrivateEditsWhileInactive() throws Exception {
        Properties old = SettingsMigration.snapshot(values(false, false), false);
        old.remove("source");
        MemoryPreferences current = values(true, true);
        assertTrue(SettingsMigration.applyLegacySnapshot(old, current, false, 100, 200));
        assertTrue(current.getBoolean("power", false));
        assertTrue(current.getBoolean("disable_sleep", false));
    }

    @Test
    public void unknownOrEqualTimesKeepBothCopiesUnconsumedUntilAnExplicitChoice() throws Exception {
        Properties old = SettingsMigration.snapshot(values(true, true), false);
        old.remove("source");
        MemoryPreferences current = values(false, false);
        assertThrows(SettingsMigration.SnapshotConflict.class,
                () -> SettingsMigration.applyLegacySnapshot(old, current, true, 100, 0));
        assertThrows(SettingsMigration.SnapshotConflict.class,
                () -> SettingsMigration.applyLegacySnapshot(old, current, true, 100, 100));
        assertFalse(current.contains("legacy_snapshot_imported"));
        assertFalse(current.getBoolean("power", true));
        assertEquals("true", old.getProperty("power"));
        assertFalse(old.containsKey("source"));
        assertTrue(SettingsMigration.resolveSnapshot(old, current, true));
        assertTrue(current.getBoolean("power", false));
        assertTrue(current.getBoolean("disable_sleep", false));
    }

    @Test
    public void choosingCurrentSettingsConsumesOldConflictWithoutChangingCurrentValues() throws Exception {
        Properties old = SettingsMigration.snapshot(values(true, true), false);
        old.remove("source");
        MemoryPreferences current = values(false, false);
        assertTrue(SettingsMigration.resolveSnapshot(old, current, false));
        assertFalse(current.getBoolean("power", true));
        assertFalse(current.getBoolean("disable_sleep", true));
        assertTrue(SettingsMigration.applyLegacySnapshot(old, current, true, 100, 100));
        assertFalse(current.getBoolean("power", true));
    }

    @Test
    public void choosingCurrentPreservesMissingKeysAndTheirDisplayedDefault() throws Exception {
        Properties old = SettingsMigration.snapshot(values(true, true), false);
        old.remove("source");
        MemoryPreferences current = new MemoryPreferences();
        current.edit().putBoolean("power", false).commit();
        assertThrows(SettingsMigration.SnapshotConflict.class,
                () -> SettingsMigration.applyLegacySnapshot(old, current, true, 100, 100));
        assertTrue(SettingsMigration.resolveSnapshot(old, current, false));
        assertFalse(current.contains("disable_sleep"));
        assertFalse(current.getBoolean("disable_sleep", false));
        assertTrue(SettingsMigration.applyLegacySnapshot(old, current, true, 100, 100));
        assertFalse(current.contains("disable_sleep"));
    }

    @Test
    public void newerCurrentFileDoesNotFillMissingKeyEvenWhenPresentKeyMatchesSnapshot() throws Exception {
        Properties old = SettingsMigration.snapshot(values(true, true), false);
        old.remove("source");
        MemoryPreferences current = new MemoryPreferences();
        current.edit().putBoolean("power", true).commit();
        assertThrows(SettingsMigration.SnapshotConflict.class,
                () -> SettingsMigration.applyLegacySnapshot(old, current, true, 100, 100));
        assertTrue(SettingsMigration.applyLegacySnapshot(old, current, true, 100, 200));
        assertTrue(current.getBoolean("power", false));
        assertFalse(current.contains("disable_sleep"));
        assertFalse(current.getBoolean("disable_sleep", false));
    }

    @Test
    public void repeatedImportDoesNotOverwriteFirstUserAction() throws Exception {
        Properties snapshot = SettingsMigration.snapshot(values(true, true), false);
        MemoryPreferences destination = new MemoryPreferences();
        assertTrue(SettingsMigration.applySnapshot(snapshot, destination, true));
        destination.edit().putBoolean("power", false).commit();
        assertTrue(SettingsMigration.applySnapshot(snapshot, destination, true));
        assertFalse(destination.getBoolean("power", true));
    }

    @Test
    public void modernBridgeStillImportsLegacySnapshotOverLocalValuesOnce() throws Exception {
        MemoryPreferences modern = values(false, false);
        Properties snapshot = SettingsMigration.snapshot(values(true, true), true);
        assertTrue(SettingsMigration.applySnapshot(snapshot, modern, false));
        assertTrue(modern.getBoolean("power", false));
        assertTrue(modern.getBoolean("disable_sleep", false));
        modern.edit().putBoolean("disable_sleep", false).commit();
        assertTrue(SettingsMigration.applySnapshot(snapshot, modern, false));
        assertFalse(modern.getBoolean("disable_sleep", true));
    }

    @Test
    public void invalidSnapshotDoesNotPartiallyApplyOrMarkImported() {
        MemoryPreferences destination = values(false, false);
        Properties snapshot = SettingsMigration.snapshot(values(true, true), false);
        snapshot.setProperty("disable_sleep", "invalid");
        assertThrows(IOException.class, () -> SettingsMigration.applySnapshot(snapshot, destination, true));
        assertFalse(destination.getBoolean("power", true));
        assertFalse(destination.contains("legacy_snapshot_imported"));
    }

    @Test
    public void remoteImportPreservesExplicitLocalChoiceAndFillsOnlyMissingKeys() {
        MemoryPreferences local = new MemoryPreferences();
        local.edit().putBoolean("power", false).commit();
        assertTrue(SettingsMigration.importMissingRemote(local, values(true, true)));
        assertFalse(local.getBoolean("power", true));
        assertTrue(local.getBoolean("disable_sleep", false));
    }

    private static MemoryPreferences values(boolean power, boolean sleep) {
        MemoryPreferences preferences = new MemoryPreferences();
        preferences.edit().putBoolean("power", power).putBoolean("disable_sleep", sleep).commit();
        return preferences;
    }

    private static final class MemoryPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();
        @Override public Map<String, ?> getAll() { return new HashMap<>(values); }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public String getString(String key, String fallback) { return (String) values.getOrDefault(key, fallback); }
        @Override public boolean getBoolean(String key, boolean fallback) { return (boolean) values.getOrDefault(key, fallback); }
        @Override public int getInt(String key, int fallback) { return (int) values.getOrDefault(key, fallback); }
        @Override public long getLong(String key, long fallback) { return (long) values.getOrDefault(key, fallback); }
        @Override public float getFloat(String key, float fallback) { return (float) values.getOrDefault(key, fallback); }
        @SuppressWarnings("unchecked")
        @Override public Set<String> getStringSet(String key, Set<String> fallback) { return (Set<String>) values.getOrDefault(key, fallback); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
        @Override public Editor edit() {
            return new Editor() {
                private final Map<String, Object> updates = new HashMap<>();
                private boolean clear;
                @Override public Editor putString(String key, String value) { updates.put(key, value); return this; }
                @Override public Editor putStringSet(String key, Set<String> value) { updates.put(key, value); return this; }
                @Override public Editor putInt(String key, int value) { updates.put(key, value); return this; }
                @Override public Editor putLong(String key, long value) { updates.put(key, value); return this; }
                @Override public Editor putFloat(String key, float value) { updates.put(key, value); return this; }
                @Override public Editor putBoolean(String key, boolean value) { updates.put(key, value); return this; }
                @Override public Editor remove(String key) { updates.put(key, null); return this; }
                @Override public Editor clear() { clear = true; return this; }
                @Override public boolean commit() {
                    if (clear) values.clear();
                    for (Map.Entry<String, Object> update : updates.entrySet()) {
                        if (update.getValue() == null) values.remove(update.getKey());
                        else values.put(update.getKey(), update.getValue());
                    }
                    return true;
                }
                @Override public void apply() { commit(); }
            };
        }
    }
}
