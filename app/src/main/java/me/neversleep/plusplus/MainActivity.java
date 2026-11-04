package me.neversleep.plusplus;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Keeps the original Shell component name; Android enforces DUMP permission at entry. */
public final class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        handleCommand(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleCommand(intent);
    }

    private void handleCommand(Intent intent) {
        if (intent.hasExtra("power")) {
            boolean enabled = intent.getBooleanExtra("power", false);
            intent.removeExtra("power");
            boolean saved = AppSettings.set(this, "power", enabled);
            Toast.makeText(this, saved ? (ModuleConnection.isPending()
                            ? R.string.settings_pending : R.string.settings_saved)
                            : (SettingsMigration.hasLegacyConflict()
                                    ? R.string.migration_resolution_required : R.string.failed_tips),
                    Toast.LENGTH_LONG).show();
            if (!saved && SettingsMigration.hasLegacyConflict()) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
        } else {
            startActivity(new Intent(this, SettingsActivity.class));
        }
        finish();
    }
}
