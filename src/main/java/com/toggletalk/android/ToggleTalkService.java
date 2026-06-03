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

    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_DIRECTORY = "target_directory";

    private static final String CHANNEL_ID = "ToggleTalkChannel";
    private static final int NOTIFICATION_ID = 101;

    private String mCurrentState = "IDLE";
    private String mCurrentText = "";
    private boolean mContinueSession = false;
    private String mTargetDirectory = "Home";

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
                Bundle resultBundle = intent.getBundleExtra("result");
                if (resultBundle != null) {
                    String stdout = resultBundle.getString("stdout");
                    String errmsg = resultBundle.getString("errmsg");
                    int exitCode = resultBundle.getInt("exitCode", -1);
                    Log.d(TAG, "Exit code: " + exitCode + ", stdout: " + stdout + ", errmsg: " + errmsg);
                    
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

        // Register receiver for state updates and command callbacks
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_STATE_UPDATE_FROM_TERMUX);
        filter.addAction(ACTION_ANTIGRAVITY_RESPONSE);
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
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.vibrate(80);
        }

        Log.d(TAG, "handleToggle current state: " + mCurrentState);

        if ("IDLE".equals(mCurrentState)) {
            updateState("RECORDING", "Listening...");
            startNativeRecording();
        } else if ("RECORDING".equals(mCurrentState)) {
            float[] samples = stopNativeRecording();
            runNativeTranscription(samples);
        } else if ("THINKING".equals(mCurrentState) || "SPEAKING".equals(mCurrentState)) {
            stopNativeRecording();
            stopAudioPlayback();
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
        mIsRecordingAudio = false;
        if (mRecordThread != null) {
            try {
                mRecordThread.join(2000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for record thread to finish", e);
            }
            mRecordThread = null;
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
                initSpeechEngines();
                if (mRecognizer == null) {
                    Log.e(TAG, "OfflineRecognizer is null, cannot transcribe");
                    updateState("IDLE", "ASR engine initialization failed. Ensure models are placed in /sdcard/ToggleTalkModels/");
                    return;
                }
                
                Log.d(TAG, "Starting Whisper ASR transcription on " + pcmData.length + " samples");
                try {
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
                    
                    // Show transcript in UI and move to THINKING
                    updateState("THINKING", text);
                    
                    // Proceed to invoke Antigravity reasoning
                    runAntigravityReasoning(text);
                } catch (Exception e) {
                    Log.e(TAG, "ASR transcription failed", e);
                    updateState("IDLE", "Transcription failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private void runAntigravityReasoning(String transcript) {
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
        
        if (mTargetDirectory != null && !mTargetDirectory.isEmpty() && !"Home".equals(mTargetDirectory) && !"/data/data/com.termux/files/home".equals(mTargetDirectory)) {
            argList.add(mTargetDirectory);
        } else {
            argList.add("/data/data/com.termux/files/home");
        }
        
        argList.add(String.valueOf(mContinueSession));
        
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

    private void processTTSOutput(final String latestResponse, final String sanitizedTts) {
        updateState("SPEAKING", latestResponse);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                initSpeechEngines();
                if (mTts == null) {
                    Log.e(TAG, "OfflineTts is null, cannot speak");
                    updateState("IDLE", "TTS engine initialization failed. Ensure models are placed in /sdcard/ToggleTalkModels/");
                    return;
                }
                
                Log.d(TAG, "Starting Kokoro TTS synthesis on: " + sanitizedTts);
                try {
                    // Generate audio floats. Default speakerId = 0, speed = 1.0f.
                    GeneratedAudio audio = mTts.generate(sanitizedTts, 0, 1.0f);
                    float[] samples = audio.getSamples();
                    int sampleRate = audio.getSampleRate();
                    
                    if (samples == null || samples.length == 0) {
                        Log.w(TAG, "TTS generated 0 audio samples");
                        updateState("IDLE", "");
                        return;
                    }
                    
                    Log.d(TAG, "Synthesized " + samples.length + " floats at " + sampleRate + "Hz. Starting native playback.");
                    playPcmAudio(samples, sampleRate);
                } catch (Exception e) {
                    Log.e(TAG, "TTS synthesis failed", e);
                    updateState("IDLE", "Speech synthesis failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private void playPcmAudio(float[] samples, int sampleRate) {
        if (mIsPlayingAudio) {
            stopAudioPlayback();
        }
        
        mIsPlayingAudio = true;
        
        int bufferSize = android.media.AudioTrack.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_OUT_MONO,
            android.media.AudioFormat.ENCODING_PCM_FLOAT
        );
        
        if (bufferSize == android.media.AudioTrack.ERROR || bufferSize == android.media.AudioTrack.ERROR_BAD_VALUE) {
            bufferSize = samples.length * 4;
        }
        
        try {
            mAudioTrack = new android.media.AudioTrack(
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
            
            if (mAudioTrack.getState() != android.media.AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed");
                mIsPlayingAudio = false;
                updateState("IDLE", "Audio output initialization failed");
                return;
            }
            
            mAudioTrack.play();
            
            int offset = 0;
            int chunkSize = 4096;
            while (mIsPlayingAudio && offset < samples.length) {
                int writeSize = Math.min(chunkSize, samples.length - offset);
                int written = mAudioTrack.write(samples, offset, writeSize, android.media.AudioTrack.WRITE_BLOCKING);
                if (written > 0) {
                    offset += written;
                } else if (written < 0) {
                    Log.e(TAG, "AudioTrack write error: " + written);
                    break;
                }
            }
            
            if (mIsPlayingAudio) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
                while (mIsPlayingAudio && mAudioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {}
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing PCM audio", e);
        } finally {
            cleanupAudioTrack();
            mIsPlayingAudio = false;
            updateState("IDLE", "");
        }
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

    private synchronized void acquireWakeLocks() {
        try {
            if (mWakeLock == null) {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    mWakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "ToggleTalk::WakeLock");
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
