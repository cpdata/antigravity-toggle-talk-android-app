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
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
import java.util.ArrayList;

import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig;
import com.k2fsa.sherpa.onnx.GeneratedAudio;

public class ToggleTalkService extends Service {
    private static final String TAG = "ToggleTalkService";

    public static final String ACTION_TOGGLE = "com.toggletalk.android.ACTION_TOGGLE";
    public static final String ACTION_STATE_CHANGED = "com.toggletalk.android.ACTION_STATE_CHANGED";
    public static final String ACTION_STATE_UPDATE_FROM_TERMUX = "com.toggletalk.android.STATE_UPDATE";
    public static final String ACTION_SET_DIRECTORY = "com.toggletalk.android.ACTION_SET_DIRECTORY";
    public static final String ACTION_ANTIGRAVITY_RESPONSE = "com.toggletalk.android.ACTION_ANTIGRAVITY_RESPONSE";
    public static final String ACTION_STOP = "com.toggletalk.android.ACTION_STOP";
    public static final String ACTION_QUEUE_CHANGED = "com.toggletalk.android.ACTION_QUEUE_CHANGED";
    public static final String ACTION_UPDATE_PROMPT = "com.toggletalk.android.ACTION_UPDATE_PROMPT";

    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_DIRECTORY = "target_directory";

    private static final String CHANNEL_ID = "ToggleTalkChannel";
    private static final int NOTIFICATION_ID = 101;

    private String mCurrentState = "IDLE";
    private String mCurrentText = "";
    private boolean mContinueSession = false;
    private boolean mBypassAntigravity = false;
    private String mTargetDirectory = "Home";
    private String mSelectedSessionId = "";
    private volatile boolean mStopRequested = false;
    private volatile boolean mIsTranscribing = false;
    private boolean mIsStreamingActive = false;

    private final java.util.Map<String, java.util.List<String>> mSessionQueues = new java.util.HashMap<>();
    
    private static class TtsItem {
        String sessionId;
        String text;
        TtsItem(String sessionId, String text) {
            this.sessionId = sessionId;
            this.text = text;
        }
    }

    private final java.util.Queue<TtsItem> mTtsQueue = new java.util.LinkedList<>();
    private TtsItem mCurrentlyPlayingItem = null;
    private String mCurrentPlayingChunk = null;
    private boolean mIsTtsPaused = false;
    private boolean mIsTtsPlaying = false;
    private Thread mTtsThread = null;

    // Speech engines
    private OfflineRecognizer mRecognizer = null;
    private OfflineTts mTts = null;

    // Audio Capture
    private AudioRecord mAudioRecord = null;
    private boolean mIsRecordingAudio = false;
    private final java.util.List<Short> mRecordedSamples = new ArrayList<>();
    private Thread mRecordThread = null;

    // Audio Playback
    private android.media.AudioTrack mAudioTrack = null;
    private boolean mIsPlayingAudio = false;

    // Wake and Wifi Locks to prevent device from sleeping while waiting for agent response
    private android.os.PowerManager.WakeLock mWakeLock = null;
    private android.net.wifi.WifiManager.WifiLock mWifiLock = null;

    private BroadcastReceiver mTermuxReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_STATE_UPDATE_FROM_TERMUX.equals(action)) {
                String state = intent.getStringExtra("state");
                String text = intent.getStringExtra("text");
                if (state != null) {
                    updateState(state, text != null ? text : "");
                }
            } else if (ACTION_ANTIGRAVITY_RESPONSE.equals(action)) {
                Log.d(TAG, "Received Antigravity response callback from Termux");
                // If stop was requested, discard the result
                if (mStopRequested) {
                    Log.d(TAG, "Stop requested: discarding Antigravity response");
                    mStopRequested = false;
                    return;
                }
                Bundle resultBundle = intent.getBundleExtra("result");
                if (resultBundle != null) {
                    String stdout = resultBundle.getString("stdout");
                    String errmsg = resultBundle.getString("errmsg");
                    int exitCode = resultBundle.getInt("exitCode", -1);
                    Log.d(TAG, "Exit code: " + exitCode + ", stdout: " + stdout + ", errmsg: " + errmsg);
                    
                    if (exitCode != 0) {
                        Log.e(TAG, "Antigravity failed with exit code: " + exitCode + ". Error: " + errmsg);
                        updateState("IDLE", "Error: " + (errmsg != null && !errmsg.isEmpty() ? errmsg : "Process exited with code " + exitCode));
                        Intent refreshIntent = new Intent("com.toggletalk.android.ACTION_REFRESH_HISTORY");
                        sendBroadcast(refreshIntent);
                        return;
                    }
                    
                    if (stdout != null && !stdout.trim().isEmpty()) {
                        handleAntigravityResponse(stdout);
                    } else {
                        Log.e(TAG, "Empty output from Antigravity. Error: " + errmsg);
                        updateState("IDLE", "Error: " + (errmsg != null ? errmsg : "Empty output"));
                    }
                } else {
                    Log.e(TAG, "No result bundle received from Termux");
                    updateState("IDLE", "Error: No command output received");
                }
            } else if ("com.toggletalk.android.ACTION_STREAM_UPDATE".equals(action)) {
                String sessionId = intent.getStringExtra("session_id");
                String messagesJson = intent.getStringExtra("messages_json");
                String filePath = intent.getStringExtra("file_path");
                String ttsText = intent.getStringExtra("tts_text");
                handleStreamedUpdate(sessionId, messagesJson, filePath, ttsText);
            } else if ("com.toggletalk.android.ACTION_TERMINATE_TTS".equals(action)) {
                Log.d(TAG, "Received ACTION_TERMINATE_TTS intent");
                stopAudioPlayback();
                synchronized (mTtsQueue) {
                    mTtsQueue.clear();
                }
                mCurrentlyPlayingItem = null;
                mCurrentPlayingChunk = null;
                mIsTtsPaused = false;
                updateState("IDLE", "");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        createNotificationChannel();
        
        // Load target directory path and active session ID from preferences
        mTargetDirectory = getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).getString("target_directory", "Home");
        mSelectedSessionId = getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).getString("selected_session_id", "");
        mContinueSession = (mSelectedSessionId != null && !mSelectedSessionId.isEmpty());
        mBypassAntigravity = getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).getBoolean("bypass_antigravity", false);
        
        startForeground(NOTIFICATION_ID, buildNotification("IDLE", ""));

        // Register receiver for state updates and command callbacks
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_STATE_UPDATE_FROM_TERMUX);
        filter.addAction(ACTION_ANTIGRAVITY_RESPONSE);
        filter.addAction("com.toggletalk.android.ACTION_STREAM_UPDATE");
        filter.addAction("com.toggletalk.android.ACTION_TERMINATE_TTS");
        registerReceiver(mTermuxReceiver, filter);
        
        // Lazily initialize speech models in background thread so service creation is instant
        new Thread(new Runnable() {
            @Override
            public void run() {
                initSpeechEngines();
            }
        }).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand action: " + action);

            if (intent.hasExtra("session_id")) {
                String intentSessionId = intent.getStringExtra("session_id");
                if (intentSessionId != null) {
                    mSelectedSessionId = intentSessionId;
                    getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putString("selected_session_id", mSelectedSessionId).apply();
                    Log.d(TAG, "onStartCommand: updated mSelectedSessionId to: " + mSelectedSessionId);
                }
            }

            if (ACTION_TOGGLE.equals(action)) {
                boolean continueIntent = intent.getBooleanExtra("continue_session", false);
                mContinueSession = continueIntent || (mSelectedSessionId != null && !mSelectedSessionId.isEmpty());
                handleToggle();
            } else if (ACTION_STOP.equals(action)) {
                handleStop();
            } else if ("com.toggletalk.android.ACTION_SEND_PROMPT".equals(action)) {
                String prompt = intent.getStringExtra("prompt");
                boolean continueIntent = intent.getBooleanExtra("continue_session", false);
                mContinueSession = continueIntent || (mSelectedSessionId != null && !mSelectedSessionId.isEmpty());
                boolean bypass = intent.getBooleanExtra("bypass_antigravity", mBypassAntigravity);
                
                if (prompt != null && !prompt.trim().isEmpty()) {
                    if (!"IDLE".equals(mCurrentState)) {
                        // Queue the prompt
                        java.util.List<String> queue = mSessionQueues.get(mSelectedSessionId);
                        if (queue == null) {
                            queue = new java.util.ArrayList<>();
                            mSessionQueues.put(mSelectedSessionId, queue);
                        }
                        queue.add(prompt);
                        broadcastQueueChanged();
                    } else {
                        mStopRequested = false; // reset stop flag for new request
                        updateState("THINKING", prompt);
                        if (bypass || prompt.toLowerCase().startsWith("mock:") || prompt.toLowerCase().startsWith("/mock")) {
                            runMockReasoning(prompt);
                        } else {
                            new Thread(() -> runAntigravityReasoning(prompt)).start();
                        }
                    }
                }
            } else if ("com.toggletalk.android.ACTION_ENQUEUE_PROMPT".equals(action)) {
                String prompt = intent.getStringExtra("prompt");
                if (prompt != null && !prompt.trim().isEmpty()) {
                    java.util.List<String> queue = mSessionQueues.get(mSelectedSessionId);
                    if (queue == null) {
                        queue = new java.util.ArrayList<>();
                        mSessionQueues.put(mSelectedSessionId, queue);
                    }
                    queue.add(prompt);
                    broadcastQueueChanged();
                }
            } else if (ACTION_UPDATE_PROMPT.equals(action)) {
                int index = intent.getIntExtra("index", -1);
                String text = intent.getStringExtra("text");
                if (index >= 0 && text != null && !text.trim().isEmpty()) {
                    java.util.List<String> queue = mSessionQueues.get(mSelectedSessionId);
                    if (queue != null && index < queue.size()) {
                        queue.set(index, text);
                        broadcastQueueChanged();
                    }
                }
            } else if ("com.toggletalk.android.ACTION_DELETE_PROMPT".equals(action)) {
                int index = intent.getIntExtra("index", -1);
                if (index >= 0) {
                    java.util.List<String> queue = mSessionQueues.get(mSelectedSessionId);
                    if (queue != null && index < queue.size()) {
                        queue.remove(index);
                        broadcastQueueChanged();
                    }
                }
            } else if ("com.toggletalk.android.ACTION_CLEAR_QUEUE".equals(action)) {
                java.util.List<String> queue = mSessionQueues.get(mSelectedSessionId);
                if (queue != null) {
                    queue.clear();
                    broadcastQueueChanged();
                }
            } else if ("com.toggletalk.android.ACTION_GET_STATE".equals(action)) {
                broadcastStateToApp();
                broadcastQueueChanged();
            } else if ("com.toggletalk.android.ACTION_SET_CONTINUE".equals(action)) {
                mContinueSession = intent.getBooleanExtra("continue_session", false);
            } else if ("com.toggletalk.android.ACTION_SET_BYPASS_ANTIGRAVITY".equals(action)) {
                mBypassAntigravity = intent.getBooleanExtra("bypass_antigravity", false);
                getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putBoolean("bypass_antigravity", mBypassAntigravity).apply();
                Log.d(TAG, "Bypass Antigravity (Mock Mode) set to: " + mBypassAntigravity);
            } else if (ACTION_SET_DIRECTORY.equals(action)) {
                String dir = intent.getStringExtra(EXTRA_DIRECTORY);
                if (dir != null) {
                    mTargetDirectory = dir;
                    getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putString("target_directory", dir).apply();
                    Log.d(TAG, "Directory updated to: " + dir);
                }
            } else if ("com.toggletalk.android.ACTION_SET_SESSION_ID".equals(action)) {
                String sessionId = intent.getStringExtra("session_id");
                if (sessionId != null) {
                    mSelectedSessionId = sessionId;
                    mContinueSession = !sessionId.isEmpty();
                    getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putString("selected_session_id", sessionId).apply();
                    Log.d(TAG, "Session ID updated to: " + sessionId + ", continue: " + mContinueSession);
                }
            }
        }
        return START_STICKY;
    }

    private void broadcastQueueChanged() {
        java.util.List<String> queue = mSessionQueues.get(mSelectedSessionId);
        Intent intent = new Intent(ACTION_QUEUE_CHANGED);
        if (queue != null) {
            intent.putStringArrayListExtra("queue", new java.util.ArrayList<>(queue));
        } else {
            intent.putStringArrayListExtra("queue", new java.util.ArrayList<>());
        }
        sendBroadcast(intent);
    }

    private void checkAndRunNextInQueue() {
        java.util.List<String> queue = mSessionQueues.get(mSelectedSessionId);
        if (queue != null && !queue.isEmpty() && "IDLE".equals(mCurrentState)) {
            String nextPrompt = queue.remove(0);
            broadcastQueueChanged();
            
            mStopRequested = false;
            updateState("THINKING", nextPrompt);
            if (mBypassAntigravity || nextPrompt.toLowerCase().startsWith("mock:") || nextPrompt.toLowerCase().startsWith("/mock")) {
                runMockReasoning(nextPrompt);
            } else {
                new Thread(() -> runAntigravityReasoning(nextPrompt)).start();
            }
        }
    }

    private void handleToggle() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.vibrate(80);
        }

        Log.d(TAG, "handleToggle current state: " + mCurrentState);

        if ("IDLE".equals(mCurrentState)) {
            updateState("RECORDING", "Listening...");
            startNativeRecording();
        } else if ("RECORDING".equals(mCurrentState)) {
            if (mIsTranscribing) {
                Log.d(TAG, "Already transcribing, ignoring toggle");
                return;
            }
            mIsTranscribing = true;
            updateState("THINKING", "Transcribing...");
            float[] samples = stopNativeRecording();
            runNativeTranscription(samples);
        } else if ("THINKING".equals(mCurrentState) || "SPEAKING".equals(mCurrentState)) {
            if ("SPEAKING".equals(mCurrentState) || mIsTtsPlaying) {
                mIsTtsPaused = true;
            }
            stopAudioPlayback();
            stopNativeRecording();
            updateState("RECORDING", "Listening...");
            startNativeRecording();
        }
    }

    private void handleStop() {
        Log.d(TAG, "handleStop: stopping agent and TTS");
        mStopRequested = true;
        stopAudioPlayback();
        mCurrentlyPlayingItem = null;
        mCurrentPlayingChunk = null;
        mIsTtsPaused = false;
        stopNativeRecording();
        synchronized (mTtsQueue) {
            mTtsQueue.clear();
        }
        mIsTtsPlaying = false;
        if (!"IDLE".equals(mCurrentState)) {
            updateState("IDLE", "");
        }
    }

    private void startNativeRecording() {
        if (mIsRecordingAudio) return;
        
        mRecordedSamples.clear();
        mIsRecordingAudio = true;
        
        mRecordThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int sampleRate = 16000;
                int channelConfig = AudioFormat.CHANNEL_IN_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                
                if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    bufferSize = sampleRate * 2;
                }
                
                try {
                    mAudioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    );
                    
                    if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioRecord initialization failed");
                        mIsRecordingAudio = false;
                        updateState("IDLE", "Mic initialization failed");
                        return;
                    }
                    
                    mAudioRecord.startRecording();
                    Log.d(TAG, "AudioRecord started recording");
                    
                    short[] buffer = new short[bufferSize / 2];
                    while (mIsRecordingAudio) {
                        int read = mAudioRecord.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            synchronized (mRecordedSamples) {
                                for (int i = 0; i < read; i++) {
                                    mRecordedSamples.add(buffer[i]);
                                }
                            }
                        } else if (read < 0) {
                            Log.e(TAG, "AudioRecord error: " + read);
                            break;
                        }
                    }
                    
                    mAudioRecord.stop();
                    mAudioRecord.release();
                    mAudioRecord = null;
                    Log.d(TAG, "AudioRecord stopped and released");
                } catch (SecurityException e) {
                    Log.e(TAG, "Recording permission check failed", e);
                    mIsRecordingAudio = false;
                    updateState("IDLE", "Mic permission denied");
                } catch (Exception e) {
                    Log.e(TAG, "Error during native audio recording", e);
                    mIsRecordingAudio = false;
                    updateState("IDLE", "Recording error: " + e.getMessage());
                }
            }
        });
        
        mRecordThread.start();
    }

    private float[] stopNativeRecording() {
        boolean wasRecording = mIsRecordingAudio;
        mIsRecordingAudio = false;
        if (mRecordThread != null) {
            try {
                mRecordThread.join(2000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for record thread to finish", e);
            }
            mRecordThread = null;
        }
        
        if (wasRecording && mIsTtsPaused && mCurrentlyPlayingItem != null && mCurrentPlayingChunk != null) {
            mIsTtsPaused = false;
            synchronized (mTtsQueue) {
                if (mTtsQueue instanceof java.util.LinkedList) {
                    ((java.util.LinkedList<TtsItem>) mTtsQueue).addFirst(mCurrentlyPlayingItem);
                } else {
                    java.util.List<TtsItem> list = new ArrayList<>(mTtsQueue);
                    list.add(0, mCurrentlyPlayingItem);
                    mTtsQueue.clear();
                    mTtsQueue.addAll(list);
                }
            }
            mCurrentlyPlayingItem = null;
            mCurrentPlayingChunk = null;
            new Thread(() -> startTtsPlaybackIfNeeded()).start();
        }
        
        float[] floatSamples;
        synchronized (mRecordedSamples) {
            floatSamples = new float[mRecordedSamples.size()];
            for (int i = 0; i < floatSamples.length; i++) {
                floatSamples[i] = mRecordedSamples.get(i) / 32768.0f;
            }
            mRecordedSamples.clear();
        }
        return floatSamples;
    }

    private void runNativeTranscription(final float[] pcmData) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    initSpeechEngines();
                    if (mRecognizer == null) {
                        Log.e(TAG, "OfflineRecognizer is null, cannot transcribe");
                        updateState("IDLE", "ASR engine initialization failed. Ensure models are placed in /sdcard/ToggleTalkModels/");
                        return;
                    }
                    
                    Log.d(TAG, "Starting Whisper ASR transcription on " + pcmData.length + " samples");
                    OfflineStream stream = mRecognizer.createStream();
                    stream.acceptWaveform(pcmData, 16000);
                    mRecognizer.decode(stream);
                    OfflineRecognizerResult result = mRecognizer.getResult(stream);
                    String text = result.getText();
                    stream.release();
                    
                    Log.d(TAG, "Whisper ASR Transcript: " + text);
                    if (text == null || text.trim().isEmpty()) {
                        Log.i(TAG, "Transcript empty, resetting to IDLE");
                        updateState("IDLE", "");
                        return;
                    }
                    
                    // Broadcast transcript back to activity
                    Intent intent = new Intent("com.toggletalk.android.ACTION_TRANSCRIPTION_RESULT");
                    intent.putExtra("transcript", text);
                    sendBroadcast(intent);

                    // Reset state to IDLE
                    updateState("IDLE", "");
                } catch (Exception e) {
                    Log.e(TAG, "ASR transcription failed", e);
                    updateState("IDLE", "Transcription failed: " + e.getMessage());
                } finally {
                    mIsTranscribing = false;
                }
            }
        }).start();
    }

    private void runMockReasoning(final String prompt) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1500); // Simulate thinking delay
                } catch (InterruptedException ignored) {}

                String queryText = prompt;
                if (prompt.toLowerCase().startsWith("mock:")) {
                    queryText = prompt.substring(5).trim();
                } else if (prompt.toLowerCase().startsWith("/mock")) {
                    queryText = prompt.substring(5).trim();
                }

                String responseText;
                if (queryText.equalsIgnoreCase("list")) {
                    responseText = "### Target Directory Contents\nHere is a list of mock files inside your current directory:\n\n" +
                        "| File Name | Size | Type |\n" +
                        "|---|---|---|\n" +
                        "| `README.md` | 2.4 KB | Markdown |\n" +
                        "| `MainActivity.java` | 31 KB | Java Source |\n" +
                        "| `build.gradle` | 908 B | Gradle Configuration |\n\n" +
                        "<tts>I found three files in the directory: README.md, MainActivity.java, and build.gradle.</tts>";
                } else if (queryText.equalsIgnoreCase("help")) {
                    responseText = "### ToggleTalk Help\nToggleTalk is a voice and text assistant interface.\n\n" +
                        "- **Speech Input**: Click the mic button to start recording speech.\n" +
                        "- **Text Input**: Type message in the input box.\n" +
                        "- **Sessions**: Swipe from the right edge to view recent sessions.\n\n" +
                        "<tts>This is Toggle Talk help. You can speak by tapping the microphone or type in the input box.</tts>";
                } else {
                    responseText = "### Echo Mode\nReceived prompt: **" + queryText + "**\n\nThis is a mocked response simulating Antigravity.\n\n" +
                        "<tts>Echoing your prompt. You said: " + queryText + "</tts>";
                }

                if (mSelectedSessionId == null || mSelectedSessionId.isEmpty()) {
                    String mockSessionId = "mock_session_" + (System.currentTimeMillis() / 1000);
                    mSelectedSessionId = mockSessionId;
                    mContinueSession = true;
                    getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putString("selected_session_id", mockSessionId).apply();
                    Log.d(TAG, "Adopted new mock session ID: " + mockSessionId);
                    
                    Intent sessionIntent = new Intent("com.toggletalk.android.ACTION_NEW_SESSION_ADOPTED");
                    sessionIntent.putExtra("session_id", mockSessionId);
                    sendBroadcast(sessionIntent);
                }

                try {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("latest_response", responseText);
                    obj.put("sanitized_tts", responseText);
                    final String jsonStr = obj.toString();
                    
                    android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                    handler.post(() -> handleAntigravityResponse(jsonStr));
                } catch (Exception e) {
                    Log.e(TAG, "Mock JSON creation failed", e);
                    updateState("IDLE", "Mock failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private void runAntigravityReasoning(String transcript) {
        mIsStreamingActive = true;
        // Construct the RUN_COMMAND Intent to invoke run_antigravity.sh in Termux
        Intent runCommandIntent = new Intent();
        runCommandIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
        runCommandIntent.setAction("com.termux.RUN_COMMAND");

        // Set command: bash
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");

        // Script path
        String scriptPath = "/data/data/com.termux/files/home/ToggleTalkAndroid/run_antigravity.sh";
        
        java.util.List<String> argList = new java.util.ArrayList<>();
        argList.add(scriptPath);
        argList.add(transcript);
        
        String absoluteTargetDir = "/data/data/com.termux/files/home";
        if (mTargetDirectory != null && !mTargetDirectory.isEmpty() && !"Home".equals(mTargetDirectory)) {
            if (mTargetDirectory.startsWith("/")) {
                absoluteTargetDir = mTargetDirectory;
            } else {
                absoluteTargetDir = "/data/data/com.termux/files/home/" + mTargetDirectory;
            }
        }
        argList.add(absoluteTargetDir);
        
        argList.add(String.valueOf(mContinueSession));
        if (mContinueSession && mSelectedSessionId != null && !mSelectedSessionId.isEmpty()) {
            argList.add(mSelectedSessionId);
        } else {
            argList.add("");
        }
        
        String[] arguments = argList.toArray(new String[0]);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arguments);

        // Run in background (don't pop up Termux console)
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");

        // Create PendingIntent for command result callback
        Intent callbackIntent = new Intent(ACTION_ANTIGRAVITY_RESPONSE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1001, callbackIntent, flags);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent);

        Log.d(TAG, "Sending RUN_COMMAND to Termux: transcript=" + transcript + ", continue_session=" + mContinueSession);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(runCommandIntent);
            } else {
                startService(runCommandIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send RUN_COMMAND intent", e);
            updateState("IDLE", "Termux invocation failed");
        }
    }

    private void handleAntigravityResponse(String jsonString) {
        try {
            // Find start of JSON object in case of extra output lines
            int startIdx = jsonString.indexOf("{");
            int endIdx = jsonString.lastIndexOf("}");
            if (startIdx == -1 || endIdx == -1 || endIdx < startIdx) {
                Log.e(TAG, "Failed to locate JSON object in stdout: " + jsonString);
                // Fallback: use raw stdout
                processTTSOutput(jsonString, jsonString);
                return;
            }
            
            String cleanJson = jsonString.substring(startIdx, endIdx + 1);
            org.json.JSONObject obj = new org.json.JSONObject(cleanJson);
            String latestResponse = obj.optString("latest_response", "");
            String sanitizedTts = obj.optString("sanitized_tts", "");
            String sessionId = obj.optString("session_id", "");
            
            if (mSelectedSessionId == null || mSelectedSessionId.isEmpty()) {
                if (sessionId != null && !sessionId.isEmpty()) {
                    mSelectedSessionId = sessionId;
                    mContinueSession = true;
                    getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putString("selected_session_id", sessionId).apply();
                    Log.d(TAG, "Adopted new session ID from final response: " + sessionId);
                    
                    Intent sessionIntent = new Intent("com.toggletalk.android.ACTION_NEW_SESSION_ADOPTED");
                    sessionIntent.putExtra("session_id", sessionId);
                    sendBroadcast(sessionIntent);
                }
            }
            
            if (latestResponse.isEmpty()) {
                latestResponse = jsonString;
            }
            if (sanitizedTts.isEmpty()) {
                sanitizedTts = latestResponse;
            }
            
            processTTSOutput(latestResponse, sanitizedTts);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Antigravity JSON output", e);
            // Fallback: use raw stdout
            processTTSOutput(jsonString, jsonString);
        }
    }

    private java.util.List<String> splitIntoTtsChunks(String text) {
        java.util.List<String> chunks = new java.util.ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }
        String[] parts = text.split("(?<=[.!?;:])\\s+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                chunks.add(trimmed);
            }
        }
        return chunks;
    }

    private String sanitizeInApp(String text) {
        if (text == null) return "";
        text = text.replaceAll("(?m)^#+\\s+", "");
        text = text.replaceAll("\\*\\*([^*]+)\\*\\*|__([^_]+)__", "$1$2");
        text = text.replaceAll("\\*([^*]+)\\*", "$1");
        text = text.replaceAll("_([^_]+)_", "$1");
        text = text.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
        text = text.replaceAll("`([^`]+)`", "$1");
        text = text.replaceAll("```[a-zA-Z0-9_-]*\\s*", "");
        text = text.replaceAll("/", " slash ");
        text = text.replaceAll("\\\\", " backslash ");
        text = text.replaceAll("(?<=\\w)\\.(?=\\w)", " dot ");
        text = text.replaceAll("(^|\\s)\\.([a-zA-Z0-9_-]+)", "$1dot $2");
        text = text.replaceAll("\\$(\\d+(?:\\.\\d+)?)", "$1 dollars");
        text = text.replaceAll("\\$", " dollars ");
        text = text.replaceAll("&", " and ");
        text = text.replaceAll("@", " at ");
        text = text.replaceAll("%", " percent");
        text = text.replaceAll("\\+", " plus ");
        text = text.replaceAll("=", " equals ");
        text = text.replaceAll("#", " number ");
        text = text.replaceAll("~", " tilde ");
        text = text.replaceAll("_", " ");
        text = text.replaceAll("[^\\w\\s.,!?;:\\-'\"()¿¡]", "");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\\n+", " ");
        return text.trim();
    }

    private void processTTSOutput(final String latestResponse, final String sanitizedTts) {
        if (mIsStreamingActive) {
            mIsStreamingActive = false;
            updateState("SPEAKING", latestResponse);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try { Thread.sleep(500); } catch (Exception ignored) {}
                    synchronized (mTtsQueue) {
                        if (mTtsQueue.isEmpty() && !mIsTtsPlaying) {
                            updateState("FINISHED", "");
                        }
                    }
                }
            }).start();
            return;
        }

        updateState("SPEAKING", latestResponse);
        
        // Parse <tts>...</tts> tags in latestResponse
        java.util.List<String> ttsSegments = new java.util.ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<tts>(.*?)</tts>", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(latestResponse);
        while (matcher.find()) {
            ttsSegments.add(matcher.group(1));
        }
        
        String textToSpeak;
        if (!ttsSegments.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String seg : ttsSegments) {
                sb.append(seg).append(" ");
            }
            textToSpeak = sanitizeInApp(sb.toString().trim());
        } else {
            textToSpeak = sanitizedTts;
        }
        
        if (textToSpeak != null && !textToSpeak.trim().isEmpty()) {
            queueTtsText(mSelectedSessionId, textToSpeak);
        } else {
            updateState("FINISHED", "");
        }
    }

    private void handleStreamedUpdate(String sessionId, String messagesJson, String filePath, String ttsText) {
        if (mSelectedSessionId == null || mSelectedSessionId.isEmpty()) {
            mSelectedSessionId = sessionId;
            mContinueSession = true;
            getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putString("selected_session_id", sessionId).apply();
            Log.d(TAG, "Adopted new session ID from stream: " + sessionId);
            
            Intent sessionIntent = new Intent("com.toggletalk.android.ACTION_NEW_SESSION_ADOPTED");
            sessionIntent.putExtra("session_id", sessionId);
            sendBroadcast(sessionIntent);
        }

        if (!mSelectedSessionId.equals(sessionId)) {
            Log.d(TAG, "Ignoring streamed update: sessionId mismatch. expected=" + mSelectedSessionId + ", got=" + sessionId);
            return;
        }

        Intent intent = new Intent("com.toggletalk.android.ACTION_STREAM_DISPLAY");
        intent.putExtra("session_id", sessionId);
        intent.putExtra("messages_json", messagesJson);
        intent.putExtra("file_path", filePath);
        sendBroadcast(intent);

        if (ttsText != null && !ttsText.trim().isEmpty()) {
            String textToSpeak = sanitizeInApp(ttsText.trim());
            if (!textToSpeak.isEmpty()) {
                queueTtsText(sessionId, textToSpeak);
            }
        }
    }

    private synchronized void queueTtsText(String sessionId, String text) {
        if (text == null || text.trim().isEmpty()) return;
        
        java.util.List<String> chunks = splitIntoTtsChunks(text);
        synchronized (mTtsQueue) {
            for (String chunk : chunks) {
                mTtsQueue.add(new TtsItem(sessionId, chunk));
            }
        }
        
        startTtsPlaybackIfNeeded();
    }

    private synchronized void startTtsPlaybackIfNeeded() {
        if (mIsTtsPlaying || mTtsQueue.isEmpty() || mIsTtsPaused) {
            return;
        }
        
        mIsTtsPlaying = true;
        updateState("SPEAKING", "Speaking...");
        
        mTtsThread = new Thread(new Runnable() {
            @Override
            public void run() {
                initSpeechEngines();
                if (mTts == null) {
                    Log.e(TAG, "OfflineTts is null, cannot speak");
                    synchronized (mTtsQueue) {
                        mTtsQueue.clear();
                    }
                    mIsTtsPlaying = false;
                    updateState("IDLE", "TTS engine initialization failed");
                    return;
                }
                
                android.media.AudioTrack track = null;
                mIsPlayingAudio = true;
                
                try {
                    while (mIsPlayingAudio) {
                        TtsItem item;
                        synchronized (mTtsQueue) {
                            if (mTtsQueue.isEmpty()) {
                                break;
                            }
                            item = mTtsQueue.poll();
                        }
                        
                        if (item.sessionId != null && !item.sessionId.equals(mSelectedSessionId)) {
                            Log.d(TAG, "Discarding TTS item from inactive session: " + item.sessionId + " (active: " + mSelectedSessionId + ")");
                            continue;
                        }
                        
                        mCurrentlyPlayingItem = item;
                        mCurrentPlayingChunk = item.text;
                        
                        Log.d(TAG, "Generating TTS chunk: " + mCurrentPlayingChunk);
                        GeneratedAudio audio = mTts.generate(mCurrentPlayingChunk, 0, 1.0f);
                        float[] samples = audio.getSamples();
                        int sampleRate = audio.getSampleRate();
                        
                        if (samples == null || samples.length == 0) {
                            continue;
                        }
                        
                        if (track == null) {
                            int bufferSize = android.media.AudioTrack.getMinBufferSize(
                                sampleRate,
                                android.media.AudioFormat.CHANNEL_OUT_MONO,
                                android.media.AudioFormat.ENCODING_PCM_FLOAT
                            );
                            if (bufferSize == android.media.AudioTrack.ERROR || bufferSize == android.media.AudioTrack.ERROR_BAD_VALUE) {
                                bufferSize = samples.length * 4;
                            }
                            
                            track = new android.media.AudioTrack(
                                new android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build(),
                                new android.media.AudioFormat.Builder()
                                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_FLOAT)
                                    .setSampleRate(sampleRate)
                                    .build(),
                                bufferSize,
                                android.media.AudioTrack.MODE_STREAM,
                                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
                            );
                            
                            if (track.getState() != android.media.AudioTrack.STATE_INITIALIZED) {
                                Log.e(TAG, "AudioTrack initialization failed");
                                break;
                            }
                            mAudioTrack = track;
                            track.play();
                        }
                        
                        int offset = 0;
                        int writeChunkSize = 4096;
                        while (mIsPlayingAudio && offset < samples.length) {
                            int writeSize = Math.min(writeChunkSize, samples.length - offset);
                            int written = track.write(samples, offset, writeSize, android.media.AudioTrack.WRITE_BLOCKING);
                            if (written > 0) {
                                offset += written;
                            } else if (written < 0) {
                                Log.e(TAG, "AudioTrack write error: " + written);
                                break;
                            }
                        }
                    }
                    
                    if (mIsPlayingAudio && track != null) {
                        try { track.stop(); } catch (Exception ignored) {}
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                        while (mIsPlayingAudio && track.getPlayState() == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "TTS streaming synthesis failed", e);
                } finally {
                    cleanupAudioTrack();
                    mIsPlayingAudio = false;
                    mIsTtsPlaying = false;
                    
                    if (!mIsTtsPaused) {
                        mCurrentlyPlayingItem = null;
                        mCurrentPlayingChunk = null;
                    }
                    
                    synchronized (mTtsQueue) {
                        if (!mTtsQueue.isEmpty() && !mIsTtsPaused) {
                            new Thread(() -> startTtsPlaybackIfNeeded()).start();
                        } else {
                            if ("SPEAKING".equals(mCurrentState) && !mIsTtsPaused) {
                                updateState("FINISHED", "");
                            }
                        }
                    }
                }
            }
        });
        
        mTtsThread.start();
    }

    private void stopAudioPlayback() {
        mIsPlayingAudio = false;
        cleanupAudioTrack();
    }

    private void cleanupAudioTrack() {
        try {
            if (mAudioTrack != null) {
                if (mAudioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                    mAudioTrack.stop();
                }
                mAudioTrack.release();
                mAudioTrack = null;
                Log.d(TAG, "AudioTrack stopped and released");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up AudioTrack", e);
        }
    }

    private synchronized void initSpeechEngines() {
        if (mRecognizer != null && mTts != null) {
            return;
        }
        
        java.io.File externalDir = getExternalFilesDir(null);
        String modelDir = (externalDir != null ? externalDir.getAbsolutePath() : getFilesDir().getAbsolutePath()) + "/ToggleTalkModels";
        
        java.io.File dir = new java.io.File(modelDir);
        if (!dir.exists()) {
            Log.w(TAG, "ToggleTalkModels directory not found: " + modelDir);
            return;
        }

        if (mRecognizer == null) {
            try {
                String encoder = modelDir + "/whisper-tiny.en-encoder.int8.onnx";
                String decoder = modelDir + "/whisper-tiny.en-decoder.int8.onnx";
                String tokens = modelDir + "/whisper-tiny.en-tokens.txt";
                
                if (new java.io.File(encoder).exists() && new java.io.File(decoder).exists() && new java.io.File(tokens).exists()) {
                    OfflineWhisperModelConfig whisperConfig = new OfflineWhisperModelConfig();
                    whisperConfig.setEncoder(encoder);
                    whisperConfig.setDecoder(decoder);
                    whisperConfig.setLanguage("en");
                    whisperConfig.setTask("transcribe");
                    
                    OfflineModelConfig modelConfig = new OfflineModelConfig();
                    modelConfig.setWhisper(whisperConfig);
                    modelConfig.setTokens(tokens);
                    modelConfig.setModelType("whisper");
                    modelConfig.setNumThreads(2);
                    modelConfig.setDebug(true);
                    modelConfig.setProvider("cpu");
                    
                    FeatureConfig featConfig = new FeatureConfig();
                    featConfig.setSampleRate(16000);
                    featConfig.setFeatureDim(80);
                    
                    OfflineRecognizerConfig config = new OfflineRecognizerConfig();
                    config.setModelConfig(modelConfig);
                    config.setFeatConfig(featConfig);
                    
                    mRecognizer = new OfflineRecognizer(null, config);
                    Log.i(TAG, "Whisper STT engine initialized successfully");
                } else {
                    Log.w(TAG, "Whisper model files missing in " + modelDir);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Whisper STT engine", e);
            }
        }
        
        if (mTts == null) {
            try {
                String model = modelDir + "/kokoro-en-v0_19-int8.onnx";
                String voices = modelDir + "/voices.bin";
                String tokens = modelDir + "/tokens.txt";
                String dataDir = modelDir + "/espeak-ng-data";
                
                if (new java.io.File(model).exists() && new java.io.File(voices).exists() && new java.io.File(tokens).exists()) {
                    OfflineTtsKokoroModelConfig kokoroConfig = new OfflineTtsKokoroModelConfig();
                    kokoroConfig.setModel(model);
                    kokoroConfig.setVoices(voices);
                    kokoroConfig.setTokens(tokens);
                    kokoroConfig.setDataDir(dataDir);
                    kokoroConfig.setLengthScale(1.0f);
                    
                    OfflineTtsModelConfig modelConfig = new OfflineTtsModelConfig();
                    modelConfig.setKokoro(kokoroConfig);
                    modelConfig.setNumThreads(2);
                    modelConfig.setDebug(true);
                    modelConfig.setProvider("cpu");
                    
                    OfflineTtsConfig config = new OfflineTtsConfig();
                    config.setModel(modelConfig);
                    
                    mTts = new OfflineTts(null, config);
                    Log.i(TAG, "Kokoro TTS engine initialized successfully");
                } else {
                    Log.w(TAG, "Kokoro model files missing in " + modelDir);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Kokoro TTS engine", e);
            }
        }
    }

    private void updateState(String state, String text) {
        Log.d(TAG, "updateState: " + state + ", text: " + text);
        mCurrentState = state;
        mCurrentText = text;

        if ("FINISHED".equals(state)) {
            checkAndRunNextInQueue();
            if ("FINISHED".equals(mCurrentState)) {
                updateState("IDLE", "");
            }
            return;
        }

        if ("IDLE".equals(state)) {
            releaseWakeLocks();
        } else {
            acquireWakeLocks();
        }

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
        intent.putExtra("bypass_antigravity", mBypassAntigravity);
        intent.putExtra(EXTRA_DIRECTORY, mTargetDirectory);
        
        boolean queueEmpty = true;
        synchronized (mTtsQueue) {
            queueEmpty = mTtsQueue.isEmpty() && !mIsTtsPlaying;
        }
        intent.putExtra("tts_queue_empty", queueEmpty);
        
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

    private synchronized void acquireWakeLocks() {
        try {
            if (mWakeLock == null) {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    mWakeLock = pm.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "ToggleTalk::WakeLock");
                }
            }
            if (mWakeLock != null && !mWakeLock.isHeld()) {
                mWakeLock.acquire(60 * 60 * 1000L); // 1 hour max timeout
                Log.d(TAG, "WakeLock acquired");
            }
            
            if (mWifiLock == null) {
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        mWifiLock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "ToggleTalk::WifiLock");
                    } else {
                        mWifiLock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL, "ToggleTalk::WifiLock");
                    }
                }
            }
            if (mWifiLock != null && !mWifiLock.isHeld()) {
                mWifiLock.acquire();
                Log.d(TAG, "WifiLock acquired");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring wake/wifi locks", e);
        }
    }

    private synchronized void releaseWakeLocks() {
        try {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
                Log.d(TAG, "WakeLock released");
            }
            if (mWifiLock != null && mWifiLock.isHeld()) {
                mWifiLock.release();
                Log.d(TAG, "WifiLock released");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing wake/wifi locks", e);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");
        mIsRecordingAudio = false;
        mIsPlayingAudio = false;
        cleanupAudioTrack();
        
        if (mAudioRecord != null) {
            try {
                mAudioRecord.release();
            } catch (Exception ignored) {}
        }
        
        if (mRecognizer != null) {
            mRecognizer.release();
            mRecognizer = null;
        }
        if (mTts != null) {
            mTts.release();
            mTts = null;
        }
        
        unregisterReceiver(mTermuxReceiver);
        releaseWakeLocks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
