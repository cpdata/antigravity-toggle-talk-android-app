package com.toggletalk.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

public class ToggleTileService extends TileService {
    private static final String TAG = "ToggleTileService";

    private boolean mReceiverRegistered = false;

    private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ToggleTalkService.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                String state = intent.getStringExtra(ToggleTalkService.EXTRA_STATE);
                if (state != null) {
                    updateTileState(state);
                }
            }
        }
    };

    @Override
    public void onClick() {
        super.onClick();
        Log.d(TAG, "Tile Clicked");
        
        // Launch toggle action via the Foreground Service
        Intent intent = new Intent(this, ToggleTalkService.class);
        intent.setAction(ToggleTalkService.ACTION_TOGGLE);
        // We can pass true or default false for continue session, let's keep default or check standard preference
        intent.putExtra("continue_session", false);
        startService(intent);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.d(TAG, "onStartListening");
        
        if (!mReceiverRegistered) {
            registerReceiver(mStateReceiver, new IntentFilter(ToggleTalkService.ACTION_STATE_CHANGED));
            mReceiverRegistered = true;
        }

        // Request initial state from service
        Intent intent = new Intent(this, ToggleTalkService.class);
        intent.setAction("com.toggletalk.android.ACTION_GET_STATE");
        startService(intent);
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        Log.d(TAG, "onStopListening");
        
        if (mReceiverRegistered) {
            unregisterReceiver(mStateReceiver);
            mReceiverRegistered = false;
        }
    }

    private void updateTileState(String state) {
        Tile tile = getQsTile();
        if (tile == null) return;

        Log.d(TAG, "Updating tile state: " + state);

        switch (state) {
            case "RECORDING":
                tile.setState(Tile.STATE_ACTIVE);
                tile.setLabel("Recording...");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    tile.setIcon(Icon.createWithResource(this, android.R.drawable.presence_video_online));
                }
                break;
            case "THINKING":
                tile.setState(Tile.STATE_ACTIVE);
                tile.setLabel("Thinking...");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    tile.setIcon(Icon.createWithResource(this, android.R.drawable.presence_video_busy));
                }
                break;
            case "SPEAKING":
                tile.setState(Tile.STATE_ACTIVE);
                tile.setLabel("Speaking...");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    tile.setIcon(Icon.createWithResource(this, android.R.drawable.presence_audio_online));
                }
                break;
            case "IDLE":
            default:
                tile.setState(Tile.STATE_INACTIVE);
                tile.setLabel("ToggleTalk");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    tile.setIcon(Icon.createWithResource(this, android.R.drawable.ic_btn_speak_now));
                }
                break;
        }
        tile.updateTile();
    }
}
