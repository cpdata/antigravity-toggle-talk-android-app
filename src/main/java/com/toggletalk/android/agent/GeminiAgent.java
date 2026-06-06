package com.toggletalk.android.agent;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.toggletalk.android.ToggleTalkService;

public class GeminiAgent extends BaseAgent {
    public GeminiAgent(Context context) {
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
        
        // Kill run_gemini.sh and any gemini processes
        String killCmd = "pkill -9 -f run_gemini.sh; " +
                         "pkill -9 -f \"gemini \"; " +
                         "pkill -9 -f \"node.*gemini\"";
        
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
        return "Gemini CLI";
    }

    @Override
    public String getId() {
        return "gemini";
    }

    @Override
    public String getScriptPath() {
        return "/data/data/com.termux/files/home/ToggleTalkAndroid/run_gemini.sh";
    }
}
