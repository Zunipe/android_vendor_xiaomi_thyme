package com.zunipe.authonwatch;

import android.app.Application;
import android.content.pm.PackageManager;
import android.util.Log;

public class MainApplication extends Application {
    public static final String TAG = "MainApplication";
    public static boolean sHasCamera = false;

    @Override
    public void onCreate() {
        super.onCreate();
        sHasCamera = getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        Log.d(TAG, "onCreate: sHasCamera = " + sHasCamera);
    }
}
