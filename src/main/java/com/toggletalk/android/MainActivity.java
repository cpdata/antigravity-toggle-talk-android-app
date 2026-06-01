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
    private TextView mTvLog;
    private ScrollView mScrollLog;
    private CheckBox mCbContinue;
    private ImageButton mBtnMic;
    private ProgressBar mPbThinking;

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
        mTvLog = findViewById(R.id.tv_log);
        mScrollLog = findViewById(R.id.scroll_log);
        mCbContinue = findViewById(R.id.cb_continue);
        mBtnMic = findViewById(R.id.btn_mic);
        mPbThinking = findViewById(R.id.pb_thinking);

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
        mTvEmptyDirs = findViewById(R.id.tv_empty_dirs);
        mBtnMenu = findViewById(R.id.btn_menu);
        mBtnRefreshDirs = findViewById(R.id.btn_refresh_dirs);

        // Setup ListView Adapter
        mDirectoryAdapter = new DirectoryAdapter(this, mDirectoriesList);
        mLvDirectories.setAdapter(mDirectoryAdapter);

        // Setup Listeners
        mBtnMic.setOnClickListener(v -> toggleTalkSession());
        mBtnMenu.setOnClickListener(v -> openDrawer());
        mDrawerDimBackground.setOnClickListener(v -> closeDrawer());
        mBtnRefreshDirs.setOnClickListener(v -> queryTermuxDirectories());

        mLvDirectories.setOnItemClickListener((parent, view, position, id) -> {
            String selectedDir = mDirectoriesList.get(position);
            selectDirectory(selectedDir);
        });

        mCbContinue.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mContinueSession = isChecked;
            Intent intent = new Intent(MainActivity.this, ToggleTalkService.class);
            intent.setAction("com.toggletalk.android.ACTION_SET_CONTINUE");
            intent.putExtra("continue_session", isChecked);
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
        unregisterReceiver(mStateReceiver);
        unregisterReceiver(mDirectoriesReceiver);
        clearAllAnimations();
    }

    private void checkPermissionsAndPreferences() {
        if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            String warning = "⚠️ WARNING: 'Run commands in Termux' permission is not granted!\n\n" +
                    "To enable it:\n" +
                    "1. Open Android Settings -> Apps -> ToggleTalk\n" +
                    "2. Go to Permissions -> Additional permissions\n" +
                    "3. Select 'Run commands in Termux environment' and set to Allow.\n\n" +
                    "Also, ensure 'allow-external-apps=true' is uncommented in ~/.termux/termux.properties inside Termux.";
            mTvLog.setText(warning);
            mTvLog.setTextColor(Color.parseColor("#FFCC00"));
        } else {
            if ("Press the mic button to start speaking...".equals(mTvLog.getText().toString()) || mTvLog.getText().toString().startsWith("⚠️")) {
                mTvLog.setText("System ready. Press the mic button to speak.");
                mTvLog.setTextColor(Color.parseColor("#D1D1D6"));
            }
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

        switch (ev.getAction()) {
            case android.view.MotionEvent.ACTION_DOWN:
                mTouchStartX = ev.getX();
                mTouchStartY = ev.getY();
                mIsDraggingDrawer = false;
                if (mIsDrawerOpen) {
                    mInitialTranslationX = mDrawerContent.getTranslationX();
                } else {
                    mInitialTranslationX = -drawerWidth;
                }
                break;

            case android.view.MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - mTouchStartX;
                float dy = ev.getY() - mTouchStartY;

                if (!mIsDraggingDrawer) {
                    // Check if we should start dragging
                    if (!mIsDrawerOpen) {
                        // Closed: touch must start near the left edge (within 50dp), and move right
                        if (mTouchStartX < 50 * density && dx > 15 * density && Math.abs(dx) > Math.abs(dy) * 1.5) {
                            mIsDraggingDrawer = true;
                            mDrawerRoot.setVisibility(View.VISIBLE);
                            queryTermuxDirectories(); // Start loading directories
                            cancelChildTouches(ev);
                        }
                    } else {
                        // Open: touch can start anywhere, and move left (closing gesture)
                        if (dx < -15 * density && Math.abs(dx) > Math.abs(dy) * 1.5) {
                            mIsDraggingDrawer = true;
                            cancelChildTouches(ev);
                        }
                    }
                }

                if (mIsDraggingDrawer) {
                    float newTranslationX = mInitialTranslationX + dx;
                    if (newTranslationX > 0f) newTranslationX = 0f;
                    if (newTranslationX < -drawerWidth) newTranslationX = -drawerWidth;

                    mDrawerContent.setTranslationX(newTranslationX);
                    
                    // Alpha of dim background from 0 to 1
                    float progress = (drawerWidth + newTranslationX) / drawerWidth;
                    mDrawerDimBackground.setAlpha(progress);
                    return true; // Consume event during drag
                }
                break;

            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL:
                if (mIsDraggingDrawer) {
                    mIsDraggingDrawer = false;
                    float currentTranslationX = mDrawerContent.getTranslationX();
                    if (currentTranslationX > -drawerWidth * 0.5f) {
                        // Open drawer
                        animateDrawer(currentTranslationX, 0f, 1.0f, true);
                    } else {
                        // Close drawer
                        animateDrawer(currentTranslationX, -drawerWidth, 0f, false);
                    }
                    return true; // Consume event
                }
                break;
        }

        // If dragging, intercept
        if (mIsDraggingDrawer) {
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

    private void updateActiveDirLabel() {
        mTvActiveDir.setText("Active: " + mTargetDirectory);
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

        clearAllAnimations();

        switch (state) {
            case "RECORDING":
                mTvStatus.setText("LISTENING...");
                mTvStatus.setTextColor(Color.parseColor("#FF2D55"));
                mBtnMic.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF2D55")));
                mBtnMic.setImageTintList(ColorStateList.valueOf(Color.parseColor("#FFFFFF")));
                mPbThinking.setVisibility(View.GONE);

                mTvLog.setText("Listening...");
                mTvLog.setTextColor(Color.parseColor("#FF2D55"));

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

                if (!text.isEmpty()) {
                    mUserPrompt = text;
                }
                String thinkText = "User: \"" + mUserPrompt + "\"\n\nAntigravity is thinking...";
                mTvLog.setText(thinkText);
                mTvLog.setTextColor(Color.parseColor("#00F2FE"));
                
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

                String speakText = "User: \"" + mUserPrompt + "\"\n\nAntigravity: " + text;
                mTvLog.setText(speakText);
                mTvLog.setTextColor(Color.parseColor("#FFFFFF"));

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
                
                if (mTvLog.getText().toString().equals("Listening...") || mTvLog.getText().toString().contains("thinking...")) {
                    mTvLog.setText("System ready. Press the mic button to speak.");
                    mTvLog.setTextColor(Color.parseColor("#D1D1D6"));
                }
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
