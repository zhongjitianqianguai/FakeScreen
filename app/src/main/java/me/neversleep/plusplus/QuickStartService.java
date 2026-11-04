package me.neversleep.plusplus;

import android.annotation.TargetApi;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.N)
public class QuickStartService extends TileService {
    private final Runnable stateListener = this::updateTile;

    @Override
    public void onClick() {
        super.onClick();
        boolean enabled = !AppSettings.enabled(this, "power");
        if (!AppSettings.set(this, "power", enabled)) {
            Toast.makeText(this, SettingsMigration.hasLegacyConflict()
                    ? R.string.migration_resolution_required : R.string.failed_tips, Toast.LENGTH_LONG).show();
        } else if (ModuleConnection.isPending()) {
            Toast.makeText(this, R.string.settings_pending, Toast.LENGTH_LONG).show();
        }
        updateTile();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        AppSettings.addListener(stateListener);
        ModuleConnection.sync(this);
        updateTile();
    }

    @Override
    public void onStopListening() {
        AppSettings.removeListener(stateListener);
        super.onStopListening();
    }

    @Override
    public void onDestroy() {
        AppSettings.removeListener(stateListener);
        super.onDestroy();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(AppSettings.enabled(this, "power") ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
