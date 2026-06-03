package com.toggletalk.android;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String ACTION_DIRECTORIES_LIST = "com.toggletalk.android.ACTION_DIRECTORIES_LIST";

    private TextView mTvStatus;
    private ScrollView mScrollLog;
    private android.widget.LinearLayout mChatContainer;
    private TextView mActiveAgentTextView;
    private CheckBox mCbContinue;
    private CheckBox mCbMockMode;
    private boolean mBypassAntigravity = false;
    private ImageButton mBtnMic;
    private ProgressBar mPbThinking;
    private android.widget.EditText mEtMessage;
    private ImageButton mBtnSend;

    private View mRingInner;
    private View mRingMiddle;
    private View mRingOuter;

    // Drawer Views
    private View mDrawerRoot;
    private View mDrawerContent;
    private View mDrawerDimBackground;
    private ListView mLvDirectories;
    private ProgressBar mPbDrawerLoading;
    private TextView mTvActiveDir;
    private TextView mTvActiveDirTop;
    private TextView mTvEmptyDirs;
    private ImageButton mBtnMenu;
    private ImageButton mBtnRefreshDirs;

    private String mCurrentState = "IDLE";
    private String mUserPrompt = "";
    private boolean mContinueSession = false;
    private String mTargetDirectory = "Home";
    
    private final List<String> mDirectoriesList = new ArrayList<>();
    private DirectoryAdapter mDirectoryAdapter;
    private boolean mIsDrawerOpen = false;

    // Right Drawer Views
    private View mRightDrawerRoot;
    private View mRightDrawerContent;
    private View mRightDrawerDimBackground;
    private ListView mLvSessions;
    private ProgressBar mPbRightDrawerLoading;
    private TextView mTvActiveSession;
    private TextView mTvEmptySessions;
    private ImageButton mBtnRightMenu;
    private ImageButton mBtnRefreshSessions;

    private boolean mIsRightDrawerOpen = false;
    private boolean mIsDraggingRightDrawer = false;
    private float mInitialRightTranslationX = 0f;
    private String mSelectedSessionId = "";
    private final List<String> mSessionsList = new ArrayList<>();
    private ArrayAdapter<String> mSessionsAdapter;

    private static final String ACTION_SESSIONS_LIST = "com.toggletalk.android.ACTION_SESSIONS_LIST";

    // Gesture tracking variables for drawer edge swipe
    private float mTouchStartX = 0f;
    private float mTouchStartY = 0f;
    private boolean mIsDraggingDrawer = false;
    private float mInitialTranslationX = 0f;

    // Animation list
    private final List<ViewAnimation> mRunningAnimations = new ArrayList<>();

    // Broadcast Receiver for App State Updates
    private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ToggleTalkService.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                String state = intent.getStringExtra(ToggleTalkService.EXTRA_STATE);
                String text = intent.getStringExtra(ToggleTalkService.EXTRA_TEXT);
                mContinueSession = intent.getBooleanExtra("continue_session", false);
                mBypassAntigravity = intent.getBooleanExtra("bypass_antigravity", false);
                if (mCbMockMode != null) {
                    mCbMockMode.setChecked(mBypassAntigravity);
                }
                
                String dir = intent.getStringExtra(ToggleTalkService.EXTRA_DIRECTORY);
                if (dir != null) {
                    mTargetDirectory = dir;
                    updateActiveDirLabel();
                }

                if (state != null) {
                    onStateChanged(state, text != null ? text : "");
                }
            }
        }
    };

    // Broadcast Receiver for Termux Command Output (Directories List)
    private final BroadcastReceiver mDirectoriesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_DIRECTORIES_LIST.equals(intent.getAction())) {
                Log.d(TAG, "Received directories list callback from Termux");
                mPbDrawerLoading.setVisibility(View.GONE);
                
                Bundle resultBundle = intent.getBundleExtra("result");
                if (resultBundle != null) {
                    String stdout = resultBundle.getString("stdout");
                    String errmsg = resultBundle.getString("errmsg");
                    
                    if (stdout != null && !stdout.trim().isEmpty()) {
                        parseAndPopulateDirectories(stdout);
                    } else {
                        Log.w(TAG, "Stdout empty. Errmsg: " + errmsg);
                        showEmptyDirectoriesState();
                    }
                } else {
                    Log.w(TAG, "Result bundle was null");
                    showEmptyDirectoriesState();
                }
            }
        }
    };

    // Broadcast Receiver for Speech Transcription Results
    private final BroadcastReceiver mTranscriptionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.toggletalk.android.ACTION_TRANSCRIPTION_RESULT".equals(intent.getAction())) {
                String transcript = intent.getStringExtra("transcript");
                if (transcript != null && !transcript.trim().isEmpty()) {
                    appendTranscription(transcript);
                }
            }
        }
    };

    // Broadcast Receiver for Termux Command Output (Sessions List)
    private final BroadcastReceiver mSessionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SESSIONS_LIST.equals(intent.getAction())) {
                Log.d(TAG, "Received sessions list callback from Termux");
                mPbRightDrawerLoading.setVisibility(View.GONE);
                
                Bundle resultBundle = intent.getBundleExtra("result");
                if (resultBundle != null) {
                    String stdout = resultBundle.getString("stdout");
                    String errmsg = resultBundle.getString("errmsg");
                    
                    if (stdout != null && !stdout.trim().isEmpty()) {
                        parseAndPopulateSessions(stdout);
                    } else {
                        Log.w(TAG, "Stdout empty. Errmsg: " + errmsg);
                        showEmptySessionsState();
                    }
                } else {
                    Log.w(TAG, "Result bundle was null");
                    showEmptySessionsState();
                }
            }
        }
    };

    private static class ViewAnimation {
        final View view;
        final Animation animation;

        ViewAnimation(View view, Animation animation) {
            this.view = view;
            this.animation = animation;
        }

        void cancel() {
            view.clearAnimation();
            animation.cancel();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind main UIs
        mTvStatus = findViewById(R.id.tv_status);
        mChatContainer = findViewById(R.id.layout_chat_container);
        mScrollLog = findViewById(R.id.scroll_log);
        mCbContinue = findViewById(R.id.cb_continue);
        mCbMockMode = findViewById(R.id.cb_mock_mode);
        mBtnMic = findViewById(R.id.btn_mic);
        mPbThinking = findViewById(R.id.pb_thinking);
        mEtMessage = findViewById(R.id.et_message);
        mBtnSend = findViewById(R.id.btn_send);
        mBtnSend.setOnClickListener(v -> sendMessage());
        mEtMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        mRingInner = findViewById(R.id.ring_inner);
        mRingMiddle = findViewById(R.id.ring_middle);
        mRingOuter = findViewById(R.id.ring_outer);

        // Bind Drawer UIs
        mDrawerRoot = findViewById(R.id.drawer_root);
        mDrawerContent = findViewById(R.id.drawer_content);
        mDrawerDimBackground = findViewById(R.id.drawer_dim_background);
        mLvDirectories = findViewById(R.id.lv_directories);
        mPbDrawerLoading = findViewById(R.id.pb_drawer_loading);
        mTvActiveDir = findViewById(R.id.tv_active_dir);
        mTvActiveDirTop = findViewById(R.id.tv_active_dir_top);
        mTvEmptyDirs = findViewById(R.id.tv_empty_dirs);
        mBtnMenu = findViewById(R.id.btn_menu);
        mBtnRefreshDirs = findViewById(R.id.btn_refresh_dirs);

        // Bind Right Drawer UIs
        mRightDrawerRoot = findViewById(R.id.right_drawer_root);
        mRightDrawerContent = findViewById(R.id.right_drawer_content);
        mRightDrawerDimBackground = findViewById(R.id.right_drawer_dim_background);
        mLvSessions = findViewById(R.id.lv_sessions);
        mPbRightDrawerLoading = findViewById(R.id.pb_right_drawer_loading);
        mTvActiveSession = findViewById(R.id.tv_active_session);
        mTvEmptySessions = findViewById(R.id.tv_empty_sessions);
        mBtnRightMenu = findViewById(R.id.btn_right_menu);
        mBtnRefreshSessions = findViewById(R.id.btn_refresh_sessions);

        // Setup ListView Adapter
        mDirectoryAdapter = new DirectoryAdapter(this, mDirectoriesList);
        mLvDirectories.setAdapter(mDirectoryAdapter);

        mSessionsAdapter = new SessionAdapter(this, mSessionsList);
        mLvSessions.setAdapter(mSessionsAdapter);

        // Setup Listeners
        mBtnMic.setOnClickListener(v -> toggleTalkSession());
        mBtnMenu.setOnClickListener(v -> openDrawer());
        mDrawerDimBackground.setOnClickListener(v -> closeDrawer());
        mBtnRefreshDirs.setOnClickListener(v -> queryTermuxDirectories());

        mBtnRightMenu.setOnClickListener(v -> openRightDrawer());
        mRightDrawerDimBackground.setOnClickListener(v -> closeRightDrawer());
        mBtnRefreshSessions.setOnClickListener(v -> queryTermuxSessions());

        mLvDirectories.setOnItemClickListener((parent, view, position, id) -> {
            String selectedDir = mDirectoriesList.get(position);
            selectDirectory(selectedDir);
        });

        mLvSessions.setOnItemClickListener((parent, view, position, id) -> {
            String selectedSession = mSessionsList.get(position);
            selectSession(selectedSession);
        });

        // Load active session from preferences
        mSelectedSessionId = getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).getString("selected_session_id", "");
        updateActiveSessionLabel();

        mCbContinue.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mContinueSession = isChecked;
            Intent intent = new Intent(MainActivity.this, ToggleTalkService.class);
            intent.setAction("com.toggletalk.android.ACTION_SET_CONTINUE");
            intent.putExtra("continue_session", isChecked);
            startService(intent);
        });

        // Load and setup mock mode settings
        mBypassAntigravity = getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).getBoolean("bypass_antigravity", false);
        mCbMockMode.setChecked(mBypassAntigravity);
        mCbMockMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mBypassAntigravity = isChecked;
            getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putBoolean("bypass_antigravity", isChecked).apply();
            Intent intent = new Intent(MainActivity.this, ToggleTalkService.class);
            intent.setAction("com.toggletalk.android.ACTION_SET_BYPASS_ANTIGRAVITY");
            intent.putExtra("bypass_antigravity", isChecked);
            startService(intent);
        });

        // Initialize state visually
        onStateChanged("IDLE", "");
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Start Foreground Service
        Intent serviceIntent = new Intent(this, ToggleTalkService.class);
        startService(serviceIntent);

        // Register State Receiver
        registerReceiver(mStateReceiver, new IntentFilter(ToggleTalkService.ACTION_STATE_CHANGED));

        // Register Directories Broadcast Callback
        registerReceiver(mDirectoriesReceiver, new IntentFilter(ACTION_DIRECTORIES_LIST));

        // Register Transcription Receiver
        registerReceiver(mTranscriptionReceiver, new IntentFilter("com.toggletalk.android.ACTION_TRANSCRIPTION_RESULT"));

        // Register Sessions Broadcast Callback
        registerReceiver(mSessionsReceiver, new IntentFilter(ACTION_SESSIONS_LIST));

        // Check Permissions
        checkPermissionsAndPreferences();

        // Request initial state from Service
        Intent getStateIntent = new Intent(this, ToggleTalkService.class);
        getStateIntent.setAction("com.toggletalk.android.ACTION_GET_STATE");
        startService(getStateIntent);

        // Default directories setup
        mDirectoriesList.clear();
        mDirectoriesList.add("Home");
        mDirectoryAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(mStateReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(mDirectoriesReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(mTranscriptionReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(mSessionsReceiver); } catch (Exception ignored) {}
        clearAllAnimations();
    }

    private void checkPermissionsAndPreferences() {
        boolean termuxGranted = checkSelfPermission("com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED;
        boolean audioGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean storageGranted = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        
        java.util.List<String> needed = new java.util.ArrayList<>();
        if (!audioGranted) {
            needed.add(android.Manifest.permission.RECORD_AUDIO);
        }
        if (!storageGranted) {
            needed.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        
        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), 100);
        }
        
        if (!termuxGranted) {
            String warning = "⚠️ WARNING: 'Run commands in Termux' permission is not granted!\n\n" +
                    "To enable it:\n" +
                    "1. Open Settings -> Apps -> ToggleTalk\n" +
                    "2. Go to Permissions -> Additional permissions\n" +
                    "3. Select 'Run commands in Termux environment' and set to Allow.";
            addSystemMessage(warning, "#FFCC00");
        } else if (!audioGranted || !storageGranted) {
            addSystemMessage("⚠️ WARNING: Microphone and Storage permissions are required to use speech features. Please grant access.", "#FFCC00");
        } else {
            if (mChatContainer != null && mChatContainer.getChildCount() == 0) {
                addSystemMessage("System ready. Press the mic button or type a message to start.", "#E6E6FA");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            checkPermissionsAndPreferences();
        }
    }

    private void toggleTalkSession() {
        Intent intent = new Intent(this, ToggleTalkService.class);
        intent.setAction(ToggleTalkService.ACTION_TOGGLE);
        intent.putExtra("continue_session", mContinueSession);
        startService(intent);
    }

    // --- Drawer Slide Animation Actions ---

    private int getDrawerWidth() {
        int width = mDrawerContent.getWidth();
        if (width == 0) {
            width = (int) (280 * getResources().getDisplayMetrics().density);
        }
        return width;
    }

    private void openDrawer() {
        if (mIsDrawerOpen) return;
        mDrawerRoot.setVisibility(View.VISIBLE);
        int width = getDrawerWidth();
        mDrawerContent.setTranslationX(-width);
        mDrawerDimBackground.setAlpha(0f);
        animateDrawer(-width, 0f, 1.0f, true);
    }

    private void closeDrawer() {
        if (!mIsDrawerOpen) return;
        int width = getDrawerWidth();
        animateDrawer(mDrawerContent.getTranslationX(), -width, 0f, false);
    }

    private void animateDrawer(float fromTranslationX, float toTranslationX, float toAlpha, final boolean open) {
        mIsDrawerOpen = open;

        // Slide card animation
        ObjectAnimator slideAnim = ObjectAnimator.ofFloat(mDrawerContent, "translationX", toTranslationX);
        slideAnim.setDuration(200);
        slideAnim.setInterpolator(new DecelerateInterpolator());

        // Fade dim background animation
        ObjectAnimator fadeAnim = ObjectAnimator.ofFloat(mDrawerDimBackground, "alpha", toAlpha);
        fadeAnim.setDuration(200);

        slideAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (open) {
                    mDrawerRoot.setVisibility(View.VISIBLE);
                    queryTermuxDirectories();
                } else {
                    mDrawerRoot.setVisibility(View.GONE);
                }
            }
        });

        slideAnim.start();
        fadeAnim.start();
    }

    private void cancelChildTouches(android.view.MotionEvent ev) {
        android.view.MotionEvent cancelEvent = android.view.MotionEvent.obtain(ev);
        cancelEvent.setAction(android.view.MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancelEvent);
        cancelEvent.recycle();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        float density = getResources().getDisplayMetrics().density;
        int drawerWidth = getDrawerWidth();
        int rightDrawerWidth = getRightDrawerWidth();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        switch (ev.getAction()) {
            case android.view.MotionEvent.ACTION_DOWN:
                mTouchStartX = ev.getX();
                mTouchStartY = ev.getY();
                mIsDraggingDrawer = false;
                mIsDraggingRightDrawer = false;
                
                if (mIsDrawerOpen) {
                    mInitialTranslationX = mDrawerContent.getTranslationX();
                } else {
                    mInitialTranslationX = -drawerWidth;
                }
                
                if (mIsRightDrawerOpen) {
                    mInitialRightTranslationX = mRightDrawerContent.getTranslationX();
                } else {
                    mInitialRightTranslationX = rightDrawerWidth;
                }
                break;

            case android.view.MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - mTouchStartX;
                float dy = ev.getY() - mTouchStartY;

                // Handle Left Drawer Gestures
                if (!mIsDraggingDrawer && !mIsDraggingRightDrawer && !mIsRightDrawerOpen) {
                    if (!mIsDrawerOpen) {
                        // Closed: touch starts near left edge (within 50dp), moves right
                        if (mTouchStartX < 50 * density && dx > 15 * density && Math.abs(dx) > Math.abs(dy) * 1.5) {
                            mIsDraggingDrawer = true;
                            mDrawerRoot.setVisibility(View.VISIBLE);
                            queryTermuxDirectories();
                            cancelChildTouches(ev);
                        }
                    } else {
                        // Open: touch moves left to close
                        if (dx < -15 * density && Math.abs(dx) > Math.abs(dy) * 1.5) {
                            mIsDraggingDrawer = true;
                            cancelChildTouches(ev);
                        }
                    }
                }

                // Handle Right Drawer Gestures
                if (!mIsDraggingDrawer && !mIsDraggingRightDrawer && !mIsDrawerOpen) {
                    if (!mIsRightDrawerOpen) {
                        // Closed: touch starts near right edge (within 50dp), moves left
                        if (mTouchStartX > screenWidth - 50 * density && dx < -15 * density && Math.abs(dx) > Math.abs(dy) * 1.5) {
                            mIsDraggingRightDrawer = true;
                            mRightDrawerRoot.setVisibility(View.VISIBLE);
                            queryTermuxSessions();
                            cancelChildTouches(ev);
                        }
                    } else {
                        // Open: touch moves right to close
                        if (dx > 15 * density && Math.abs(dx) > Math.abs(dy) * 1.5) {
                            mIsDraggingRightDrawer = true;
                            cancelChildTouches(ev);
                        }
                    }
                }

                if (mIsDraggingDrawer) {
                    float newTranslationX = mInitialTranslationX + dx;
                    if (newTranslationX > 0f) newTranslationX = 0f;
                    if (newTranslationX < -drawerWidth) newTranslationX = -drawerWidth;

                    mDrawerContent.setTranslationX(newTranslationX);
                    
                    float progress = (drawerWidth + newTranslationX) / drawerWidth;
                    mDrawerDimBackground.setAlpha(progress);
                    return true;
                }

                if (mIsDraggingRightDrawer) {
                    float newTranslationX = mInitialRightTranslationX + dx;
                    if (newTranslationX < 0f) newTranslationX = 0f;
                    if (newTranslationX > rightDrawerWidth) newTranslationX = rightDrawerWidth;

                    mRightDrawerContent.setTranslationX(newTranslationX);
                    
                    float progress = (rightDrawerWidth - newTranslationX) / rightDrawerWidth;
                    mRightDrawerDimBackground.setAlpha(progress);
                    return true;
                }
                break;

            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL:
                if (mIsDraggingDrawer) {
                    mIsDraggingDrawer = false;
                    float currentTranslationX = mDrawerContent.getTranslationX();
                    if (currentTranslationX > -drawerWidth * 0.5f) {
                        animateDrawer(currentTranslationX, 0f, 1.0f, true);
                    } else {
                        animateDrawer(currentTranslationX, -drawerWidth, 0f, false);
                    }
                    return true;
                }

                if (mIsDraggingRightDrawer) {
                    mIsDraggingRightDrawer = false;
                    float currentTranslationX = mRightDrawerContent.getTranslationX();
                    if (currentTranslationX < rightDrawerWidth * 0.5f) {
                        animateRightDrawer(currentTranslationX, 0f, 1.0f, true);
                    } else {
                        animateRightDrawer(currentTranslationX, rightDrawerWidth, 0f, false);
                    }
                    return true;
                }
                break;
        }

        if (mIsDraggingDrawer || mIsDraggingRightDrawer) {
            return true;
        }

        return super.dispatchTouchEvent(ev);
    }

    private void selectDirectory(String dir) {
        mTargetDirectory = dir;
        updateActiveDirLabel();
        mDirectoryAdapter.notifyDataSetChanged();

        // Update the ToggleTalkService with the new active directory setting
        Intent intent = new Intent(this, ToggleTalkService.class);
        intent.setAction(ToggleTalkService.ACTION_SET_DIRECTORY);
        intent.putExtra("target_directory", dir);
        startService(intent);

        Toast.makeText(this, "Active directory set to: " + dir, Toast.LENGTH_SHORT).show();
        
        // Close drawer smoothly
        mDrawerContent.postDelayed(this::closeDrawer, 150);
    }

    private void sendMessage() {
        if (mEtMessage == null) return;
        String message = mEtMessage.getText().toString().trim();
        if (message.isEmpty()) return;

        mEtMessage.setText("");

        // Check if continue checkbox status
        Intent intent = new Intent(this, ToggleTalkService.class);
        intent.setAction("com.toggletalk.android.ACTION_SEND_PROMPT");
        intent.putExtra("prompt", message);
        intent.putExtra("continue_session", mContinueSession);
        intent.putExtra("bypass_antigravity", mBypassAntigravity);
        startService(intent);
    }

    private void appendTranscription(String text) {
        if (mEtMessage == null) return;
        int start = mEtMessage.getSelectionStart();
        int end = mEtMessage.getSelectionEnd();
        String currentText = mEtMessage.getText().toString();
        
        if (start >= 0 && end >= 0) {
            StringBuilder sb = new StringBuilder(currentText);
            sb.replace(start, end, text);
            mEtMessage.setText(sb.toString());
            mEtMessage.setSelection(start + text.length());
        } else {
            if (!currentText.isEmpty() && !currentText.endsWith(" ")) {
                mEtMessage.setText(currentText + " " + text);
            } else {
                mEtMessage.setText(currentText + text);
            }
            mEtMessage.setSelection(mEtMessage.getText().length());
        }
        
        mEtMessage.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mEtMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void updateActiveDirLabel() {
        mTvActiveDir.setText("Active: " + mTargetDirectory);
        if (mTvActiveDirTop != null) {
            mTvActiveDirTop.setText("Active: " + mTargetDirectory);
        }
    }

    // --- Right Drawer (Sessions) Slide Animation Actions ---

    private int getRightDrawerWidth() {
        int width = mRightDrawerContent.getWidth();
        if (width == 0) {
            width = (int) (280 * getResources().getDisplayMetrics().density);
        }
        return width;
    }

    private void openRightDrawer() {
        if (mIsRightDrawerOpen) return;
        mRightDrawerRoot.setVisibility(View.VISIBLE);
        int width = getRightDrawerWidth();
        mRightDrawerContent.setTranslationX(width);
        mRightDrawerDimBackground.setAlpha(0f);
        animateRightDrawer(width, 0f, 1.0f, true);
    }

    private void closeRightDrawer() {
        if (!mIsRightDrawerOpen) return;
        int width = getRightDrawerWidth();
        animateRightDrawer(mRightDrawerContent.getTranslationX(), width, 0f, false);
    }

    private void animateRightDrawer(float fromTranslationX, float toTranslationX, float toAlpha, final boolean open) {
        mIsRightDrawerOpen = open;

        // Slide card animation
        ObjectAnimator slideAnim = ObjectAnimator.ofFloat(mRightDrawerContent, "translationX", toTranslationX);
        slideAnim.setDuration(200);
        slideAnim.setInterpolator(new DecelerateInterpolator());

        // Fade dim background animation
        ObjectAnimator fadeAnim = ObjectAnimator.ofFloat(mRightDrawerDimBackground, "alpha", toAlpha);
        fadeAnim.setDuration(200);

        slideAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (open) {
                    mRightDrawerRoot.setVisibility(View.VISIBLE);
                    queryTermuxSessions();
                } else {
                    mRightDrawerRoot.setVisibility(View.GONE);
                }
            }
        });

        slideAnim.start();
        fadeAnim.start();
    }

    private void selectSession(String sessionId) {
        mSelectedSessionId = sessionId;
        updateActiveSessionLabel();
        mSessionsAdapter.notifyDataSetChanged();

        // Enable continue conversation checkbox automatically
        mContinueSession = true;
        mCbContinue.setChecked(true);

        // Update the ToggleTalkService with the new active session ID
        Intent intent = new Intent(this, ToggleTalkService.class);
        intent.setAction("com.toggletalk.android.ACTION_SET_SESSION_ID");
        intent.putExtra("session_id", sessionId);
        intent.putExtra("continue_session", true);
        startService(intent);

        Toast.makeText(this, "Active session set: " + (sessionId.length() > 8 ? sessionId.substring(0, 8) + "..." : sessionId), Toast.LENGTH_SHORT).show();
        
        // Close right drawer smoothly
        mRightDrawerContent.postDelayed(this::closeRightDrawer, 150);
    }

    private void updateActiveSessionLabel() {
        if (mTvActiveSession == null) return;
        if (mSelectedSessionId == null || mSelectedSessionId.isEmpty()) {
            mTvActiveSession.setText("Active: None");
        } else {
            String shortId = mSelectedSessionId;
            if (mSelectedSessionId.length() > 10) {
                shortId = mSelectedSessionId.substring(0, 8) + "...";
            }
            mTvActiveSession.setText("Active: " + shortId);
        }
    }

    private void addSystemMessage(String text, String colorHex) {
        if (mChatContainer == null) return;
        float density = getResources().getDisplayMetrics().density;
        
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTextSize(14);
        tv.setPadding((int)(12 * density), (int)(8 * density), (int)(12 * density), (int)(8 * density));
        
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.parseColor("#1AFFFFFF"));
        gd.setCornerRadius(density * 8);
        gd.setStroke((int)density, Color.parseColor("#1A" + colorHex.substring(1)));
        tv.setBackground(gd);
        
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(6 * density), 0, (int)(6 * density));
        tv.setLayoutParams(lp);
        
        mChatContainer.addView(tv);
        mScrollLog.post(() -> mScrollLog.fullScroll(View.FOCUS_DOWN));
    }

    private void addUserBubble(String message) {
        if (mChatContainer == null || message == null || message.trim().isEmpty()) return;
        
        float density = getResources().getDisplayMetrics().density;
        
        android.widget.LinearLayout bubbleLayout = new android.widget.LinearLayout(this);
        bubbleLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(8 * density), 0, (int)(8 * density));
        bubbleLayout.setLayoutParams(lp);
        bubbleLayout.setGravity(android.view.Gravity.END);
        
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(15);
        tv.setPadding((int)(14 * density), (int)(10 * density), (int)(14 * density), (int)(10 * density));
        
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.parseColor("#4D9D50BB"));
        gd.setCornerRadius(density * 12);
        gd.setStroke((int)density, Color.parseColor("#669D50BB"));
        tv.setBackground(gd);
        
        android.widget.LinearLayout.LayoutParams tvLp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvLp.setMarginStart((int)(40 * density));
        tv.setLayoutParams(tvLp);
        
        bubbleLayout.addView(tv);
        mChatContainer.addView(bubbleLayout);
        mScrollLog.post(() -> mScrollLog.fullScroll(View.FOCUS_DOWN));
    }

    private void addAgentBubble(String initialText) {
        if (mChatContainer == null) return;
        
        float density = getResources().getDisplayMetrics().density;
        
        android.widget.LinearLayout bubbleLayout = new android.widget.LinearLayout(this);
        bubbleLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(8 * density), 0, (int)(8 * density));
        bubbleLayout.setLayoutParams(lp);
        bubbleLayout.setGravity(android.view.Gravity.START);
        
        TextView tv = new TextView(this);
        tv.setText(renderMarkdown(initialText));
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(15);
        tv.setPadding((int)(14 * density), (int)(10 * density), (int)(14 * density), (int)(10 * density));
        
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.parseColor("#2600F2FE"));
        gd.setCornerRadius(density * 12);
        gd.setStroke((int)density, Color.parseColor("#4D00F2FE"));
        tv.setBackground(gd);
        
        android.widget.LinearLayout.LayoutParams tvLp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvLp.setMarginEnd((int)(40 * density));
        tv.setLayoutParams(tvLp);
        
        bubbleLayout.addView(tv);
        mChatContainer.addView(bubbleLayout);
        mActiveAgentTextView = tv;
        mScrollLog.post(() -> mScrollLog.fullScroll(View.FOCUS_DOWN));
    }

    private android.os.Handler mTypeHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable mTypeRunnable;
    
    private void streamAgentResponse(final String fullText) {
        if (mActiveAgentTextView == null) {
            addAgentBubble("");
        }
        
        if (mTypeRunnable != null) {
            mTypeHandler.removeCallbacks(mTypeRunnable);
        }
        
        final int[] index = {0};
        mTypeRunnable = new Runnable() {
            @Override
            public void run() {
                if (index[0] <= fullText.length()) {
                    String part = fullText.substring(0, index[0]);
                    mActiveAgentTextView.setText(renderMarkdown(part));
                    mScrollLog.post(() -> mScrollLog.fullScroll(View.FOCUS_DOWN));
                    index[0] += 2;
                    mTypeHandler.postDelayed(this, 15);
                } else {
                    mActiveAgentTextView.setText(renderMarkdown(fullText));
                    mScrollLog.post(() -> mScrollLog.fullScroll(View.FOCUS_DOWN));
                }
            }
        };
        mTypeHandler.post(mTypeRunnable);
    }

    private static android.text.Spanned renderMarkdown(String markdown) {
        if (markdown == null) return new android.text.SpannableString("");
        
        // Headers
        String html = markdown.replaceAll("(?m)^###\\s+(.*)$", "<br/><font color=\"#00F2FE\"><b>$1</b></font><br/>");
        html = html.replaceAll("(?m)^##\\s+(.*)$", "<br/><font color=\"#00F2FE\"><b><big>$1</big></b></font><br/>");
        html = html.replaceAll("(?m)^#\\s+(.*)$", "<br/><font color=\"#00F2FE\"><b><big><big>$1</big></big></b></font><br/>");

        // Bold
        html = html.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        html = html.replaceAll("__([^_]+)__", "<b>$1</b>");

        // Italic
        html = html.replaceAll("\\*([^*]+)\\*", "<i>$1</i>");
        html = html.replaceAll("_([^_]+)_", "<i>$1</i>");

        // Bullets
        html = html.replaceAll("(?m)^[\\-*]\\s+(.*)$", "&#8226; $1<br/>");

        // Code blocks
        html = html.replaceAll("(?s)```[a-zA-Z0-9_-]*\\n(.*?)\\n```", "<br/><font face=\"monospace\" color=\"#00F2FE\"><tt>$1</tt></font><br/>");

        // Inline code
        html = html.replaceAll("`([^`]+)`", "<font face=\"monospace\" color=\"#E6E6FA\"><tt>$1</tt></font>");

        // Strip <tts> tags
        html = html.replaceAll("(?s)<tts>.*?</tts>", "");

        // Convert newlines
        html = html.replaceAll("\\n", "<br/>");
        html = html.replaceAll("(<br/>\\s*){2,}", "<br/><br/>");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY);
        } else {
            return android.text.Html.fromHtml(html);
        }
    }

    // --- Query Termux Antigravity Sessions list via RUN_COMMAND ---

    private void queryTermuxSessions() {
        mPbRightDrawerLoading.setVisibility(View.VISIBLE);
        mTvEmptySessions.setVisibility(View.GONE);

        // Create PendingIntent for command result callback
        Intent callbackIntent = new Intent(ACTION_SESSIONS_LIST);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 888, callbackIntent, flags);

        // Build the command Intent targeting Termux's RunCommandService
        Intent runCommandIntent = new Intent();
        runCommandIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
        runCommandIntent.setAction("com.termux.RUN_COMMAND");

        // Executable path: find utility
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/find");

        // Arguments: list directories in .gemini/antigravity-cli/brain/
        String[] arguments = new String[]{
                "/data/data/com.termux/files/home/.gemini/antigravity-cli/brain",
                "-maxdepth", "1",
                "-type", "d",
                "-not", "-path", "/data/data/com.termux/files/home/.gemini/antigravity-cli/brain"
        };
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arguments);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
        
        // Set PendingIntent extra
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent);

        Log.d(TAG, "Querying Termux sessions...");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(runCommandIntent);
            } else {
                startService(runCommandIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch sessions query command", e);
            mPbRightDrawerLoading.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to query Termux environment", Toast.LENGTH_LONG).show();
        }
    }

    private void parseAndPopulateSessions(String stdout) {
        String[] lines = stdout.split("\n");
        List<String> list = new ArrayList<>();
        
        String brainPath = "/data/data/com.termux/files/home/.gemini/antigravity-cli/brain";

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.equals(brainPath)) {
                continue;
            }
            
            // Extract folder name relative to brain dir
            if (line.startsWith(brainPath + "/")) {
                String relativeName = line.substring(brainPath.length() + 1);
                if (!relativeName.isEmpty() && !list.contains(relativeName)) {
                    list.add(relativeName);
                }
            }
        }

        // Sort items (sessions)
        Collections.sort(list);

        mSessionsList.clear();
        mSessionsList.addAll(list);
        mSessionsAdapter.notifyDataSetChanged();
        
        if (mSessionsList.isEmpty()) {
            mTvEmptySessions.setVisibility(View.VISIBLE);
        }
    }

    private void showEmptySessionsState() {
        mSessionsList.clear();
        mSessionsAdapter.notifyDataSetChanged();
        mTvEmptySessions.setVisibility(View.VISIBLE);
    }

    // --- Session adapter sub-class ---

    private class SessionAdapter extends ArrayAdapter<String> {
        public SessionAdapter(Context context, List<String> sessions) {
            super(context, R.layout.item_directory, sessions);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_directory, parent, false);
            }
            String sessionId = getItem(position);
            TextView tvDirName = convertView.findViewById(R.id.tv_dir_name);
            View viewIndicator = convertView.findViewById(R.id.view_indicator);
            ImageView imgFolder = convertView.findViewById(R.id.img_folder);
            
            // Shorten display name
            String displayName = sessionId;
            if (sessionId.length() > 16) {
                displayName = sessionId.substring(0, 14) + "...";
            }
            tvDirName.setText(displayName);
            
            boolean isSelected = sessionId.equals(mSelectedSessionId);
            if (isSelected) {
                convertView.setBackgroundColor(Color.parseColor("#3300F2FE")); // Glassy Cyan Tint
                viewIndicator.setVisibility(View.VISIBLE);
                tvDirName.setTextColor(Color.parseColor("#00F2FE"));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    imgFolder.setImageResource(android.R.drawable.ic_menu_recent_history);
                    imgFolder.setImageTintList(ColorStateList.valueOf(Color.parseColor("#00F2FE")));
                }
            } else {
                convertView.setBackgroundColor(Color.TRANSPARENT);
                viewIndicator.setVisibility(View.GONE);
                tvDirName.setTextColor(Color.parseColor("#FFFFFF"));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    imgFolder.setImageResource(android.R.drawable.ic_menu_recent_history);
                    imgFolder.setImageTintList(ColorStateList.valueOf(Color.parseColor("#E6E6FA")));
                }
            }
            
            return convertView;
        }
    }

    // --- Query Termux Directories list via RUN_COMMAND ---

    private void queryTermuxDirectories() {
        mPbDrawerLoading.setVisibility(View.VISIBLE);
        mTvEmptyDirs.setVisibility(View.GONE);

        // Create PendingIntent for command result callback
        Intent callbackIntent = new Intent(ACTION_DIRECTORIES_LIST);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 999, callbackIntent, flags);

        // Build the command Intent targeting Termux's RunCommandService
        Intent runCommandIntent = new Intent();
        runCommandIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
        runCommandIntent.setAction("com.termux.RUN_COMMAND");

        // Executable path: find utility
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/find");

        // Arguments: list directories in home
        String[] arguments = new String[]{
                "/data/data/com.termux/files/home",
                "-maxdepth", "1",
                "-type", "d",
                "-not", "-name", ".*"
        };
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arguments);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
        
        // Set PendingIntent extra
        runCommandIntent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent);

        Log.d(TAG, "Querying Termux directories...");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(runCommandIntent);
            } else {
                startService(runCommandIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch directory query command", e);
            mPbDrawerLoading.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to query Termux environment", Toast.LENGTH_LONG).show();
        }
    }

    private void parseAndPopulateDirectories(String stdout) {
        String[] lines = stdout.split("\n");
        List<String> list = new ArrayList<>();
        
        // Always place Home at the top
        list.add("Home");

        String homePrefix = "/data/data/com.termux/files/home";

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.equals(homePrefix)) {
                continue;
            }
            
            // Extract folder name relative to home
            if (line.startsWith(homePrefix + "/")) {
                String relativeName = line.substring(homePrefix.length() + 1);
                if (!relativeName.isEmpty() && !list.contains(relativeName)) {
                    list.add(relativeName);
                }
            }
        }

        // Sort items (excluding 'Home' which stays first)
        if (list.size() > 1) {
            List<String> subdirs = list.subList(1, list.size());
            Collections.sort(subdirs);
        }

        mDirectoriesList.clear();
        mDirectoriesList.addAll(list);
        mDirectoryAdapter.notifyDataSetChanged();
        
        if (mDirectoriesList.size() <= 1) {
            mTvEmptyDirs.setVisibility(View.VISIBLE);
        }
    }

    private void showEmptyDirectoriesState() {
        mDirectoriesList.clear();
        mDirectoriesList.add("Home");
        mDirectoryAdapter.notifyDataSetChanged();
        mTvEmptyDirs.setVisibility(View.VISIBLE);
    }

    // --- App States Visual Management ---

    private void onStateChanged(String state, String text) {
        mCurrentState = state;
        mCbContinue.setChecked(mContinueSession);
        if (mCbMockMode != null) {
            mCbMockMode.setChecked(mBypassAntigravity);
        }

        clearAllAnimations();

        switch (state) {
            case "RECORDING":
                mTvStatus.setText("LISTENING...");
                mTvStatus.setTextColor(Color.parseColor("#FF2D55"));
                mBtnMic.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF2D55")));
                mBtnMic.setImageTintList(ColorStateList.valueOf(Color.parseColor("#FFFFFF")));
                mPbThinking.setVisibility(View.GONE);

                startPulseAnimation(mRingInner, 1200, 0);
                startPulseAnimation(mRingMiddle, 1200, 400);
                startPulseAnimation(mRingOuter, 1200, 800);
                break;

            case "THINKING":
                mTvStatus.setText("THINKING...");
                mTvStatus.setTextColor(Color.parseColor("#00F2FE"));
                mBtnMic.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0D1A2E")));
                mBtnMic.setImageTintList(ColorStateList.valueOf(Color.parseColor("#00F2FE")));
                mPbThinking.setVisibility(View.VISIBLE);

                if (!text.isEmpty() && !text.equals(mUserPrompt)) {
                    mUserPrompt = text;
                    addUserBubble(mUserPrompt);
                    addAgentBubble("...");
                } else if (mUserPrompt.isEmpty()) {
                    mUserPrompt = text.isEmpty() ? "Voice Command" : text;
                    addUserBubble(mUserPrompt);
                    addAgentBubble("...");
                }
                
                mRingInner.setScaleX(1.15f);
                mRingInner.setScaleY(1.15f);
                mRingInner.setAlpha(0.15f);
                mRingInner.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#00F2FE")));
                break;

            case "SPEAKING":
                mTvStatus.setText("SPEAKING...");
                mTvStatus.setTextColor(Color.parseColor("#4CD964"));
                mBtnMic.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CD964")));
                mBtnMic.setImageTintList(ColorStateList.valueOf(Color.parseColor("#FFFFFF")));
                mPbThinking.setVisibility(View.GONE);

                streamAgentResponse(text);

                mRingInner.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CD964")));
                mRingMiddle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CD964")));
                startBreatheAnimation(mRingInner, 1.0f, 1.25f, 0.2f, 0.6f, 1000);
                startBreatheAnimation(mRingMiddle, 1.0f, 1.4f, 0.1f, 0.4f, 1000);
                break;

            case "IDLE":
            default:
                mTvStatus.setText("IDLE");
                mTvStatus.setTextColor(Color.parseColor("#8A8A8F"));
                mBtnMic.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2D1F54")));
                mBtnMic.setImageTintList(ColorStateList.valueOf(Color.parseColor("#E6E6FA")));
                mPbThinking.setVisibility(View.GONE);

                mRingInner.setAlpha(0f);
                mRingMiddle.setAlpha(0f);
                mRingOuter.setAlpha(0f);
                
                if (mActiveAgentTextView != null && "...".equals(mActiveAgentTextView.getText().toString())) {
                    mActiveAgentTextView.setText("No response received.");
                }
                
                // Clear user prompt cache so same text can be sent again
                mUserPrompt = "";
                break;
        }

        mScrollLog.post(() -> mScrollLog.fullScroll(View.FOCUS_DOWN));
    }

    private void startPulseAnimation(final View view, long duration, long delay) {
        view.setAlpha(0f);
        view.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF2D55")));

        ScaleAnimation scaleAnim = new ScaleAnimation(
                1.0f, 2.2f, 1.0f, 2.2f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleAnim.setDuration(duration);
        scaleAnim.setRepeatCount(Animation.INFINITE);

        AlphaAnimation alphaAnim = new AlphaAnimation(0.4f, 0.0f);
        alphaAnim.setDuration(duration);
        alphaAnim.setRepeatCount(Animation.INFINITE);

        android.view.animation.AnimationSet set = new android.view.animation.AnimationSet(true);
        set.addAnimation(scaleAnim);
        set.addAnimation(alphaAnim);
        set.setStartOffset(delay);
        
        view.startAnimation(set);
        mRunningAnimations.add(new ViewAnimation(view, set));
    }

    private void startBreatheAnimation(View view, float startScale, float endScale, float startAlpha, float endAlpha, long duration) {
        ScaleAnimation scaleAnim = new ScaleAnimation(
                startScale, endScale, startScale, endScale,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleAnim.setDuration(duration);
        scaleAnim.setRepeatCount(Animation.INFINITE);
        scaleAnim.setRepeatMode(Animation.REVERSE);

        AlphaAnimation alphaAnim = new AlphaAnimation(startAlpha, endAlpha);
        alphaAnim.setDuration(duration);
        alphaAnim.setRepeatCount(Animation.INFINITE);
        alphaAnim.setRepeatMode(Animation.REVERSE);

        android.view.animation.AnimationSet set = new android.view.animation.AnimationSet(true);
        set.addAnimation(scaleAnim);
        set.addAnimation(alphaAnim);

        view.startAnimation(set);
        mRunningAnimations.add(new ViewAnimation(view, set));
    }

    private void clearAllAnimations() {
        for (ViewAnimation anim : mRunningAnimations) {
            anim.cancel();
        }
        mRunningAnimations.clear();
        
        mRingInner.setScaleX(1.0f);
        mRingInner.setScaleY(1.0f);
        mRingMiddle.setScaleX(1.0f);
        mRingMiddle.setScaleY(1.0f);
        mRingOuter.setScaleX(1.0f);
        mRingOuter.setScaleY(1.0f);
    }

    // --- Directory adapter sub-class ---

    private class DirectoryAdapter extends ArrayAdapter<String> {
        public DirectoryAdapter(Context context, List<String> dirs) {
            super(context, R.layout.item_directory, dirs);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_directory, parent, false);
            }
            String dirName = getItem(position);
            TextView tvDirName = convertView.findViewById(R.id.tv_dir_name);
            View viewIndicator = convertView.findViewById(R.id.view_indicator);
            ImageView imgFolder = convertView.findViewById(R.id.img_folder);
            
            tvDirName.setText(dirName);
            
            boolean isSelected = dirName.equals(mTargetDirectory);
            if (isSelected) {
                convertView.setBackgroundColor(Color.parseColor("#3300F2FE")); // Glassy Cyan Tint
                viewIndicator.setVisibility(View.VISIBLE);
                tvDirName.setTextColor(Color.parseColor("#00F2FE"));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    imgFolder.setImageTintList(ColorStateList.valueOf(Color.parseColor("#00F2FE")));
                }
            } else {
                convertView.setBackgroundColor(Color.TRANSPARENT);
                viewIndicator.setVisibility(View.GONE);
                tvDirName.setTextColor(Color.parseColor("#FFFFFF"));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    imgFolder.setImageTintList(ColorStateList.valueOf(Color.parseColor("#E6E6FA")));
                }
            }
            
            return convertView;
        }
    }
}
