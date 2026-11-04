package me.neversleep.plusplus;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
    private SwitchCompat power;
    private CheckBox disableSleep;
    private boolean rendering;
    private boolean resumed;
    private AlertDialog migrationDialog;
    private final Runnable stateListener = this::render;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
        power = findViewById(R.id.power);
        disableSleep = findViewById(R.id.disable_sleep);
        power.setOnCheckedChangeListener((button, checked) -> changeSetting("power", checked));
        disableSleep.setOnCheckedChangeListener((button, checked) -> changeSetting("disable_sleep", checked));

        TextView info = findViewById(R.id.info);
        info.setText(String.format(Locale.ROOT, "v%s (%s) | jitcor",
                BuildConfig.VERSION_NAME, BuildConfig.FLAVOR));
        info.setOnClickListener(view -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/jitcor"))));
        String command = "am start -n " + BuildConfig.APPLICATION_ID + "/"
                + MainActivity.class.getName() + " --ez power true/false";
        TextView shell = findViewById(R.id.shell_control);
        shell.setText(getString(R.string.shell_control, command));
        shell.setOnLongClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) return false;
            clipboard.setPrimaryClip(ClipData.newPlainText("FakeScreen", command));
            Toast.makeText(this, R.string.copy_success, Toast.LENGTH_SHORT).show();
            return true;
        });
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        AppSettings.addListener(stateListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        ModuleConnection.sync(this);
        render();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (migrationDialog != null) migrationDialog.dismiss();
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        AppSettings.removeListener(stateListener);
        super.onStop();
    }

    private void changeSetting(String key, boolean checked) {
        if (rendering) return;
        if (!AppSettings.set(this, key, checked)) {
            Toast.makeText(this, SettingsMigration.hasLegacyConflict()
                    ? R.string.migration_resolution_required : R.string.failed_tips, Toast.LENGTH_LONG).show();
        } else if (ModuleConnection.isPending()) {
            Toast.makeText(this, R.string.settings_pending, Toast.LENGTH_LONG).show();
        }
        render();
    }

    private void render() {
        rendering = true;
        power.setChecked(AppSettings.enabled(this, "power"));
        disableSleep.setChecked(AppSettings.enabled(this, "disable_sleep"));
        rendering = false;
        TextView tips = findViewById(R.id.tips);
        long active = ModuleConnection.activeVersion();
        tips.setVisibility(View.VISIBLE);
        if (active == 0) {
            tips.setText(R.string.active_tips);
        } else if (active != BuildConfig.VERSION_CODE) {
            tips.setText(getString(R.string.active_warn, active, BuildConfig.VERSION_CODE));
        } else if (ModuleConnection.isPending()) {
            tips.setText(R.string.settings_pending);
        } else {
            tips.setVisibility(View.GONE);
        }
        if (resumed && SettingsMigration.hasLegacyConflict() && migrationDialog == null) {
            showMigrationConflict();
        }
    }

    private void showMigrationConflict() {
        boolean[] saved = SettingsMigration.legacySnapshotValues(this);
        if (saved == null) return;
        SharedPreferences current = AppSettings.local(this);
        String message = getString(R.string.migration_conflict_message,
                stateLabel(saved[0]), stateLabel(saved[1]),
                stateLabel(current.getBoolean("power", false)),
                stateLabel(current.getBoolean("disable_sleep", false)));
        migrationDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.migration_conflict_title)
                .setMessage(message)
                .setPositiveButton(R.string.migration_use_snapshot, (dialog, which) -> resolveMigration(true))
                .setNegativeButton(R.string.migration_keep_current, (dialog, which) -> resolveMigration(false))
                .setNeutralButton(android.R.string.cancel, null)
                .create();
        migrationDialog.setOnDismissListener(dialog -> migrationDialog = null);
        migrationDialog.show();
    }

    private String stateLabel(boolean enabled) {
        return getString(enabled ? R.string.setting_on : R.string.setting_off);
    }

    private void resolveMigration(boolean useSnapshot) {
        if (!SettingsMigration.resolveLegacyConflict(this, AppSettings.local(this), useSnapshot)) {
            Toast.makeText(this, R.string.failed_tips, Toast.LENGTH_LONG).show();
            return;
        }
        AppSettings.settingsChanged(this);
        render();
    }
}
