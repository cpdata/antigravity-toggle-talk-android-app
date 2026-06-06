package com.toggletalk.android.agent;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.toggletalk.android.ToggleTalkService;

public class AntigravityAgent extends BaseAgent {
    public AntigravityAgent(Context context) {
        super(context);
    }

    @Override
    public void run(String sessionId, String transcript, String targetDir, boolean continueSession) {
        sendRunCommand(getScriptPath(), transcript, targetDir, continueSession, sessionId, ToggleTalkService.ACTION_ANTIGRAVITY_RESPONSE);
    }

    @Override
    public void terminate(String sessionId) {
        Intent runCommandIntent = new Intent();
        runCommandIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
        runCommandIntent.setAction("com.termux.RUN_COMMAND");
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        
        String killCmd = "pkill -9 -f run_antigravity.sh; " +
                         "pkill -9 -f antigravity; " +
                         "find /data/data/com.termux/files/home/.gemini/antigravity-cli/brain -name run.pid -delete";
        
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", killCmd});
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(runCommandIntent);
        } else {
            context.startService(runCommandIntent);
        }
    }

    @Override
    public String getName() {
        return "Antigravity CLI";
    }

    @Override
    public String getId() {
        return "antigravity";
    }

    @Override
    public String getScriptPath() {
        return "/data/data/com.termux/files/home/ToggleTalkAndroid/run_antigravity.sh";
    }
}
