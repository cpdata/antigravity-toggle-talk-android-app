package com.toggletalk.android;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import com.toggletalk.android.agent.AgentManager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared logic for ToggleTalk views.
 */
public class ToggleTalkMainHelper {
    private static final String TAG = "ToggleTalkMainHelper";
    private final Activity mActivity;
    private final SharedPreferences mPrefs;
    
    private String mSelectedSessionId = "";
    private boolean mContinueSession = false;
    private String mTargetDirectory = "Home";
    private boolean mBypassAntigravity = false;

    public ToggleTalkMainHelper(Activity activity) {
        this.mActivity = activity;
        this.mPrefs = activity.getSharedPreferences("ToggleTalkPrefs", Context.MODE_PRIVATE);
        this.mSelectedSessionId = mPrefs.getString("selected_session_id", "");
        this.mContinueSession = !mSelectedSessionId.isEmpty();
        this.mBypassAntigravity = mPrefs.getBoolean("bypass_antigravity", false);
        this.mTargetDirectory = mPrefs.getString("target_directory", "Home");
    }

    private String getAbsoluteTargetDir() {
        String absoluteTargetDir = "/data/data/com.termux/files/home";
        if (mTargetDirectory != null && !mTargetDirectory.isEmpty() && !"Home".equals(mTargetDirectory)) {
            if (mTargetDirectory.startsWith("/")) {
                absoluteTargetDir = mTargetDirectory;
            } else {
                absoluteTargetDir = "/data/data/com.termux/files/home/" + mTargetDirectory;
            }
        }
        return absoluteTargetDir;
    }

    public String getSelectedSessionId() { return mSelectedSessionId; }
    public boolean isContinueSession() { return mContinueSession; }
    public String getTargetDirectory() { return mTargetDirectory; }
    public boolean isBypassAntigravity() { return mBypassAntigravity; }

    public void setSessionId(String sessionId, boolean continueSession) {
        mSelectedSessionId = sessionId;
        mContinueSession = continueSession;
        mPrefs.edit().putString("selected_session_id", sessionId).apply();
    }

    public void setTargetDirectory(String dir) {
        mTargetDirectory = dir;
    }

    public void setBypassAntigravity(boolean bypass) {
        mBypassAntigravity = bypass;
        mPrefs.edit().putBoolean("bypass_antigravity", bypass).apply();
    }

    public void sendMessage(String message) {
        if (message == null || message.trim().isEmpty()) return;
        Intent intent = new Intent(mActivity, ToggleTalkService.class);
        intent.setAction("com.toggletalk.android.ACTION_SEND_PROMPT");
        intent.putExtra("prompt", message);
        intent.putExtra("continue_session", mContinueSession);
        intent.putExtra("session_id", mSelectedSessionId);
        intent.putExtra("bypass_antigravity", mBypassAntigravity);
        mActivity.startService(intent);
    }

    public void runTermuxCommand(String command, boolean background) {
        Intent runCommandIntent = new Intent();
        runCommandIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
        runCommandIntent.setAction("com.termux.RUN_COMMAND");
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", command});
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", background);
        if (!background) {
            runCommandIntent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", 1); // 1 = NEW_SESSION
        }
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_WORKDIR", getAbsoluteTargetDir());

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mActivity.startForegroundService(runCommandIntent);
            } else {
                mActivity.startService(runCommandIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to run Termux command: " + command, e);
            Toast.makeText(mActivity, "Failed to run command in Termux", Toast.LENGTH_SHORT).show();
        }
    }
    
    public List<String> getSavedCommands() {
        String cmds = mPrefs.getString("saved_commands", "git status");
        List<String> list = new ArrayList<>();
        for (String s : cmds.split("\n")) {
            if (!s.trim().isEmpty()) list.add(s.trim());
        }
        return list;
    }
    
    public void saveCommand(String command) {
        String cmds = mPrefs.getString("saved_commands", "git status");
        if (!cmds.contains(command)) {
            cmds += "\n" + command;
            mPrefs.edit().putString("saved_commands", cmds.trim()).apply();
        }
    }

    public static final String ACTION_DIRECTORIES_LIST = "com.toggletalk.android.ACTION_DIRECTORIES_LIST";
    public static final String ACTION_SESSIONS_LIST = "com.toggletalk.android.ACTION_SESSIONS_LIST";
    public static final String ACTION_SESSION_HISTORY = "com.toggletalk.android.ACTION_SESSION_HISTORY";

    public void queryTermuxDirectories() {
        Intent callbackIntent = new Intent(ACTION_DIRECTORIES_LIST);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mActivity, 999, callbackIntent, flags);

        Intent runCommandIntent = new Intent();
        runCommandIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
        runCommandIntent.setAction("com.termux.RUN_COMMAND");
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/find");
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{
                "/data/data/com.termux/files/home",
                "-maxdepth", "1",
                "-type", "d",
                "-not", "-name", ".*"
        });
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mActivity.startForegroundService(runCommandIntent);
            } else {
                mActivity.startService(runCommandIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query directories", e);
        }
    }

    public void queryTermuxSessions() {
        Intent callbackIntent = new Intent(ACTION_SESSIONS_LIST);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mActivity, 888, callbackIntent, flags);

        String activeAgentId = AgentManager.getInstance(mActivity).getActiveAgent().getId();
        String absDir = getAbsoluteTargetDir();

        Intent runCommandIntent = new Intent();
        runCommandIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
        runCommandIntent.setAction("com.termux.RUN_COMMAND");
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        
        String command = "AGENT=" + activeAgentId + " python3 /data/data/com.termux/files/home/ToggleTalkAndroid/list_sessions.py \"" + absDir + "\"";
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", command});
        
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_WORKDIR", absDir);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mActivity.startForegroundService(runCommandIntent);
            } else {
                mActivity.startService(runCommandIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query sessions", e);
        }
    }

    public void querySessionHistory(String sessionId) {
        Intent callbackIntent = new Intent(ACTION_SESSION_HISTORY);
        callbackIntent.putExtra("session_id", sessionId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mActivity, 999, callbackIntent, flags);

        String activeAgentId = AgentManager.getInstance(mActivity).getActiveAgent().getId();

        Intent runCommandIntent = new Intent();
        runCommandIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
        runCommandIntent.setAction("com.termux.RUN_COMMAND");
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        
        String command = "AGENT=" + activeAgentId + " python3 /data/data/com.termux/files/home/ToggleTalkAndroid/load_session_history.py " + sessionId;
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", command});
        
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mActivity.startForegroundService(runCommandIntent);
            } else {
                mActivity.startService(runCommandIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query session history", e);
        }
    }
}
