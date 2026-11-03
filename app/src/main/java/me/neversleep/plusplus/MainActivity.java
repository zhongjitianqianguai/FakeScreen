package me.neversleep.plusplus;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private SwitchCompat power;
    private CheckBox disableSleep;
    private boolean rendering;
    private final Runnable stateListener = this::render;

    /** Only the legacy build self-hooks this method. */
    public static int getActiveVersion() {
        return 0;
    }

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
        handleCommand(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        AppSettings.addListener(stateListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ModuleConnection.sync(this);
        render();
    }

    @Override
    protected void onStop() {
        AppSettings.removeListener(stateListener);
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (BuildConfig.DEBUG) android.util.Log.d("FakeScreen", "New command: power extra="
                + intent.hasExtra("power"));
        setIntent(intent);
        handleCommand(intent);
    }

    private void handleCommand(Intent intent) {
        if (!intent.hasExtra("power")) return;
        boolean enabled = intent.getBooleanExtra("power", false);
        intent.removeExtra("power");
        boolean saved = AppSettings.set(this, "power", enabled);
        Toast.makeText(this, saved ? (ModuleConnection.isPending()
                        ? R.string.settings_pending : R.string.settings_saved) : R.string.failed_tips,
                Toast.LENGTH_LONG).show();
        finish();
    }

    private void changeSetting(String key, boolean checked) {
        if (rendering) return;
        if (!AppSettings.set(this, key, checked)) {
            Toast.makeText(this, R.string.failed_tips, Toast.LENGTH_LONG).show();
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
    }
}
