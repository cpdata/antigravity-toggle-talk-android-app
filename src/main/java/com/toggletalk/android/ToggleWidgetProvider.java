package com.toggletalk.android;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;
import android.widget.RemoteViews;

public class ToggleWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "ToggleWidgetProvider";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate");
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, "IDLE");
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ToggleTalkService.ACTION_STATE_CHANGED.equals(intent.getAction())) {
            String state = intent.getStringExtra(ToggleTalkService.EXTRA_STATE);
            if (state == null) {
                state = "IDLE";
            }
            Log.d(TAG, "Widget received state update: " + state);

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, ToggleWidgetProvider.class));
            for (int appWidgetId : appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, state);
            }
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, String state) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.toggle_widget);

        // Click PendingIntent
        Intent intent = new Intent(context, ToggleTalkService.class);
        intent.setAction(ToggleTalkService.ACTION_TOGGLE);
        intent.putExtra("continue_session", false); // Default toggle context
        
        PendingIntent pendingIntent = PendingIntent.getService(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_button_container, pendingIntent);

        // Update colors based on state
        int btnColor;
        int ringColor;
        int micColor;

        switch (state) {
            case "RECORDING":
                btnColor = Color.parseColor("#FF2D55");
                ringColor = Color.parseColor("#66FF2D55");
                micColor = Color.parseColor("#FFFFFF");
                break;
            case "THINKING":
                btnColor = Color.parseColor("#0D1A2E");
                ringColor = Color.parseColor("#6600F2FE");
                micColor = Color.parseColor("#00F2FE");
                break;
            case "SPEAKING":
                btnColor = Color.parseColor("#4CD964");
                ringColor = Color.parseColor("#664CD964");
                micColor = Color.parseColor("#FFFFFF");
                break;
            case "IDLE":
            default:
                btnColor = Color.parseColor("#1A0B2E");
                ringColor = Color.parseColor("#40FFFFFF");
                micColor = Color.parseColor("#E6E6FA");
                break;
        }

        // Apply tints
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            views.setInt(R.id.widget_btn_mic, "setBackgroundTint", btnColor);
            views.setInt(R.id.widget_ring, "setBackgroundTint", ringColor);
            views.setInt(R.id.widget_btn_mic, "setColorFilter", micColor);
        } else {
            // Fallback for older devices if necessary (usually not hit on modern S20)
            views.setInt(R.id.widget_btn_mic, "setColorFilter", micColor);
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
