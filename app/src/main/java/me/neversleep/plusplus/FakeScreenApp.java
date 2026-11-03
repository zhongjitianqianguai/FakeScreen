package me.neversleep.plusplus;

import android.app.Application;

public final class FakeScreenApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ModuleConnection.initialize(this);
    }
}
