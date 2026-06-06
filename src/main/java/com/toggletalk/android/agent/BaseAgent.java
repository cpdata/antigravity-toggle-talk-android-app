package com.toggletalk.android.agent;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public abstract class BaseAgent implements AgentCLI {
    protected Context context;
    private static final String TAG = "BaseAgent";

    public BaseAgent(Context context) {
        this.context = context;
    }

    protected void sendRunCommand(String scriptPath, String transcript, String targetDir, boolean continueSession, String sessionId, String callbackAction) {
        Intent runCommandIntent = new Intent();
        runCommandIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
        runCommandIntent.setAction("com.termux.RUN_COMMAND");
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");

        java.util.List<String> argList = new java.util.ArrayList<>();
        argList.add(scriptPath);
        argList.add(transcript);
        argList.add(targetDir);
        argList.add(String.valueOf(continueSession));
        argList.add(sessionId != null ? sessionId : "");
        
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", argList.toArray(new String[0]));
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");

        // PendingIntent for callback
        Intent callbackIntent = new Intent(callbackAction);
        callbackIntent.putExtra("session_id", sessionId);
        callbackIntent.setData(android.net.Uri.parse("session://" + (sessionId != null ? sessionId : "new")));
        
        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= android.app.PendingIntent.FLAG_MUTABLE;
        }
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(context, 1001, callbackIntent, flags);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent);

        Log.d(TAG, "Executing agent " + getId() + " via " + scriptPath);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(runCommandIntent);
        } else {
            context.startService(runCommandIntent);
        }
    }
}
