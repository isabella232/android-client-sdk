/*
 * Copyright (c) 2021 Blue Jeans Network, Inc. All rights reserved.
 */
package com.bluejeans.android.sdksample;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.bluejeans.bluejeanssdk.meeting.MeetingService;

/**
 * It is recommended to start a foreground service before getting into the meeting.
 * Starting a foreground service ensures we have all the system resources available to our app
 * even when in background, thereby not compromising on audio quality, content capture quality
 * during features like content share and also prevents app from being killed due to lack of resources.
 */
public class OnGoingMeetingService extends Service {

    private static final String TAG = "OnGoingMeetingService";

    public static void startService(Context context) {
        Log.i(TAG, "Starting service");
        Intent serviceIntent = new Intent();
        serviceIntent.setClass(context, OnGoingMeetingService.class);
        context.startForegroundService(serviceIntent);
    }

    public static void stopService(Context context) {
        Log.i(TAG, "Stopping service");
        Intent serviceIntent = new Intent();
        serviceIntent.setClass(context, OnGoingMeetingService.class);
        context.stopService(serviceIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MeetingNotificationUtility.createNotificationChannel(this);
        NotificationCompat.Builder notification = MeetingNotificationUtility.getNotification(this);
        //For API level 30 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(MeetingNotificationUtility.MEETING_NOTIFICATION_ID, notification.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION |
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            //Default behaviour of foreground service.
            startForeground(MeetingNotificationUtility.MEETING_NOTIFICATION_ID, notification.build());
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service Destroyed");
        stopForeground(true);
        MeetingNotificationUtility.clearMeetingNotification(this);
        super.onDestroy();
    }

    /**
     * As we have set android:stopWithTask="false" to get the onTaskRemoved on swipe kill
     * need to stop the service after getting this callback
     * setting android:stopWithTask="true" will auto handle the service stop but restrict
     * onTaskRemoved callback
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "onTaskRemoved called");
        endMeeting();
        stopSelf();
    }

    /**
     * This method will check the meeting status and end the meeting if it is running
     * Calling this inside onTaskRemoved will end the meeting if user swipe kill the application
     * without ending/leaving the meeting. Not doing this will cause meeting runs in background
     * after application swipe kill
     */
    private void endMeeting() {
        MeetingService meetingService = SampleApplication.getBlueJeansSDK().getMeetingService();
        if (meetingService.getMeetingState().getValue() == MeetingService.MeetingState.Connected.INSTANCE) {
            Log.d(TAG, "Leaving meeting onTaskRemoved");
            meetingService.endMeeting();
        }
    }
}
