/*
 * Copyright (c) 2021 Blue Jeans Network, Inc. All rights reserved.
 */
package com.bluejeans.android.sdksample

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.bluejeans.bluejeanssdk.meeting.MeetingService


/**
 * It is recommended to start a foreground service before getting into the meeting.
 * Starting a foreground service ensures we have all the system resources available to our app
 * even when in background, thereby not compromising on audio quality, content capture quality
 * during features like content share and also prevents app from being killed due to lack of resources.
 */
class OnGoingMeetingService : Service() {


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onCreate")
        MeetingNotificationUtility.createNotificationChannel(this)
        val notification = MeetingNotificationUtility.getNotification(this)
        notification?.build()?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                //For API level 30 and above
                startForeground(
                    MeetingNotificationUtility.MEETING_NOTIFICATION_ID,
                    it,
                    FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                //Default behaviour of foreground service.
                startForeground(MeetingNotificationUtility.MEETING_NOTIFICATION_ID, it)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.i(TAG, "Service Destroyed")
        stopForeground(true)
        MeetingNotificationUtility.clearMeetingNotification(this)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved called")
        endMeeting()
        /**
         * As we have set android:stopWithTask="false" to get the onTaskRemoved on swipe kill
         * need to stop the service after getting this callback
         * setting android:stopWithTask="true" will auto handle the service stop but restrict
         * onTaskRemoved callback
         */
        stopSelf()
    }

    /**
     * This method will check the meeting status and end the meeting if it is running
     * Calling this inside onTaskRemoved will end the meeting if user swipe kill the application
     * without ending/leaving the meeting. Not doing this will cause meeting runs in background
     * after application swipe kill
     */
    private fun endMeeting() {
        val meetingService = SampleApplication.blueJeansSDK.meetingService
        if (meetingService.meetingState.value == MeetingService.MeetingState.Connected) {
            Log.d(TAG, "Leaving meeting onTaskRemoved")
            meetingService.endMeeting()
        }
    }

    companion object {
        private const val TAG = "OnGoingMeetingService"
        fun startService(context: Context) {
            Log.i(TAG, "Starting service")
            val serviceIntent = Intent().apply {
                setClass(context,OnGoingMeetingService::class.java)
            }
            context.startForegroundService(serviceIntent)
        }

        fun stopService(context: Context) {
            Log.i(TAG, "Stopping service")
            val serviceIntent = Intent().apply {
                setClass(context,OnGoingMeetingService::class.java)
            }
            context.stopService(serviceIntent)
        }
    }
}