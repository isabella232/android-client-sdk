/*
 * Copyright (c) 2020 Blue Jeans Network, Inc. All rights reserved.
 */
package com.bluejeans.android.sdksample;

import com.bluejeans.bluejeanssdk.BlueJeansSDK;
import com.bluejeans.bluejeanssdk.BlueJeansSDKInitParams;
import com.bluejeans.bluejeanssdk.GalleryLayoutConfiguration;
import com.bluejeans.bluejeanssdk.VideoConfiguration;

import android.app.Application;
import android.util.Log;

public class SampleApplication extends Application {

    private static BlueJeansSDK blueJeansSDK;
    private static final String TAG = "SampleApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        initSDK();
        Log.i(
                TAG, "App VersionName " + BuildConfig.VERSION_NAME +
                        " App VersionCode " + BuildConfig.VERSION_CODE +
                        " SDK VersionName " + blueJeansSDK.getVersion());

    }

    private void initSDK() {
        try {
            blueJeansSDK = new BlueJeansSDK(new BlueJeansSDKInitParams(this, false, new VideoConfiguration(new GalleryLayoutConfiguration.FiveByFive())));
        } catch (Exception ex) {
            Log.e(TAG, "Exception while initiating sdk " + ex.getMessage());
        }
    }

    public static BlueJeansSDK getBlueJeansSDK() {
        return blueJeansSDK;
    }
}
