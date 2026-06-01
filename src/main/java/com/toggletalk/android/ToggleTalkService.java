package com.toggletalk.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;

public class ToggleTalkService extends Service {
    private static final String TAG = "ToggleTalkService";

    public static final String ACTION_TOGGLE = "com.toggletalk.android.ACTION_TOGGLE";
    public static final String ACTION_STATE_CHANGED = "com.toggletalk.android.ACTION_STATE_CHANGED";
    public static final String ACTION_STATE_UPDATE_FROM_TERMUX = "com.toggletalk.android.STATE_UPDATE";
    public static final String ACTION_SET_DIRECTORY = "com.toggletalk.android.ACTION_SET_DIRECTORY";

    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_DIRECTORY = "target_directory";

    private static final String CHANNEL_ID = "ToggleTalkChannel";
    private static final int NOTIFICATION_ID = 101;

    private String mCurrentState = "IDLE";
    private String mCurrentText = "";
    private boolean mContinueSession = false;
    private String mTargetDirectory = "Home";

    private BroadcastReceiver mTermuxReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_STATE_UPDATE_FROM_TERMUX.equals(intent.getAction())) {
                String state = intent.getStringExtra("state");
                String text = intent.getStringExtra("text");
                if (state != null) {
                    updateState(state, text != null ? text : "");
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        createNotificationChannel();
        
        // Load target directory path from preferences
        mTargetDirectory = getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).getString("target_directory", "Home");
        
        startForeground(NOTIFICATION_ID, buildNotification("IDLE", ""));

        // Register receiver for state updates sent from Termux scripts
        IntentFilter filter = new IntentFilter(ACTION_STATE_UPDATE_FROM_TERMUX);
        // On Android 13+ we should specify receiver export flag, but registering dynamically is default exported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(mTermuxReceiver, filter);
        } else {
            registerReceiver(mTermuxReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand action: " + action);

            if (ACTION_TOGGLE.equals(action)) {
                mContinueSession = intent.getBooleanExtra("continue_session", false);
                handleToggle();
            } else if ("com.toggletalk.android.ACTION_SET_CONTINUE".equals(action)) {
                mContinueSession = intent.getBooleanExtra("continue_session", false);
            } else if (ACTION_SET_DIRECTORY.equals(action)) {
                String dir = intent.getStringExtra(EXTRA_DIRECTORY);
                if (dir != null) {
                    mTargetDirectory = dir;
                    getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putString("target_directory", dir).apply();
                    Log.d(TAG, "Directory updated to: " + dir);
                }
            } else if ("com.toggletalk.android.ACTION_GET_STATE".equals(action)) {
                broadcastStateToApp();
            }
        }
        return START_STICKY;
    }

    private void handleToggle() {
        // Trigger a short haptic feedback vibration
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.vibrate(80);
        }

        // Update state immediately for instant visual feedback
        if ("RECORDING".equals(mCurrentState)) {
            updateState("THINKING", "Processing speech...");
        } else if ("THINKING".equals(mCurrentState) || "SPEAKING".equals(mCurrentState)) {
            updateState("IDLE", "");
        }

        // Construct the RUN_COMMAND Intent to invoke toggle_talk_antigravity.sh in Termux
        Intent runCommandIntent = new Intent();
        runCommandIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
        runCommandIntent.setAction("com.termux.RUN_COMMAND");

        // Set command: bash
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");

        // Setup arguments
        String scriptPath;
        if (mContinueSession) {
            scriptPath = "/data/data/com.termux/files/home/ToggleTalkAndroid/toggle_talk_continue.sh";
        } else {
            scriptPath = "/data/data/com.termux/files/home/ToggleTalkAndroid/toggle_talk_antigravity.sh";
        }
        java.util.List<String> argList = new java.util.ArrayList<>();
        argList.add(scriptPath);
        if (mTargetDirectory != null && !mTargetDirectory.isEmpty() && !"Home".equals(mTargetDirectory) && !"/data/data/com.termux/files/home".equals(mTargetDirectory)) {
            argList.add("-d");
            argList.add(mTargetDirectory);
        }
        String[] arguments = argList.toArray(new String[0]);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arguments);

        // Run in background (don't pop up Termux console)
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");

        Log.d(TAG, "Sending RUN_COMMAND to Termux: continue_session=" + mContinueSession);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(runCommandIntent);
            } else {
                startService(runCommandIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send RUN_COMMAND intent", e);
        }
    }

    private void updateState(String state, String text) {
        Log.d(TAG, "updateState: " + state + ", text: " + text);
        mCurrentState = state;
        mCurrentText = text;

        // Update foreground notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(state, text));
        }

        // Broadcast to Activity, Tiles, Widgets
        broadcastStateToApp();

        // If returned to IDLE, stop foreground service self-destruct if needed, but we keep it active to listen for Widget triggers.
    }

    private void broadcastStateToApp() {
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.putExtra(EXTRA_STATE, mCurrentState);
        intent.putExtra(EXTRA_TEXT, mCurrentText);
        intent.putExtra("continue_session", mContinueSession);
        intent.putExtra(EXTRA_DIRECTORY, mTargetDirectory);
        sendBroadcast(intent);
    }

    private Notification buildNotification(String state, String text) {
        String title = "ToggleTalk: " + state;
        String content = text.isEmpty() ? "Ready to talk" : text;
        if (content.length() > 60) {
            content = content.substring(0, 57) + "...";
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.presence_audio_online)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "ToggleTalk Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");
        unregisterReceiver(mTermuxReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
