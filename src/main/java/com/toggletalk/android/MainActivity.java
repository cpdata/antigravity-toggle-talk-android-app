package com.toggletalk.android;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
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

import android.widget.RadioGroup;
import android.widget.RadioButton;
import com.toggletalk.android.agent.AgentCLI;
import com.toggletalk.android.agent.AgentManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity implements PromptQueueView.OnPromptActionListener {
    private static final String TAG = "MainActivity";
    private static final String ACTION_DIRECTORIES_LIST = "com.toggletalk.android.ACTION_DIRECTORIES_LIST";
    private static final String ACTION_COPY_ARTIFACT = "com.toggletalk.android.ACTION_COPY_ARTIFACT";

    private TextView mTvStatus;
    private ScrollView mScrollLog;
    private android.widget.LinearLayout mChatContainer;
    private TextView mActiveAgentTextView;
    private TextView mBtnNewChat;
    private TextView mBtnNewChatDrawer;
    private TextView mTvActiveSessionTop;
    private CheckBox mCbMockMode;
    private CheckBox mCbWakeLock;
    private CheckBox mCbCollapseTools;
    private CheckBox mCbCollapseThoughts;
    private RadioGroup mRgAgentSelection;
    private RadioButton mRbAgentAntigravity;
    private RadioButton mRbAgentGemini;
    private boolean mWakeLockEnabled = false;
    private boolean mCollapseTools = false;
    private boolean mCollapseThoughts = false;

    private PromptQueueView mPromptQueueView;
    private final List<String> mPromptQueue = new ArrayList<>();
    private boolean mIsQueueExpanded = false;

    private PromptEditPopup mPromptEditPopup;
    private QuickPromptsPopup mQuickPromptsPopup;
    private UnresolvedQueueManager mUnresolvedQueueManager;
    private int mEditingPromptIndex = -1;

    // Artifacts popup views
    private View mArtifactsPopupRoot;
    private View mBtnArtifactsClose;
    private View mArtifactsDimBackground;
    private android.widget.LinearLayout mLayoutArtifactsList;
    private ImageButton mBtnArtifacts;
    private View mBtnFlush;

    private View mSettingsPopupRoot;
    private TextView mBtnSettings;
    private TextView mBtnExpandAll;
    private View mBtnSettingsClose;
    private View mSettingsDimBackground;
    private View mBtnClearSessionCache;
    private View mBtnKillAll;
    private boolean mBypassAntigravity = false;
    private ImageButton mBtnMic;
    private ProgressBar mPbThinking;
    private View mLayoutThinkingContainer;
    private View mBtnTerminate;
    private View mBtnAddOptions;
    private View mAddOptionsDrawerRoot;
    private View mAddOptionsDimBackground;
    private View mBtnAddOptionsClose;
    private View mOptionFiles;
    private View mOptionCamera;
    private View mOptionQuickPrompts;
    private final java.util.Map<Integer, android.text.Spanned> mRenderedTextCache = new java.util.HashMap<>();
    private final java.util.Map<Integer, String> mCachedRawTexts = new java.util.HashMap<>();
    private final java.util.Set<Integer> mCollapsedIndices = new java.util.HashSet<>();
    private final java.util.Set<Integer> mExpandedSummaryIndices = new java.util.HashSet<>();
    private android.widget.EditText mEtMessage;
    private ImageButton mBtnSend;
    private boolean mIsAgentActive = false; // true when THINKING or SPEAKING

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
    private boolean mIsResuming = false;

    private ImageButton mBtnScrollBottom;
    private ImageButton mBtnSearch;
    private View mSearchPopupRoot;
    private android.widget.EditText mEtSearchPopup;
    private android.widget.ListView mLvSearchResults;
    private View mSearchDimBackground;
    private View mBtnSearchClose;
    private List<SearchResult> mCurrentSearchResults = new ArrayList<>();

    private ToggleTalkMainHelper mHelper;
    private android.widget.ViewFlipper mViewFlipper;
    private View mSharedFooter;
    private View mBtnFooterMenu;
    private View mBottomDrawerRoot;
    private View mBottomDrawerDim;
    private View mBtnDrawerHome;
    private View mBtnDrawerChat;
    private View mBtnDrawerCommands;
    
    // Commands view
    private android.widget.EditText mEtNewCommand;
    private ImageButton mBtnAddCommand;
    private ListView mLvCommands;
    private List<String> mCommandsList;
    private ArrayAdapter<String> mCommandsAdapter;

    private static class SearchResult {
        String text;
        View view;
        SearchResult(String text, View view) {
            this.text = text;
            this.view = view;
        }
        @Override
        public String toString() {
            String preview = text.replace("\n", " ").trim();
            if (preview.length() > 60) preview = preview.substring(0, 57) + "...";
            return preview;
        }
    }
    
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
    
    private static class SessionItem {
        final String id;
        final String title;
        
        SessionItem(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    private static class ArtifactItem {
        final String label;
        final String url;
        
        ArtifactItem(String label, String url) {
            this.label = label;
            this.url = url;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ArtifactItem)) return false;
            ArtifactItem that = (ArtifactItem) o;
            return url.equals(that.url);
        }
        
        @Override
        public int hashCode() {
            return url.hashCode();
        }
    }
    
    private static class CollapsibleBubbleHolder {
        final android.widget.LinearLayout layout;
        final TextView headerTv;
        final TextView contentTv;
        final String headerText;
        final int index;
        boolean expanded = false;
        CharSequence fullContent = "";
        CharSequence collapsedContent = null;

        CollapsibleBubbleHolder(android.widget.LinearLayout layout, TextView headerTv, TextView contentTv, String headerText, int index) {
            this.layout = layout;
            this.headerTv = headerTv;
            this.contentTv = contentTv;
            this.headerText = headerText;
            this.index = index;
        }

        void setExpanded(boolean exp) {
            this.expanded = exp;
            if (exp) {
                contentTv.setText(fullContent);
                contentTv.setMaxLines(Integer.MAX_VALUE);
                contentTv.setEllipsize(null);
                contentTv.setVisibility(View.VISIBLE);
            } else {
                if (collapsedContent != null) {
                    contentTv.setText(collapsedContent);
                    contentTv.setMaxLines(Integer.MAX_VALUE);
                    contentTv.setEllipsize(null);
                } else {
                    contentTv.setText(fullContent);
                    contentTv.setMaxLines(1);
                    contentTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                }
                // Hide if content is empty (e.g. tool_result header only)
                contentTv.setVisibility(contentTv.getText().length() > 0 ? View.VISIBLE : View.GONE);
            }
            headerTv.setText((exp ? "▼ " : "▶ ") + headerText);
        }
    }
    
    private final List<SessionItem> mSessionsList = new ArrayList<>();
    private final List<CollapsibleBubbleHolder> mCollapsibleBubbleHolders = new ArrayList<>();
    private final java.util.Set<Integer> mExpandedIndices = new java.util.HashSet<>();
    private ArrayAdapter<SessionItem> mSessionsAdapter;

    private static final String ACTION_SESSIONS_LIST = "com.toggletalk.android.ACTION_SESSIONS_LIST";
    private static final String ACTION_SESSION_HISTORY = "com.toggletalk.android.ACTION_SESSION_HISTORY";

    // Gesture tracking variables for drawer edge swipe
    private float mTouchStartX = 0f;
    private float mTouchStartY = 0f;
    private boolean mIsDraggingDrawer = false;
    private float mInitialTranslationX = 0f;

    // Animation list
    private final List<ViewAnimation> mRunningAnimations = new ArrayList<>();

    private final java.util.Set<String> mDisplayedStepKeys = new java.util.HashSet<>();
    private boolean mShowThoughtsAndToolCalls = false;
    private boolean mShowAllEarlierMessages = false;
    private org.json.JSONArray mCurrentSessionHistory = new org.json.JSONArray();

    private final android.os.Handler mStreamHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private String mBufferedSessionId = "";
    private String mBufferedMessagesJson = "";
    private String mBufferedFilePath = "";
    private boolean mStreamUpdatePending = false;
    private long mLastStreamUpdateTime = 0;

    private final Runnable mStreamUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            mStreamUpdatePending = false;
            mLastStreamUpdateTime = System.currentTimeMillis();
            performStreamedDisplay(mBufferedSessionId, mBufferedMessagesJson, mBufferedFilePath);
        }
    };
    private View mNewChatStartedBubble = null;
    private String mLastStreamedAgentText = "";

    private final BroadcastReceiver mStreamDisplayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.toggletalk.android.ACTION_STREAM_DISPLAY".equals(action)) {
                String sessionId = intent.getStringExtra("session_id");
                String messagesJson = intent.getStringExtra("messages_json");
                String filePath = intent.getStringExtra("file_path");
                handleStreamedDisplay(sessionId, messagesJson, filePath);
            } else if ("com.toggletalk.android.ACTION_NEW_SESSION_ADOPTED".equals(action)) {
                String sessionId = intent.getStringExtra("session_id");
                if (sessionId != null && !sessionId.isEmpty()) {
                    mSelectedSessionId = sessionId;
                    mContinueSession = true;
                    getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putString("selected_session_id", sessionId).apply();
                    updateActiveSessionLabel();
                    queryTermuxSessions();
                }
            } else if ("com.toggletalk.android.ACTION_REFRESH_HISTORY".equals(action)) {
                if (mSelectedSessionId != null && !mSelectedSessionId.isEmpty()) {
                    loadSessionHistory(mSelectedSessionId);
                }
            }
        }
    };

    private final BroadcastReceiver mQueueReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ToggleTalkService.ACTION_QUEUE_CHANGED.equals(intent.getAction())) {
                ArrayList<String> queue = intent.getStringArrayListExtra("queue");
                Log.d(TAG, "QueueChanged: current state=" + mCurrentState + ", queue size=" + (queue != null ? queue.size() : 0));
                if (queue != null) {
                    mPromptQueue.clear();
                    mPromptQueue.addAll(queue);
                    if (mPromptQueueView != null) {
                        mPromptQueueView.render(mPromptQueue, mIsQueueExpanded);
                    }
                    if (mUnresolvedQueueManager != null) {
                        mUnresolvedQueueManager.onStateOrQueueChanged(mCurrentState, mPromptQueue);
                    }
                    
                    // CRITICAL: Re-enforce thinking container visibility if state is THINKING or SPEAKING
                    if ("THINKING".equals(mCurrentState) || "SPEAKING".equals(mCurrentState)) {
                        if (mLayoutThinkingContainer != null && mTvStatus != null && !"TRANSCRIBING...".equals(mTvStatus.getText())) {
                            Log.d(TAG, "QueueChanged: " + mCurrentState + " state detected, re-showing thinking container");
                            mLayoutThinkingContainer.setVisibility(View.VISIBLE);
                            mLayoutThinkingContainer.bringToFront();
                        }
                    }
                }
            }
        }
    };

    // Broadcast Receiver for App State Updates
    private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ToggleTalkService.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                String state = intent.getStringExtra(ToggleTalkService.EXTRA_STATE);
                String text = intent.getStringExtra(ToggleTalkService.EXTRA_TEXT);
                // Only accept continue_session from service if no session is selected locally
                if (mSelectedSessionId == null || mSelectedSessionId.isEmpty()) {
                    mContinueSession = intent.getBooleanExtra("continue_session", false);
                }
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
                
                boolean ttsQueueEmpty = intent.getBooleanExtra("tts_queue_empty", true);
                if (mBtnFlush != null) {
                    mBtnFlush.setVisibility(ttsQueueEmpty ? View.GONE : View.VISIBLE);
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

    // Broadcast Receiver for Termux Command Output (Session History)
    private final BroadcastReceiver mSessionHistoryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SESSION_HISTORY.equals(intent.getAction())) {
                Log.d(TAG, "Received session history callback from Termux");
                Bundle resultBundle = intent.getBundleExtra("result");
                String stdout = null;
                String errmsg = null;
                if (resultBundle != null) {
                    stdout = resultBundle.getString("stdout");
                    errmsg = resultBundle.getString("errmsg");
                }
                String sessionId = intent.getStringExtra("session_id");
                Log.d(TAG, "Session history stdout len=" + (stdout == null ? "null" : stdout.length()) + " errmsg=" + errmsg);
                receiveSessionHistory(sessionId, stdout, errmsg);
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
        mBtnNewChat = findViewById(R.id.btn_new_chat);
        mBtnNewChatDrawer = findViewById(R.id.btn_new_chat_drawer);
        mTvActiveSessionTop = findViewById(R.id.tv_active_session_top);
        mCbMockMode = findViewById(R.id.cb_mock_mode);
        mSettingsPopupRoot = findViewById(R.id.settings_popup_root);
        mBtnSettings = findViewById(R.id.btn_settings);
        mBtnExpandAll = findViewById(R.id.btn_expand_all);
        if (mBtnExpandAll != null) {
            mBtnExpandAll.setOnClickListener(v -> {
                boolean anyExpanded = mShowThoughtsAndToolCalls || !mExpandedSummaryIndices.isEmpty() || !mExpandedIndices.isEmpty();
                if (anyExpanded) {
                    mShowThoughtsAndToolCalls = false;
                    mExpandedIndices.clear();
                    mExpandedSummaryIndices.clear();
                } else {
                    mShowThoughtsAndToolCalls = true;
                }
                updateBtnExpandAllText();
                displayMessages(mCurrentSessionHistory, mShowAllEarlierMessages);
            });
        }
        mBtnSettingsClose = findViewById(R.id.btn_settings_close);
        mSettingsDimBackground = findViewById(R.id.settings_popup_dim_background);
        mBtnClearSessionCache = findViewById(R.id.btn_clear_session_cache);
        mBtnKillAll = findViewById(R.id.btn_kill_all);

        mCbWakeLock = findViewById(R.id.cb_wake_lock);
        mArtifactsPopupRoot = findViewById(R.id.artifacts_popup_root);
        mBtnArtifactsClose = findViewById(R.id.btn_artifacts_close);
        mArtifactsDimBackground = findViewById(R.id.artifacts_popup_dim_background);
        mLayoutArtifactsList = findViewById(R.id.layout_artifacts_list);
        mBtnArtifacts = findViewById(R.id.btn_artifacts);

        // Bypass strict mode file URI exposure detection
        try {
            java.lang.reflect.Method m = Class.forName("android.os.StrictMode")
                    .getMethod("disableDeathOnFileUriExposure");
            m.invoke(null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to disable strict mode check", e);
        }

        if (mBtnArtifacts != null) {
            mBtnArtifacts.setOnClickListener(v -> openArtifactsPopup());
        }
        if (mBtnArtifactsClose != null) {
            mBtnArtifactsClose.setOnClickListener(v -> closeArtifactsPopup());
        }
        if (mArtifactsDimBackground != null) {
            mArtifactsDimBackground.setOnClickListener(v -> closeArtifactsPopup());
        }

        if (mBtnSettings != null) {
            mBtnSettings.setOnClickListener(v -> openSettings());
        }
        if (mBtnSettingsClose != null) {
            mBtnSettingsClose.setOnClickListener(v -> closeSettings());
        }
        if (mSettingsDimBackground != null) {
            mSettingsDimBackground.setOnClickListener(v -> closeSettings());
        }
        if (mBtnClearSessionCache != null) {
            mBtnClearSessionCache.setOnClickListener(v -> {
                clearSessionHistoryCache();
                closeSettings();
            });
        }
        if (mBtnKillAll != null) {
            mBtnKillAll.setOnClickListener(v -> killAllSessions());
        }
        mBtnMic = findViewById(R.id.btn_mic);
        mPbThinking = findViewById(R.id.pb_thinking);
        mLayoutThinkingContainer = findViewById(R.id.layout_thinking_container);
        mBtnTerminate = findViewById(R.id.btn_terminate);
        if (mBtnTerminate != null) {
            mBtnTerminate.setOnClickListener(v -> {
                Log.d("MainActivity", "Terminate button clicked for session: " + mSelectedSessionId);
                Toast.makeText(MainActivity.this, "Terminating session...", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MainActivity.this, ToggleTalkService.class);
                intent.setAction("com.toggletalk.android.ACTION_TERMINATE_SESSION");
                intent.putExtra("session_id", mSelectedSessionId);
                startService(intent);
            });
        }
        
        mBtnAddOptions = findViewById(R.id.btn_add_options);
        mAddOptionsDrawerRoot = findViewById(R.id.add_options_drawer_root);
        mAddOptionsDimBackground = findViewById(R.id.add_options_dim_background);
        mBtnAddOptionsClose = findViewById(R.id.btn_add_options_close);
        mOptionFiles = findViewById(R.id.option_files);
        mOptionCamera = findViewById(R.id.option_camera);
        mOptionQuickPrompts = findViewById(R.id.option_quick_prompts);

        if (mBtnAddOptions != null) {
            mBtnAddOptions.setOnClickListener(v -> {
                if (mAddOptionsDrawerRoot != null) {
                    mAddOptionsDrawerRoot.setVisibility(View.VISIBLE);
                }
            });
        }
        if (mBtnAddOptionsClose != null) {
            mBtnAddOptionsClose.setOnClickListener(v -> {
                if (mAddOptionsDrawerRoot != null) mAddOptionsDrawerRoot.setVisibility(View.GONE);
            });
        }
        if (mAddOptionsDimBackground != null) {
            mAddOptionsDimBackground.setOnClickListener(v -> {
                if (mAddOptionsDrawerRoot != null) mAddOptionsDrawerRoot.setVisibility(View.GONE);
            });
        }
        if (mOptionFiles != null) {
            mOptionFiles.setOnClickListener(v -> {
                if (mAddOptionsDrawerRoot != null) mAddOptionsDrawerRoot.setVisibility(View.GONE);
                openFilePicker();
            });
        }
        if (mOptionCamera != null) {
            mOptionCamera.setOnClickListener(v -> {
                if (mAddOptionsDrawerRoot != null) mAddOptionsDrawerRoot.setVisibility(View.GONE);
                openCameraPicker();
            });
        }
        if (mOptionQuickPrompts != null) {
            mOptionQuickPrompts.setOnClickListener(v -> {
                if (mAddOptionsDrawerRoot != null) mAddOptionsDrawerRoot.setVisibility(View.GONE);
                if (mQuickPromptsPopup != null) {
                    mQuickPromptsPopup.show();
                }
            });
        }
        
        mEtMessage = findViewById(R.id.et_message);
        mBtnSend = findViewById(R.id.btn_send);
        mBtnSend.setOnClickListener(v -> {
            sendMessage();
        });
        mEtMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        View btnFlush = findViewById(R.id.btn_flush);
        mBtnFlush = btnFlush;
        if (btnFlush != null) {
            btnFlush.setOnClickListener(v -> {
                Intent intent = new Intent("com.toggletalk.android.ACTION_TERMINATE_TTS");
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
            });
        }

        mRingInner = findViewById(R.id.ring_inner);
        mRingMiddle = findViewById(R.id.ring_middle);
        mRingOuter = findViewById(R.id.ring_outer);

        // Initialize Prompt Queue View
        View queueContainer = findViewById(R.id.layout_queue_container);
        if (queueContainer != null) {
            mPromptQueueView = new PromptQueueView(queueContainer, this);
            View root = findViewById(android.R.id.content);
            mUnresolvedQueueManager = new UnresolvedQueueManager(this, root, mBtnSend, mEtMessage, queueContainer);
        }

        // Scroll to Bottom Button
        mBtnScrollBottom = findViewById(R.id.btn_scroll_bottom);
        if (mBtnScrollBottom != null) {
            mBtnScrollBottom.setOnClickListener(v -> scrollToBottom());
            if (mScrollLog != null) {
                mScrollLog.getViewTreeObserver().addOnScrollChangedListener(() -> {
                    if (isScrolledToBottom()) {
                        mBtnScrollBottom.setVisibility(View.GONE);
                    } else {
                        mBtnScrollBottom.setVisibility(View.VISIBLE);
                    }
                });
            }
        }

        // Search Components
        mBtnSearch = findViewById(R.id.btn_search);
        mSearchPopupRoot = findViewById(R.id.search_popup_root);
        mEtSearchPopup = findViewById(R.id.et_search_popup);
        mLvSearchResults = findViewById(R.id.lv_search_results);
        mSearchDimBackground = findViewById(R.id.search_popup_dim_background);
        mBtnSearchClose = findViewById(R.id.btn_search_close);

        if (mBtnSearch != null && mSearchPopupRoot != null) {
            mBtnSearch.setOnClickListener(v -> {
                mSearchPopupRoot.setVisibility(View.VISIBLE);
                if (mEtSearchPopup != null) {
                    mEtSearchPopup.requestFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(mEtSearchPopup, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });

            if (mSearchDimBackground != null) {
                mSearchDimBackground.setOnClickListener(v -> mSearchPopupRoot.setVisibility(View.GONE));
            }
            if (mBtnSearchClose != null) {
                mBtnSearchClose.setOnClickListener(v -> mSearchPopupRoot.setVisibility(View.GONE));
            }

            if (mEtSearchPopup != null) {
                mEtSearchPopup.addTextChangedListener(new android.text.TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        performSearch(s.toString());
                    }
                    @Override public void afterTextChanged(android.text.Editable s) {}
                });

                mEtSearchPopup.setOnEditorActionListener((v, actionId, event) -> {
                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                        performSearch(mEtSearchPopup.getText().toString());
                        return true;
                    }
                    return false;
                });
            }

            if (mLvSearchResults != null) {
                mLvSearchResults.setOnItemClickListener((parent, view, position, id) -> {
                    SearchResult result = mCurrentSearchResults.get(position);
                    scrollToView(result.view);
                    mSearchPopupRoot.setVisibility(View.GONE);
                });
            }
        }

        // Initialize Prompt Edit Popup
        View editPopupRoot = findViewById(R.id.prompt_edit_popup_root);
        if (editPopupRoot != null) {
            mPromptEditPopup = new PromptEditPopup(editPopupRoot, new PromptEditPopup.OnEditActionListener() {
                @Override
                public void onUpdate(String text) {
                    if (mEditingPromptIndex != -1) {
                        Intent intent = new Intent(MainActivity.this, ToggleTalkService.class);
                        intent.setAction("com.toggletalk.android.ACTION_UPDATE_PROMPT");
                        intent.putExtra("index", mEditingPromptIndex);
                        intent.putExtra("text", text);
                        startService(intent);
                    }
                }

                @Override
                public void onDelete() {
                    if (mEditingPromptIndex != -1) {
                        Intent intent = new Intent(MainActivity.this, ToggleTalkService.class);
                        intent.setAction("com.toggletalk.android.ACTION_DELETE_PROMPT");
                        intent.putExtra("index", mEditingPromptIndex);
                        startService(intent);
                    }
                }

                @Override
                public void onSend(String text) {
                    // Send the combined prompt
                    Intent intent = new Intent(MainActivity.this, ToggleTalkService.class);
                    intent.setAction("com.toggletalk.android.ACTION_SEND_PROMPT");
                    intent.putExtra("prompt", text);
                    intent.putExtra("continue_session", true);
                    intent.putExtra("session_id", mSelectedSessionId);
                    intent.putExtra("bypass_antigravity", mBypassAntigravity);
                    startService(intent);
                    
                    // Clear the queue since they were combined
                    Intent clearIntent = new Intent(MainActivity.this, ToggleTalkService.class);
                    clearIntent.setAction("com.toggletalk.android.ACTION_CLEAR_QUEUE");
                    startService(clearIntent);
                    
                    // Clear the main input text
                    if (mEtMessage != null) {
                        mEtMessage.setText("");
                    }
                    
                    // Clear unresolved state
                    if (mUnresolvedQueueManager != null) {
                        mUnresolvedQueueManager.clearUnresolvedState();
                    }
                }

                @Override
                public void onCancel() {
                    mEditingPromptIndex = -1;
                }
            });
        }

        // Initialize Quick Prompts Popup
        View quickPromptsRoot = findViewById(R.id.quick_prompts_popup_root);
        if (quickPromptsRoot != null) {
            mQuickPromptsPopup = new QuickPromptsPopup(quickPromptsRoot, prompt -> {
                if (mEtMessage != null) {
                    String current = mEtMessage.getText().toString();
                    if (!current.isEmpty() && !current.endsWith(" ")) {
                        current += " ";
                    }
                    mEtMessage.setText(current + prompt);
                    mEtMessage.setSelection(mEtMessage.getText().length());
                }
            });
        }

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
            SessionItem selectedSession = mSessionsList.get(position);
            selectSession(selectedSession.id);
        });

        // New Chat buttons (bottom bar and drawer)
        mBtnNewChat.setOnClickListener(v -> startNewChat());
        if (mBtnNewChatDrawer != null) {
            mBtnNewChatDrawer.setOnClickListener(v -> {
                startNewChat();
                closeRightDrawer();
            });
        }

        // Load active session from preferences
        mSelectedSessionId = getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).getString("selected_session_id", "");
        mContinueSession = (mSelectedSessionId != null && !mSelectedSessionId.isEmpty());
        updateActiveSessionLabel();

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

        // Load and setup WakeLock settings
        mWakeLockEnabled = getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).getBoolean("wake_lock_enabled", false);
        if (mCbWakeLock != null) {
            mCbWakeLock.setChecked(mWakeLockEnabled);
            mCbWakeLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
                mWakeLockEnabled = isChecked;
                getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putBoolean("wake_lock_enabled", isChecked).apply();
                applyWakeLockSetting(isChecked);
            });
        }
        applyWakeLockSetting(mWakeLockEnabled);

        // Load and setup Collapse Tools settings
        mCollapseTools = getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).getBoolean("collapse_tools", false);
        mCbCollapseTools = findViewById(R.id.cb_collapse_tools);
        if (mCbCollapseTools != null) {
            mCbCollapseTools.setChecked(mCollapseTools);
            mCbCollapseTools.setOnCheckedChangeListener((buttonView, isChecked) -> {
                mCollapseTools = isChecked;
                getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putBoolean("collapse_tools", isChecked).apply();
                displayMessages(mCurrentSessionHistory, mShowAllEarlierMessages);
            });
        }

        // Load and setup Collapse Thoughts settings
        mCollapseThoughts = getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).getBoolean("collapse_thoughts", false);
        mCbCollapseThoughts = findViewById(R.id.cb_collapse_thoughts);
        if (mCbCollapseThoughts != null) {
            mCbCollapseThoughts.setChecked(mCollapseThoughts);
            mCbCollapseThoughts.setOnCheckedChangeListener((buttonView, isChecked) -> {
                mCollapseThoughts = isChecked;
                getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putBoolean("collapse_thoughts", isChecked).apply();
                displayMessages(mCurrentSessionHistory, mShowAllEarlierMessages);
            });
        }

        mRgAgentSelection = findViewById(R.id.rg_agent_selection);
        mRbAgentAntigravity = findViewById(R.id.rb_agent_antigravity);
        mRbAgentGemini = findViewById(R.id.rb_agent_gemini);
        setupAgentSelection();

        // Initialize multi-view architecture
        mHelper = new ToggleTalkMainHelper(this);
        mViewFlipper = findViewById(R.id.view_flipper);
        mSharedFooter = findViewById(R.id.layout_shared_footer);
        mBtnFooterMenu = findViewById(R.id.btn_footer_menu);
        mBottomDrawerRoot = findViewById(R.id.bottom_drawer_root);
        mBottomDrawerDim = findViewById(R.id.bottom_drawer_dim_background);
        mBtnDrawerHome = findViewById(R.id.btn_drawer_home);
        mBtnDrawerChat = findViewById(R.id.btn_drawer_chat);
        mBtnDrawerCommands = findViewById(R.id.btn_drawer_commands);

        if (mBtnFooterMenu != null) {
            mBtnFooterMenu.setOnClickListener(v -> openBottomDrawer());
        }
        if (mBottomDrawerDim != null) {
            mBottomDrawerDim.setOnClickListener(v -> closeBottomDrawer());
        }
        if (mBtnDrawerHome != null) {
            mBtnDrawerHome.setOnClickListener(v -> {
                showView(0);
                closeBottomDrawer();
            });
        }
        if (mBtnDrawerChat != null) {
            mBtnDrawerChat.setOnClickListener(v -> {
                showView(1);
                closeBottomDrawer();
            });
        }
        if (mBtnDrawerCommands != null) {
            mBtnDrawerCommands.setOnClickListener(v -> {
                showView(2);
                closeBottomDrawer();
            });
        }

        initMenuView();
        initCommandsView();
        
        // Initialize state visually
        onStateChanged("IDLE", "");
        
        // Default to Menu view
        showView(0);

        // Load previously selected session history on startup
        if (mSelectedSessionId != null && !mSelectedSessionId.isEmpty()) {
            loadSessionHistory(mSelectedSessionId);
        }
    }

    private void setupAgentSelection() {
        if (mRgAgentSelection == null) return;

        AgentManager agentManager = AgentManager.getInstance(this);
        String activeAgentId = agentManager.getActiveAgent().getId();
        
        if ("gemini".equals(activeAgentId)) {
            mRbAgentGemini.setChecked(true);
        } else {
            mRbAgentAntigravity.setChecked(true);
        }

        mRgAgentSelection.setOnCheckedChangeListener((group, checkedId) -> {
            String newAgentId = (checkedId == R.id.rb_agent_gemini) ? "gemini" : "antigravity";
            agentManager.setActiveAgent(this, newAgentId);
            Log.d(TAG, "Switched active agent to: " + newAgentId);
            
            // Broadcast to service that agent changed
            Intent intent = new Intent(this, ToggleTalkService.class);
            intent.setAction("com.toggletalk.android.ACTION_AGENT_CHANGED");
            intent.putExtra("active_agent_id", newAgentId);
            startService(intent);
            
            // Refresh sessions list for the new agent
            queryTermuxSessions();
        });
    }

    private void showView(int index) {
        if (mViewFlipper != null) {
            mViewFlipper.setDisplayedChild(index);
        }
        if (mSharedFooter != null) {
            mSharedFooter.setVisibility(index == 0 ? View.GONE : View.VISIBLE);
        }
    }

    private void openBottomDrawer() {
        if (mBottomDrawerRoot != null) {
            mBottomDrawerRoot.setVisibility(View.VISIBLE);
        }
    }

    private void closeBottomDrawer() {
        if (mBottomDrawerRoot != null) {
            mBottomDrawerRoot.setVisibility(View.GONE);
        }
    }

    private void initMenuView() {
        View btnChat = findViewById(R.id.btn_menu_chat);
        View btnCmds = findViewById(R.id.btn_menu_commands);
        if (btnChat != null) btnChat.setOnClickListener(v -> showView(1));
        if (btnCmds != null) btnCmds.setOnClickListener(v -> showView(2));
        updateMenuLayout();
    }

    private void updateMenuLayout() {
        android.widget.LinearLayout container = findViewById(R.id.layout_menu_container);
        if (container == null) return;
        boolean isLandscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        container.setOrientation(isLandscape ? android.widget.LinearLayout.HORIZONTAL : android.widget.LinearLayout.VERTICAL);
        
        // Adjust child weights for landscape
        View stats = findViewById(R.id.layout_stats_section);
        View grid = findViewById(R.id.layout_grid_section);
        if (stats != null && grid != null) {
            android.widget.LinearLayout.LayoutParams statsLp = (android.widget.LinearLayout.LayoutParams) stats.getLayoutParams();
            android.widget.LinearLayout.LayoutParams gridLp = (android.widget.LinearLayout.LayoutParams) grid.getLayoutParams();
            if (isLandscape) {
                statsLp.width = 0;
                statsLp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                statsLp.setMargins(0, 0, 8, 0);
                gridLp.width = 0;
                gridLp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            } else {
                statsLp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                statsLp.height = 0;
                statsLp.setMargins(0, 0, 0, 8);
                gridLp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                gridLp.height = 0;
            }
            stats.setLayoutParams(statsLp);
            grid.setLayoutParams(gridLp);
        }
    }

    private void initCommandsView() {
        mEtNewCommand = findViewById(R.id.et_new_command);
        mBtnAddCommand = findViewById(R.id.btn_add_command);
        mLvCommands = findViewById(R.id.lv_commands);
        if (mLvCommands != null) {
            mCommandsList = mHelper.getSavedCommands();
            mCommandsAdapter = new android.widget.ArrayAdapter<String>(this, R.layout.item_command, R.id.tv_command_text, mCommandsList);
            mLvCommands.setAdapter(mCommandsAdapter);
            
            mLvCommands.setOnItemClickListener((parent, view, position, id) -> {
                String cmd = mCommandsList.get(position);
                mHelper.runTermuxCommand(cmd, false);
                Toast.makeText(this, "Running: " + cmd, Toast.LENGTH_SHORT).show();
            });
        }
        if (mBtnAddCommand != null && mEtNewCommand != null) {
            mBtnAddCommand.setOnClickListener(v -> {
                String cmd = mEtNewCommand.getText().toString().trim();
                if (!cmd.isEmpty()) {
                    mHelper.saveCommand(cmd);
                    mCommandsList.clear();
                    mCommandsList.addAll(mHelper.getSavedCommands());
                    mCommandsAdapter.notifyDataSetChanged();
                    mEtNewCommand.setText("");
                    Toast.makeText(this, "Command saved", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateMenuLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsResuming = true;
        
        // Apply WakeLock setting
        applyWakeLockSetting(mWakeLockEnabled);

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

        // Register Session History Broadcast Callback
        registerReceiver(mSessionHistoryReceiver, new IntentFilter(ACTION_SESSION_HISTORY));

        // Register Copy Artifact Callback
        registerReceiver(mCopyArtifactReceiver, new IntentFilter(ACTION_COPY_ARTIFACT));

        // Register Stream Display Receiver
        IntentFilter streamFilter = new IntentFilter();
        streamFilter.addAction("com.toggletalk.android.ACTION_STREAM_DISPLAY");
        streamFilter.addAction("com.toggletalk.android.ACTION_NEW_SESSION_ADOPTED");
        streamFilter.addAction("com.toggletalk.android.ACTION_REFRESH_HISTORY");
        registerReceiver(mStreamDisplayReceiver, streamFilter);

        // Register Queue Receiver
        registerReceiver(mQueueReceiver, new IntentFilter(ToggleTalkService.ACTION_QUEUE_CHANGED));

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
        try { unregisterReceiver(mSessionHistoryReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(mCopyArtifactReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(mStreamDisplayReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(mQueueReceiver); } catch (Exception ignored) {}
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
        intent.putExtra("session_id", mSelectedSessionId);
        startService(intent);
    }

    private void stopAgent() {
        // Send stop intent to service (kills TTS + signals stop)
        Intent intent = new Intent(this, ToggleTalkService.class);
        intent.setAction("com.toggletalk.android.ACTION_STOP");
        startService(intent);
        // Immediately update UI
        mIsAgentActive = false;
        updateSendButtonState();
        addSystemMessage("⛔ Stopped by user.", "#FF6B6B");
    }

    private void startNewChat() {
        // Clear session selection
        mSelectedSessionId = "";
        mContinueSession = false;
        getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putString("selected_session_id", "").apply();
        updateActiveSessionLabel();

        // Notify service
        Intent intent = new Intent(this, ToggleTalkService.class);
        intent.setAction("com.toggletalk.android.ACTION_SET_SESSION_ID");
        intent.putExtra("session_id", "");
        intent.putExtra("continue_session", false);
        startService(intent);

        // Clear chat log
        if (mChatContainer != null) mChatContainer.removeAllViews();
        mActiveAgentTextView = null;
        mUserPrompt = "";
        mDisplayedStepKeys.clear();
        mCurrentSessionHistory = new org.json.JSONArray();
        mShowAllEarlierMessages = false;
        mLastStreamedAgentText = "";
        mExpandedIndices.clear();
        mSessionsAdapter.notifyDataSetChanged();

        mNewChatStartedBubble = addSystemMessage("✦ New chat started. Type or speak your message.", "#00F2FE");
        Toast.makeText(this, "New chat started", Toast.LENGTH_SHORT).show();
    }

    private void updateSendButtonState() {
        if (mBtnSend == null) return;
        mBtnSend.setImageResource(android.R.drawable.ic_menu_send);
        mBtnSend.setImageTintList(ColorStateList.valueOf(Color.parseColor("#00F2FE")));
        mBtnSend.setBackgroundTintList(null);
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
        
        // Sync with helper
        if (mHelper != null) {
            mHelper.setTargetDirectory(dir);
        }

        // Update the ToggleTalkService with the new active directory setting
        Intent intent = new Intent(this, ToggleTalkService.class);
        intent.setAction(ToggleTalkService.ACTION_SET_DIRECTORY);
        intent.putExtra("target_directory", dir);
        startService(intent);

        Toast.makeText(this, "Active directory set to: " + dir, Toast.LENGTH_SHORT).show();
        
        // Close drawer smoothly
        mDrawerContent.postDelayed(this::closeDrawer, 150);
        
        // Re-query sessions for the new directory
        queryTermuxSessions();
    }

    private void sendMessage() {
        if (mEtMessage == null) return;
        String message = mEtMessage.getText().toString().trim();
        if (message.isEmpty()) return;

        mEtMessage.setText("");

        // Clear previous state for a new turn
        mUserPrompt = message;
        mLastStreamedAgentText = "";
        mIsResuming = false;
        
        // Add manual bubbles immediately for better UX
        addUserBubble(mUserPrompt);
        addAgentBubble("...");

        Intent intent = new Intent(this, ToggleTalkService.class);
        intent.setAction("com.toggletalk.android.ACTION_SEND_PROMPT");
        intent.putExtra("prompt", message);
        intent.putExtra("continue_session", mContinueSession);
        intent.putExtra("session_id", mSelectedSessionId);
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

    private void openSettings() {
        if (mSettingsPopupRoot != null) {
            mSettingsPopupRoot.setVisibility(View.VISIBLE);
        }
    }

    private void closeSettings() {
        if (mSettingsPopupRoot != null) {
            mSettingsPopupRoot.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        if (mArtifactsPopupRoot != null && mArtifactsPopupRoot.getVisibility() == View.VISIBLE) {
            closeArtifactsPopup();
        } else if (mSettingsPopupRoot != null && mSettingsPopupRoot.getVisibility() == View.VISIBLE) {
            closeSettings();
        } else if (mIsDrawerOpen) {
            closeDrawer();
        } else if (mIsRightDrawerOpen) {
            closeRightDrawer();
        } else {
            super.onBackPressed();
        }
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
        mContinueSession = true;
        updateActiveSessionLabel();
        mSessionsAdapter.notifyDataSetChanged();

        // Save to preferences
        getSharedPreferences("ToggleTalkPrefs", MODE_PRIVATE).edit().putString("selected_session_id", sessionId).apply();

        // Update the ToggleTalkService with the new active session ID
        Intent intent = new Intent(this, ToggleTalkService.class);
        intent.setAction("com.toggletalk.android.ACTION_SET_SESSION_ID");
        intent.putExtra("session_id", sessionId);
        intent.putExtra("continue_session", true);
        startService(intent);

        Toast.makeText(this, "Session loaded: " + (sessionId.length() > 8 ? sessionId.substring(0, 8) + "..." : sessionId), Toast.LENGTH_SHORT).show();

        // Load session history into chat log
        loadSessionHistory(sessionId);

        // Close right drawer smoothly
        mRightDrawerContent.postDelayed(this::closeRightDrawer, 150);
    }

    private void loadSessionHistory(String sessionId) {
        // Clear current chat log
        if (mChatContainer != null) mChatContainer.removeAllViews();
        mNewChatStartedBubble = null;
        mActiveAgentTextView = null;
        mUserPrompt = "";
        mDisplayedStepKeys.clear();
        mCurrentSessionHistory = new org.json.JSONArray();
        mShowAllEarlierMessages = false;
        mLastStreamedAgentText = "";
        mExpandedIndices.clear();
        mCollapsedIndices.clear();
        mExpandedSummaryIndices.clear();
        mRenderedTextCache.clear();
        mCachedRawTexts.clear();

        // Check if cached session history exists on the app side
        java.io.File cacheDir = new java.io.File(getCacheDir(), "session_history_cache");
        java.io.File cacheFile = new java.io.File(cacheDir, sessionId + ".json");
        if (cacheFile.exists()) {
            String cachedJson = readFileContent(cacheFile.getAbsolutePath());
            if (cachedJson != null && !cachedJson.isEmpty()) {
                try {
                    org.json.JSONArray array = new org.json.JSONArray(cachedJson);
                    if (array.length() > 0) {
                        mCurrentSessionHistory = array;
                        displayMessages(mCurrentSessionHistory, false);
                        Log.d(TAG, "Loaded session history from app-side cache for " + sessionId);
                        return; // Bypass Termux Python execution entirely
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing cached session history", e);
                }
            }
        }

        addSystemMessage("Loading session history...", "#80FFFFFF");
        mHelper.querySessionHistory(sessionId);
    }

    private void receiveSessionHistory(String sessionId, String stdout, String errmsg) {
        if (mChatContainer != null) mChatContainer.removeAllViews();
        mNewChatStartedBubble = null;
        mActiveAgentTextView = null;

        if (stdout == null || stdout.trim().isEmpty()) {
            Log.w(TAG, "Session history stdout empty. errmsg=" + errmsg);
            addSystemMessage("No conversation history found for this session.", "#80FFFFFF");
            return;
        }

        try {
            String trimmed = stdout.trim();
            String jsonContent = trimmed;

            if (trimmed.startsWith("{")) {
                org.json.JSONObject obj = new org.json.JSONObject(trimmed);
                String err = obj.optString("error", "");
                if (!err.isEmpty()) {
                    Log.e(TAG, "History python script returned error: " + err);
                    addSystemMessage("No conversation history found for this session.", "#80FFFFFF");
                    return;
                }
                
                String status = obj.optString("status", "");
                if ("success".equals(status)) {
                    String filePath = obj.optString("file", "");
                    if (!filePath.isEmpty()) {
                        Log.d(TAG, "Reading session history from file: " + filePath);
                        jsonContent = readFileContent(filePath);
                    }
                }
            }

            org.json.JSONArray array = new org.json.JSONArray(jsonContent);
            if (array.length() == 0) {
                addSystemMessage("No messages found in this session.", "#80FFFFFF");
                return;
            }

            mCurrentSessionHistory = array;
            
            // Cache the successfully retrieved session history
            String activeSessionId = (sessionId != null && !sessionId.isEmpty()) ? sessionId : mSelectedSessionId;
            writeSessionToCache(activeSessionId, jsonContent);

            displayMessages(mCurrentSessionHistory, false);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing session history JSON", e);
            addSystemMessage("Error loading session history.", "#FF6B6B");
        }
    }

    private String readFileContent(String filePath) {
        StringBuilder sb = new StringBuilder();
        java.io.BufferedReader reader = null;
        try {
            reader = new java.io.BufferedReader(new java.io.FileReader(filePath));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading file: " + filePath, e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
        return sb.toString().trim();
    }

    private int mCurrentDisplayLimit = 250;

    private void displayMessages(final org.json.JSONArray array, boolean showAll) {
        mShowAllEarlierMessages = showAll;
        if (showAll) {
            mCurrentDisplayLimit = array.length();
        }
        displayMessagesInternal(array, array.length(), showAll);
    }

    private void displayMessagesUpTo(int limitCount, boolean showAll) {
        mShowAllEarlierMessages = showAll;
        displayMessagesInternal(mCurrentSessionHistory, limitCount, showAll);
    }

    private int mViewIndex = 0;
    private boolean mRecreateMode = false;

    private void clearHoldersFromIndex(int startIndex) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mCollapsibleBubbleHolders.removeIf(holder -> holder.index >= startIndex);
        } else {
            java.util.Iterator<CollapsibleBubbleHolder> it = mCollapsibleBubbleHolders.iterator();
            while (it.hasNext()) {
                if (it.next().index >= startIndex) {
                    it.remove();
                }
            }
        }
    }

    private void displayMessagesInternal(final org.json.JSONArray array, int limitCount, boolean showAll) {
        if (array == null || (array.length() == 0 && mUserPrompt != null && !mUserPrompt.isEmpty())) {
            Log.d(TAG, "displayMessagesInternal: skipping empty array while user prompt is pending");
            return;
        }
        updateBtnExpandAllText();
        boolean wasAtBottom = isScrolledToBottom();
        
        mViewIndex = 0;
        mRecreateMode = false;

        int total = Math.min(array.length(), limitCount);
        int start = showAll ? 0 : Math.max(0, total - mCurrentDisplayLimit);

        if (start > 0) {
            BubbleTag expectedTag = new BubbleTag("omitted", start, "omitted_" + start);
            if (!mRecreateMode && mViewIndex < mChatContainer.getChildCount()) {
                View existingView = mChatContainer.getChildAt(mViewIndex);
                Object tag = existingView.getTag();
                if (tag instanceof BubbleTag && tag.equals(expectedTag)) {
                    mViewIndex++;
                } else {
                    mRecreateMode = true;
                    mChatContainer.removeViews(mViewIndex, mChatContainer.getChildCount() - mViewIndex);
                    clearHoldersFromIndex(start);
                }
            }
            if (mRecreateMode || mViewIndex >= mChatContainer.getChildCount()) {
                addOmittedMessagesClickable(start, array);
                View newView = mChatContainer.getChildAt(mChatContainer.getChildCount() - 1);
                if (newView != null) newView.setTag(expectedTag);
                mViewIndex = mChatContainer.getChildCount();
            }
        }

        boolean hasAgentResponseAtEnd = false;

        for (int i = start; i < total; i++) {
            try {
                org.json.JSONObject msg = array.getJSONObject(i);
                String role = msg.optString("role", "");
                String text = msg.optString("text", "");
                if (text.isEmpty()) continue;

                if (isCollapsible(role)) {
                    if (shouldGroup(role)) {
                        // Gather consecutive collapsible messages to group
                        int j = i;
                        int thoughts = 0;
                        int toolCalls = 0;
                        int toolResults = 0;
                        while (j < total) {
                            org.json.JSONObject nextMsg = array.getJSONObject(j);
                            String nextRole = nextMsg.optString("role", "");
                            if (!isCollapsible(nextRole) || !shouldGroup(nextRole)) break;
                            
                            if ("thought".equals(nextRole)) thoughts++;
                            else if ("tool_call".equals(nextRole)) toolCalls++;
                            else if ("tool_result".equals(nextRole)) toolResults++;
                            j++;
                        }
                        
                        int groupSize = thoughts + toolCalls + toolResults;
                        if (groupSize > 1) {
                            if (!mExpandedSummaryIndices.contains(i) && !mShowThoughtsAndToolCalls) {
                                BubbleTag expectedTag = new BubbleTag("summary", i, thoughts + "_" + toolCalls + "_" + toolResults);
                                if (!mRecreateMode && mViewIndex < mChatContainer.getChildCount()) {
                                    View existingView = mChatContainer.getChildAt(mViewIndex);
                                    Object tag = existingView.getTag();
                                    if (tag instanceof BubbleTag && tag.equals(expectedTag)) {
                                        mViewIndex++;
                                    } else {
                                        mRecreateMode = true;
                                        mChatContainer.removeViews(mViewIndex, mChatContainer.getChildCount() - mViewIndex);
                                        clearHoldersFromIndex(i);
                                    }
                                }
                                if (mRecreateMode || mViewIndex >= mChatContainer.getChildCount()) {
                                    addSummaryBubble(thoughts, toolCalls, toolResults, i, array);
                                    View newView = mChatContainer.getChildAt(mChatContainer.getChildCount() - 1);
                                    if (newView != null) newView.setTag(expectedTag);
                                    mViewIndex = mChatContainer.getChildCount();
                                }
                            } else {
                                // Group is expanded! Show expanded header & child bubbles
                                BubbleTag expectedTag = new BubbleTag("summary_header", i, thoughts + "_" + toolCalls + "_" + toolResults);
                                if (!mRecreateMode && mViewIndex < mChatContainer.getChildCount()) {
                                    View existingView = mChatContainer.getChildAt(mViewIndex);
                                    Object tag = existingView.getTag();
                                    if (tag instanceof BubbleTag && tag.equals(expectedTag)) {
                                        mViewIndex++;
                                    } else {
                                        mRecreateMode = true;
                                        mChatContainer.removeViews(mViewIndex, mChatContainer.getChildCount() - mViewIndex);
                                        clearHoldersFromIndex(i);
                                    }
                                }
                                if (mRecreateMode || mViewIndex >= mChatContainer.getChildCount()) {
                                    addExpandedSummaryHeader(thoughts, toolCalls, toolResults, i, array);
                                    View newView = mChatContainer.getChildAt(mChatContainer.getChildCount() - 1);
                                    if (newView != null) newView.setTag(expectedTag);
                                    mViewIndex = mChatContainer.getChildCount();
                                }

                                for (int k = i; k < j; k++) {
                                    org.json.JSONObject childMsg = array.getJSONObject(k);
                                    String childRole = childMsg.optString("role", "");
                                    String childText = childMsg.optString("text", "");
                                    if (!childText.isEmpty()) {
                                        boolean childShouldExpand = mShowThoughtsAndToolCalls || true || isBubbleExpanded(childRole, k);
                                        BubbleTag childTag = new BubbleTag(childRole, k, childText + "_" + childShouldExpand);
                                        if (!mRecreateMode && mViewIndex < mChatContainer.getChildCount()) {
                                            View existingView = mChatContainer.getChildAt(mViewIndex);
                                            Object tag = existingView.getTag();
                                            if (tag instanceof BubbleTag && tag.equals(childTag)) {
                                                mViewIndex++;
                                            } else {
                                                mRecreateMode = true;
                                                mChatContainer.removeViews(mViewIndex, mChatContainer.getChildCount() - mViewIndex);
                                                clearHoldersFromIndex(k);
                                            }
                                        }
                                        if (mRecreateMode || mViewIndex >= mChatContainer.getChildCount()) {
                                            addCollapsibleBubble(childRole, childText, k, true);
                                            View newView = mChatContainer.getChildAt(mChatContainer.getChildCount() - 1);
                                            if (newView != null) newView.setTag(childTag);
                                            mViewIndex = mChatContainer.getChildCount();
                                        }
                                    }
                                }
                            }
                            i = j - 1;
                            continue;
                        }
                    }
                    
                    boolean shouldExpand = mShowThoughtsAndToolCalls || false || isBubbleExpanded(role, i);
                    BubbleTag expectedTag = new BubbleTag(role, i, text + "_" + shouldExpand);
                    if (!mRecreateMode && mViewIndex < mChatContainer.getChildCount()) {
                        View existingView = mChatContainer.getChildAt(mViewIndex);
                        Object tag = existingView.getTag();
                        if (tag instanceof BubbleTag && tag.equals(expectedTag)) {
                            mViewIndex++;
                        } else {
                            mRecreateMode = true;
                            mChatContainer.removeViews(mViewIndex, mChatContainer.getChildCount() - mViewIndex);
                            clearHoldersFromIndex(i);
                        }
                    }
                    if (mRecreateMode || mViewIndex >= mChatContainer.getChildCount()) {
                        addCollapsibleBubble(role, text, i, false);
                        View newView = mChatContainer.getChildAt(mChatContainer.getChildCount() - 1);
                        if (newView != null) newView.setTag(expectedTag);
                        mViewIndex = mChatContainer.getChildCount();
                    }
                } else {
                    if ("user".equals(role)) {
                        BubbleTag expectedTag = new BubbleTag("user", i, text);
                        if (!mRecreateMode && mViewIndex < mChatContainer.getChildCount()) {
                            View existingView = mChatContainer.getChildAt(mViewIndex);
                            Object tag = existingView.getTag();
                            if (tag instanceof BubbleTag && tag.equals(expectedTag)) {
                                mViewIndex++;
                            } else {
                                mRecreateMode = true;
                                mChatContainer.removeViews(mViewIndex, mChatContainer.getChildCount() - mViewIndex);
                                clearHoldersFromIndex(i);
                            }
                        }
                        if (mRecreateMode || mViewIndex >= mChatContainer.getChildCount()) {
                            addUserBubble(text);
                            View newView = mChatContainer.getChildAt(mChatContainer.getChildCount() - 1);
                            if (newView != null) newView.setTag(expectedTag);
                            mViewIndex = mChatContainer.getChildCount();
                        }
                        hasAgentResponseAtEnd = false;
                    } else if ("agent".equals(role)) {
                        BubbleTag expectedTag = new BubbleTag("agent", i, text);
                        if (!mRecreateMode && mViewIndex < mChatContainer.getChildCount()) {
                            View existingView = mChatContainer.getChildAt(mViewIndex);
                            Object tag = existingView.getTag();
                            if (tag instanceof BubbleTag && tag.equals(expectedTag)) {
                                mViewIndex++;
                                if (i == total - 1) {
                                    android.widget.LinearLayout layout = (android.widget.LinearLayout) existingView;
                                    mActiveAgentTextView = (TextView) layout.getChildAt(0);
                                }
                            } else {
                                mRecreateMode = true;
                                mChatContainer.removeViews(mViewIndex, mChatContainer.getChildCount() - mViewIndex);
                                clearHoldersFromIndex(i);
                            }
                        }
                        if (mRecreateMode || mViewIndex >= mChatContainer.getChildCount()) {
                            addAgentBubble(text);
                            View newView = mChatContainer.getChildAt(mChatContainer.getChildCount() - 1);
                            if (newView != null) newView.setTag(expectedTag);
                            mViewIndex = mChatContainer.getChildCount();
                        }
                        mActiveAgentTextView = null;
                        hasAgentResponseAtEnd = true;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error displaying message at " + i, e);
            }
        }

        if (mIsAgentActive && !hasAgentResponseAtEnd && mActiveAgentTextView == null) {
            BubbleTag expectedTag = new BubbleTag("agent_active", -1, "...");
            if (!mRecreateMode && mViewIndex < mChatContainer.getChildCount()) {
                View existingView = mChatContainer.getChildAt(mViewIndex);
                Object tag = existingView.getTag();
                if (tag instanceof BubbleTag && tag.equals(expectedTag)) {
                    mViewIndex++;
                    android.widget.LinearLayout layout = (android.widget.LinearLayout) existingView;
                    mActiveAgentTextView = (TextView) layout.getChildAt(0);
                } else {
                    mRecreateMode = true;
                    mChatContainer.removeViews(mViewIndex, mChatContainer.getChildCount() - mViewIndex);
                    clearHoldersFromIndex(total);
                }
            }
            if (mRecreateMode || mViewIndex >= mChatContainer.getChildCount()) {
                addAgentBubble("...");
                View newView = mChatContainer.getChildAt(mChatContainer.getChildCount() - 1);
                if (newView != null) newView.setTag(expectedTag);
                mViewIndex = mChatContainer.getChildCount();
            }
        }

        if (mViewIndex < mChatContainer.getChildCount()) {
            mChatContainer.removeViews(mViewIndex, mChatContainer.getChildCount() - mViewIndex);
            clearHoldersFromIndex(total);
        }

        scrollToBottomIfNeeded(wasAtBottom);
    }

    private static class BubbleTag {
        final String type;
        final int index;
        final String hash;

        BubbleTag(String type, int index, String hash) {
            this.type = type;
            this.index = index;
            this.hash = hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BubbleTag bubbleTag = (BubbleTag) o;
            return index == bubbleTag.index &&
                    type.equals(bubbleTag.type) &&
                    hash.equals(bubbleTag.hash);
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + index;
            result = 31 * result + hash.hashCode();
            return result;
        }
    }

    private boolean isCollapsible(String role) {
        return "thought".equals(role) || "tool_call".equals(role) || "tool_result".equals(role);
    }

    private boolean shouldGroup(String role) {
        if ("thought".equals(role)) return mCollapseThoughts;
        if ("tool_call".equals(role) || "tool_result".equals(role)) return mCollapseTools;
        return false;
    }

    private void addSummaryBubble(int thoughts, int toolCalls, int toolResults, final int startIndex, final org.json.JSONArray array) {
        StringBuilder sb = new StringBuilder();
        if (thoughts > 0) sb.append("Thoughts [").append(thoughts).append("]");
        if (toolCalls > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Tool Calls [").append(toolCalls).append("]");
        }
        if (toolResults > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Tool Results [").append(toolResults).append("]");
        }
        
        String summaryText = sb.toString();
        final TextView tv = addSystemMessage("▶ " + summaryText, "#00F2FE");
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setBackgroundResource(R.drawable.card_glass);
        float density = getResources().getDisplayMetrics().density;
        tv.setPadding((int)(12*density), (int)(8*density), (int)(12*density), (int)(8*density));
        
        tv.setOnClickListener(v -> {
            if (!mShowThoughtsAndToolCalls) {
                mExpandedSummaryIndices.clear();
            }
            mExpandedSummaryIndices.add(startIndex);
            displayMessages(array, mShowAllEarlierMessages);
        });
    }

    private void addExpandedSummaryHeader(int thoughts, int toolCalls, int toolResults, final int startIndex, final org.json.JSONArray array) {
        StringBuilder sb = new StringBuilder();
        if (thoughts > 0) sb.append("Thoughts [").append(thoughts).append("]");
        if (toolCalls > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Tool Calls [").append(toolCalls).append("]");
        }
        if (toolResults > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Tool Results [").append(toolResults).append("]");
        }
        
        String summaryText = sb.toString();
        final TextView tv = addSystemMessage("▼ " + summaryText, "#00F2FE");
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setBackgroundResource(R.drawable.card_glass);
        float density = getResources().getDisplayMetrics().density;
        tv.setPadding((int)(12*density), (int)(8*density), (int)(12*density), (int)(8*density));
        
        tv.setOnClickListener(v -> {
            mExpandedSummaryIndices.remove(startIndex);
            displayMessages(array, mShowAllEarlierMessages);
        });
    }

    private boolean isBubbleExpanded(String role, int index) {
        if (mShowThoughtsAndToolCalls) {
            return true;
        }
        boolean defaultExpanded = false;
        if ("thought".equals(role)) {
            defaultExpanded = !mCollapseThoughts;
        } else if ("tool_call".equals(role) || "tool_result".equals(role)) {
            defaultExpanded = !mCollapseTools;
        }
        
        if (defaultExpanded) {
            return !mCollapsedIndices.contains(index);
        } else {
            return mExpandedIndices.contains(index);
        }
    }

    private void addCollapsibleBubble(String role, String text, int index, boolean forceExpand) {
        if (mChatContainer == null || text == null || text.isEmpty()) return;

        float density = getResources().getDisplayMetrics().density;

        String header;
        String content;
        if ("tool_result".equals(role)) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline != -1) {
                header = text.substring(0, firstNewline).trim();
                content = text.substring(firstNewline + 1);
            } else {
                header = text.trim();
                content = "";
            }
        } else if ("thought".equals(role)) {
            header = "Thought";
            content = text;
        } else { // tool_call
            header = "Tool Call";
            content = text;
        }

        final android.widget.LinearLayout bubbleLayout = new android.widget.LinearLayout(this);
        bubbleLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        bubbleLayout.setPadding((int)(8 * density), (int)(6 * density), (int)(8 * density), (int)(6 * density));
        bubbleLayout.setClickable(true);
        bubbleLayout.setFocusable(true);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setCornerRadius(density * 8);

        if ("thought".equals(role)) {
            gd.setColor(Color.parseColor("#10FFFFFF"));
            gd.setStroke((int)density, Color.parseColor("#1AFFFFFF"));
        } else if ("tool_call".equals(role)) {
            gd.setColor(Color.parseColor("#0D00F2FE"));
            gd.setStroke((int)density, Color.parseColor("#2000F2FE"));
        } else { // tool_result
            gd.setColor(Color.parseColor("#0800F2FE"));
            gd.setStroke((int)density, Color.parseColor("#1A00F2FE"));
        }
        bubbleLayout.setBackground(gd);

        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int)(8 * density), (int)(3 * density), (int)(8 * density), (int)(3 * density));
        bubbleLayout.setLayoutParams(lp);

        // Header TextView
        final TextView headerTv = new TextView(this);
        headerTv.setTextSize(11);
        if ("thought".equals(role)) {
            headerTv.setTextColor(Color.parseColor("#D0FFFFFF"));
        } else {
            headerTv.setTextColor(Color.parseColor("#00F2FE"));
        }

        // Content TextView
        final TextView contentTv = new TextView(this);
        contentTv.setTextSize(10);
        contentTv.setTextIsSelectable(true);
        contentTv.setPadding(0, (int)(4 * density), 0, 0);
        contentTv.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());

        CharSequence fullSpanned = "";
        CharSequence collapsedSpanned = null;
        String filePath = null;

        if ("thought".equals(role)) {
            contentTv.setTextColor(Color.parseColor("#D0FFFFFF"));
            String cachedRaw = mCachedRawTexts.get(index);
            android.text.Spanned cached = mRenderedTextCache.get(index);
            if (cached == null || !content.equals(cachedRaw)) {
                cached = renderMarkdown(content);
                mRenderedTextCache.put(index, cached);
                mCachedRawTexts.put(index, content);
            }
            fullSpanned = cached;
        } else if ("tool_call".equals(role)) {
            contentTv.setTextColor(Color.parseColor("#A0E6FF"));
            contentTv.setTypeface(android.graphics.Typeface.MONOSPACE);
            String cachedRaw = mCachedRawTexts.get(index);
            android.text.Spanned cached = mRenderedTextCache.get(index);
            if (cached == null || !content.equals(cachedRaw)) {
                cached = renderMarkdown(content);
                mRenderedTextCache.put(index, cached);
                mCachedRawTexts.put(index, content);
            }
            fullSpanned = cached;

            if (content.contains("view_file")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("AbsolutePath\\s*=\\s*([^,\\)]+)").matcher(content);
                if (m.find()) {
                    filePath = m.group(1).trim();
                }
            }
        } else { // tool_result
            if (!content.isEmpty()) {
                if (header.startsWith("View File")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("File Path:\\s*`?(file://[^`\\n]+)`?").matcher(content);
                    if (m.find()) {
                        filePath = m.group(1).trim();
                    } else {
                        m = java.util.regex.Pattern.compile("File Path:\\s*`?([^`\\n]+)`?").matcher(content);
                        if (m.find()) {
                            filePath = m.group(1).trim();
                        }
                    }
                }

                String cachedRaw = mCachedRawTexts.get(index);
                android.text.Spanned spanned = mRenderedTextCache.get(index);
                if (spanned == null || !content.equals(cachedRaw)) {
                    String processedContent = content;
                    // Auto-wrap in backticks if it looks like code and has no backticks
                    if (!content.contains("```") && looksLikeCode(content)) {
                        String lang = detectLanguage(content, header);
                        processedContent = "```" + lang + "\n" + content + "\n```";
                    }
                    spanned = renderMarkdown(processedContent);
                    mRenderedTextCache.put(index, spanned);
                    mCachedRawTexts.put(index, content);
                }
                fullSpanned = spanned;
            }
        }

        if (filePath != null) {
            String fileName = filePath;
            if (fileName.contains("/")) {
                fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
            }
            String linkUrl = filePath;
            if (!linkUrl.startsWith("file://")) {
                if (linkUrl.startsWith("/")) {
                    linkUrl = "file://" + linkUrl;
                } else {
                    linkUrl = "file:///data/data/com.termux/files/home/" + linkUrl;
                }
            }
            String mdLink = "[" + fileName + "](" + linkUrl + ")";
            collapsedSpanned = renderMarkdown(mdLink);
        }

        bubbleLayout.addView(headerTv);
        bubbleLayout.addView(contentTv);

        final CollapsibleBubbleHolder holder = new CollapsibleBubbleHolder(bubbleLayout, headerTv, contentTv, header, index);
        holder.fullContent = fullSpanned;
        holder.collapsedContent = collapsedSpanned;
        mCollapsibleBubbleHolders.add(holder);

        // Determine if it should be expanded initially
        boolean shouldExpand = mShowThoughtsAndToolCalls || forceExpand || isBubbleExpanded(role, index);
        holder.setExpanded(shouldExpand);

        View.OnClickListener clickListener = v -> toggleCollapsibleBubble(holder, role);
        bubbleLayout.setOnClickListener(clickListener);
        headerTv.setOnClickListener(clickListener);

        mChatContainer.addView(bubbleLayout);
    }

    private void toggleCollapsibleBubble(CollapsibleBubbleHolder clickedHolder, String role) {
        boolean targetState = !clickedHolder.expanded;
        int index = clickedHolder.index;
        
        boolean defaultExpanded = false;
        if ("thought".equals(role)) {
            defaultExpanded = !mCollapseThoughts;
        } else if ("tool_call".equals(role) || "tool_result".equals(role)) {
            defaultExpanded = !mCollapseTools;
        }
        
        if (!mShowThoughtsAndToolCalls) {
            if (targetState) {
                mExpandedIndices.clear();
                mCollapsedIndices.clear();
                mExpandedIndices.add(index);
                for (CollapsibleBubbleHolder holder : mCollapsibleBubbleHolders) {
                    if (holder != clickedHolder && holder.expanded) {
                        String otherRole = "tool_result";
                        if (holder.headerText.equals("Thought")) otherRole = "thought";
                        else if (holder.headerText.equals("Tool Call")) otherRole = "tool_call";
                        
                        boolean otherDefaultExpanded = false;
                        if ("thought".equals(otherRole)) {
                            otherDefaultExpanded = !mCollapseThoughts;
                        } else if ("tool_call".equals(otherRole) || "tool_result".equals(otherRole)) {
                            otherDefaultExpanded = !mCollapseTools;
                        }
                        
                        if (otherDefaultExpanded) {
                            mCollapsedIndices.add(holder.index);
                        } else {
                            mExpandedIndices.remove(holder.index);
                        }
                        holder.setExpanded(false);
                    }
                }
            } else {
                if (defaultExpanded) {
                    mCollapsedIndices.add(index);
                } else {
                    mExpandedIndices.remove(index);
                }
            }
        } else {
            if (targetState) {
                mCollapsedIndices.remove(index);
                mExpandedIndices.add(index);
            } else {
                mCollapsedIndices.add(index);
                mExpandedIndices.remove(index);
            }
        }
        clickedHolder.setExpanded(targetState);
        updateBtnExpandAllText();
    }

    private void addOmittedMessagesClickable(final int count, final org.json.JSONArray array) {
        if (mChatContainer == null) return;
        float density = getResources().getDisplayMetrics().density;
        
        final TextView tv = new TextView(this);
        tv.setText("... (" + count + " earlier messages omitted. Tap to load 250 more) ...");
        tv.setTextColor(Color.parseColor("#60FFFFFF"));
        tv.setTextSize(12);
        tv.setPadding((int)(8 * density), (int)(5 * density), (int)(8 * density), (int)(5 * density));
        tv.setTextIsSelectable(false);
        tv.setFocusable(true);
        tv.setClickable(true);
        
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.parseColor("#1AFFFFFF"));
        gd.setCornerRadius(density * 6);
        gd.setStroke((int)density, Color.parseColor("#33FFFFFF"));
        tv.setBackground(gd);
        
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(4 * density), 0, (int)(4 * density));
        tv.setLayoutParams(lp);

        final ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        pb.setVisibility(View.GONE);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor("#00F2FE")));
        android.widget.LinearLayout.LayoutParams lpPb = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpPb.gravity = android.view.Gravity.CENTER;
        lpPb.setMargins(0, (int)(4 * density), 0, (int)(4 * density));
        pb.setLayoutParams(lpPb);

        tv.setOnClickListener(v -> {
            tv.setVisibility(View.GONE);
            pb.setVisibility(View.VISIBLE);
            new android.os.Handler().postDelayed(() -> {
                mCurrentDisplayLimit += 250;
                displayMessages(array, false);
            }, 400);
        });
        
        mChatContainer.addView(tv);
        mChatContainer.addView(pb);
    }

    private void handleStreamedDisplay(String sessionId, String messagesJson, String filePath) {
        Log.d("MainActivity", "handleStreamedDisplay: sess=" + sessionId + ", hasJson=" + (messagesJson != null) + ", hasPath=" + (filePath != null));
        mBufferedSessionId = sessionId;
        mBufferedMessagesJson = messagesJson;
        mBufferedFilePath = filePath;

        long now = System.currentTimeMillis();
        long timeSinceLastUpdate = now - mLastStreamUpdateTime;
        long delay = 150 - timeSinceLastUpdate;

        if (delay <= 0) {
            mStreamHandler.removeCallbacks(mStreamUpdateRunnable);
            mStreamUpdateRunnable.run();
        } else if (!mStreamUpdatePending) {
            mStreamUpdatePending = true;
            mStreamHandler.postDelayed(mStreamUpdateRunnable, delay);
        }
    }

    private void performStreamedDisplay(String sessionId, String messagesJson, String filePath) {
        Log.d("MainActivity", "performStreamedDisplay start: sess=" + sessionId + ", mSelectedSessionId=" + mSelectedSessionId);
        try {
            String jsonContent = messagesJson;
            if ((jsonContent == null || jsonContent.isEmpty()) && filePath != null && !filePath.isEmpty()) {
                Log.d("MainActivity", "Reading content from file: " + filePath);
                jsonContent = readFileContent(filePath);
            }
            if (jsonContent == null || jsonContent.isEmpty()) {
                Log.w("MainActivity", "performStreamedDisplay: empty content");
                return;
            }

            org.json.JSONArray newHistory = new org.json.JSONArray(jsonContent);
            Log.d("MainActivity", "Streamed history length: " + newHistory.length());

            if (mCurrentSessionHistory != null && newHistory.toString().equals(mCurrentSessionHistory.toString())) {
                Log.d("MainActivity", "History unchanged, skipping update");
                return;
            }

            Log.d("MainActivity", "Updating UI with " + newHistory.length() + " messages");
            mCurrentSessionHistory = newHistory;

            // Cache the streamed session history
            String activeSessionId = (sessionId != null && !sessionId.isEmpty()) ? sessionId : mSelectedSessionId;
            writeSessionToCache(activeSessionId, jsonContent);

            if (mCurrentSessionHistory.length() > 0) {
                org.json.JSONObject lastMsg = mCurrentSessionHistory.getJSONObject(mCurrentSessionHistory.length() - 1);
                String role = lastMsg.optString("role", "");
                String text = lastMsg.optString("text", "");
                Log.d("MainActivity", "Last message: role=" + role + ", textLength=" + text.length());

                if ("agent".equals(role) && !text.equals(mLastStreamedAgentText)) {
                    mLastStreamedAgentText = text;
                    displayMessagesUpTo(mCurrentSessionHistory.length() - 1, mShowAllEarlierMessages);
                    streamAgentResponse(text);
                } else {
                    displayMessages(mCurrentSessionHistory, mShowAllEarlierMessages);
                }
            } else {
                displayMessages(mCurrentSessionHistory, mShowAllEarlierMessages);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error performing streamed display", e);
        }
    }

    private void updateBtnExpandAllText() {
        if (mBtnExpandAll != null) {
            boolean anyExpanded = mShowThoughtsAndToolCalls || !mExpandedSummaryIndices.isEmpty() || !mExpandedIndices.isEmpty();
            mBtnExpandAll.setText(anyExpanded ? "▼ Collapse" : "▶ Expand");
        }
    }

    @Override
    public void onPromptClick(int index, String text) {
        mEditingPromptIndex = index;
        if (mPromptEditPopup != null) {
            mPromptEditPopup.show(text, false);
        }
    }

    public void showPromptEditPopupForCombine(String text) {
        mEditingPromptIndex = -1; // Not editing a specific one
        if (mPromptEditPopup != null) {
            mPromptEditPopup.show(text, true);
        }
    }

    @Override
    public void onPromptDelete(int index) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Delete Prompt")
            .setMessage("Are you sure you want to delete this queued prompt?")
            .setPositiveButton("Delete", (dialog, which) -> {
                Intent intent = new Intent(this, ToggleTalkService.class);
                intent.setAction("com.toggletalk.android.ACTION_DELETE_PROMPT");
                intent.putExtra("index", index);
                startService(intent);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public void onExpansionToggled(boolean isExpanded) {
        mIsQueueExpanded = isExpanded;
        if (mPromptQueueView != null) {
            mPromptQueueView.render(mPromptQueue, mIsQueueExpanded);
        }
    }

    private void updateActiveSessionLabel() {
        String displayTitle = null;
        if (mSelectedSessionId == null || mSelectedSessionId.isEmpty()) {
            if (mTvActiveSession != null) mTvActiveSession.setText("Active: New Chat");
            if (mTvActiveSessionTop != null) mTvActiveSessionTop.setText("New Chat");
            return;
        }
        for (SessionItem item : mSessionsList) {
            if (item.id.equals(mSelectedSessionId)) {
                displayTitle = item.title;
                break;
            }
        }
        if (displayTitle == null) {
            displayTitle = mSelectedSessionId.length() > 10
                    ? mSelectedSessionId.substring(0, 8) + "..."
                    : mSelectedSessionId;
        }
        if (mTvActiveSession != null) mTvActiveSession.setText("Active: " + displayTitle);
        if (mTvActiveSessionTop != null) mTvActiveSessionTop.setText(displayTitle);
    }

    private TextView addSystemMessage(String text, String colorHex) {
        if (mChatContainer == null) return null;
        boolean wasAtBottom = isScrolledToBottom();
        float density = getResources().getDisplayMetrics().density;
        
        TextView tv = new TextView(this);
        tv.setText(renderMarkdown(text));
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTextSize(12);
        tv.setPadding((int)(8 * density), (int)(5 * density), (int)(8 * density), (int)(5 * density));
        tv.setTextIsSelectable(true);
        tv.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        
        // Extract the last 6 hex digits (RGB) for the stroke color - handles both #RRGGBB and #AARRGGBB
        String rgbHex = colorHex.replaceAll("^#[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})$", "#$1");
        if (!rgbHex.startsWith("#") || rgbHex.length() != 7) {
            rgbHex = colorHex.length() >= 7 ? "#" + colorHex.substring(colorHex.length() - 6) : "#FFFFFF";
        }
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.parseColor("#1AFFFFFF"));
        gd.setCornerRadius(density * 6);
        gd.setStroke((int)density, Color.parseColor("#1A" + rgbHex.substring(1)));
        tv.setBackground(gd);
        
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(4 * density), 0, (int)(4 * density));
        tv.setLayoutParams(lp);
        
        mChatContainer.addView(tv);
        scrollToBottomIfNeeded(wasAtBottom);
        return tv;
    }

    private void addUserBubble(String message) {
        if (mChatContainer == null || message == null || message.trim().isEmpty()) return;
        
        float density = getResources().getDisplayMetrics().density;
        
        android.widget.LinearLayout bubbleLayout = new android.widget.LinearLayout(this);
        bubbleLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(5 * density), 0, (int)(5 * density));
        bubbleLayout.setLayoutParams(lp);
        
        TextView tv = new TextView(this);
        tv.setText(renderMarkdown(message));
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        tv.setPadding((int)(10 * density), (int)(7 * density), (int)(10 * density), (int)(7 * density));
        tv.setTextIsSelectable(true);
        tv.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.parseColor("#4D9D50BB"));
        gd.setCornerRadius(density * 10);
        gd.setStroke((int)density, Color.parseColor("#669D50BB"));
        tv.setBackground(gd);
        
        android.widget.LinearLayout.LayoutParams tvLp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvLp.setMarginStart((int)(4 * density));
        tvLp.setMarginEnd((int)(4 * density));
        tv.setLayoutParams(tvLp);
        
        bubbleLayout.addView(tv);
        mChatContainer.addView(bubbleLayout);
        scrollToBottom();
    }

    private void addAgentBubble(String initialText) {
        if (mChatContainer == null) return;
        boolean wasAtBottom = isScrolledToBottom();
        
        float density = getResources().getDisplayMetrics().density;
        
        android.widget.LinearLayout bubbleLayout = new android.widget.LinearLayout(this);
        bubbleLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(5 * density), 0, (int)(5 * density));
        bubbleLayout.setLayoutParams(lp);
        
        TextView tv = new TextView(this);
        tv.setText(renderMarkdown(initialText));
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        tv.setPadding((int)(10 * density), (int)(7 * density), (int)(10 * density), (int)(7 * density));
        tv.setTextIsSelectable(true);
        tv.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.parseColor("#2600F2FE"));
        gd.setCornerRadius(density * 10);
        gd.setStroke((int)density, Color.parseColor("#4D00F2FE"));
        tv.setBackground(gd);
        
        android.widget.LinearLayout.LayoutParams tvLp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvLp.setMarginStart((int)(4 * density));
        tvLp.setMarginEnd((int)(4 * density));
        tv.setLayoutParams(tvLp);
        
        bubbleLayout.addView(tv);
        mChatContainer.addView(bubbleLayout);
        mActiveAgentTextView = tv;
        scrollToBottomIfNeeded(wasAtBottom);
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
        
        boolean wasAtBottom = isScrolledToBottom();
        mActiveAgentTextView.setText(renderMarkdown(fullText));
        scrollToBottomIfNeeded(wasAtBottom);
    }

    private boolean isScrolledToBottom() {
        if (mScrollLog == null || mScrollLog.getChildCount() == 0) return true;
        View child = mScrollLog.getChildAt(0);
        int diff = child.getBottom() - (mScrollLog.getHeight() + mScrollLog.getScrollY());
        float density = getResources().getDisplayMetrics().density;
        int tolerance = (int) (24 * density); // 24dp tolerance
        return diff <= tolerance;
    }

    private void scrollToBottom() {
        if (mScrollLog == null) return;
        mScrollLog.post(() -> {
            if (mScrollLog.getChildCount() > 0) {
                mScrollLog.scrollTo(0, mScrollLog.getChildAt(0).getBottom());
            }
        });
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            mCurrentSearchResults.clear();
            showSearchResults();
            return;
        }
        
        mCurrentSearchResults.clear();
        String lowerQuery = query.toLowerCase().trim();
        
        if (mChatContainer != null) {
            for (int i = 0; i < mChatContainer.getChildCount(); i++) {
                View child = mChatContainer.getChildAt(i);
                TextView tv = findTextView(child);
                if (tv != null) {
                    String text = tv.getText().toString();
                    if (text.toLowerCase().contains(lowerQuery)) {
                        mCurrentSearchResults.add(new SearchResult(text, child));
                    }
                }
            }
        }
        
        showSearchResults();
    }

    private TextView findTextView(View v) {
        if (v instanceof TextView) return (TextView) v;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextView tv = findTextView(vg.getChildAt(i));
                if (tv != null) return tv;
            }
        }
        return null;
    }

    private void showSearchResults() {
        if (mLvSearchResults == null) return;
        
        android.widget.ArrayAdapter<SearchResult> adapter = new android.widget.ArrayAdapter<SearchResult>(
                this, android.R.layout.simple_list_item_1, mCurrentSearchResults) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = (TextView) view.findViewById(android.R.id.text1);
                text.setTextColor(0xFFE6E6FA);
                text.setTextSize(12);
                return view;
            }
        };
        mLvSearchResults.setAdapter(adapter);
    }

    private void scrollToView(View view) {
        if (mScrollLog == null || view == null) return;
        mScrollLog.post(() -> {
            int top = view.getTop();
            mScrollLog.smoothScrollTo(0, top);
        });
    }

    private void scrollToBottomIfNeeded(boolean wasAtBottom) {
        if (wasAtBottom) {
            scrollToBottom();
        }
    }

    private static boolean looksLikeCode(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String trimmed = text.trim();
        if (trimmed.startsWith("#!") || trimmed.startsWith("<?xml")) return true;
        
        String[] lines = text.split("\n");
        int codeScore = 0;
        int totalLines = 0;
        
        java.util.regex.Pattern pKeywords = java.util.regex.Pattern.compile(
            "\\b(public|private|protected|class|interface|enum|extends|implements|import|package|static|final|volatile|transient|synchronized|native|fun|val|var|const|let|function|new|return|if|else|for|while|do|switch|case|break|continue|throw|throws|try|catch|finally|this|super|instanceof|void|int|double|float|long|short|byte|char|boolean|true|false|null)\\b"
        );
        java.util.regex.Pattern pBracesOrIndents = java.util.regex.Pattern.compile(
            "[{};]|\\s{4}|\\t"
        );
        
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            totalLines++;
            if (pKeywords.matcher(l).find() || pBracesOrIndents.matcher(line).find()) {
                codeScore++;
            }
        }
        
        if (totalLines > 0) {
            float ratio = (float) codeScore / totalLines;
            return ratio >= 0.6f && totalLines >= 2;
        }
        return false;
    }

    private static String detectLanguage(String code, String headerText) {
        if (headerText != null) {
            String lowerHeader = headerText.toLowerCase();
            if (lowerHeader.contains(".java")) return "java";
            if (lowerHeader.contains(".kt")) return "kotlin";
            if (lowerHeader.contains(".py")) return "python";
            if (lowerHeader.contains(".sh") || lowerHeader.contains("bash") || lowerHeader.contains("command")) return "bash";
            if (lowerHeader.contains(".json")) return "json";
            if (lowerHeader.contains(".xml") || lowerHeader.contains(".html")) return "xml";
            if (lowerHeader.contains(".gradle")) return "groovy";
            if (lowerHeader.contains(".md")) return "markdown";
            if (lowerHeader.contains(".cpp") || lowerHeader.contains(".h") || lowerHeader.contains(".c")) return "cpp";
            if (lowerHeader.contains(".js") || lowerHeader.contains(".ts")) return "javascript";
        }
        
        String trimmed = code.trim();
        if (trimmed.startsWith("#!") || trimmed.startsWith("echo ") || trimmed.startsWith("git ") || trimmed.startsWith("cd ")) {
            return "bash";
        }
        if (trimmed.contains("import java.") || trimmed.contains("public class ") || trimmed.contains("private final ") || trimmed.contains("@Override")) {
            return "java";
        }
        if (trimmed.contains("import kotlin.") || trimmed.contains("fun ") || trimmed.contains("val ") || trimmed.contains("var ")) {
            return "kotlin";
        }
        if (trimmed.contains("def ") || trimmed.contains("import os") || trimmed.contains("import sys") || trimmed.contains("print(")) {
            return "python";
        }
        if (trimmed.startsWith("<?xml") || (trimmed.startsWith("<") && trimmed.contains("xmlns:"))) {
            return "xml";
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}") && trimmed.contains("\":")) {
            return "json";
        }
        
        return "";
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private static String replaceSpacesOutsideTags(String htmlCode) {
        StringBuilder sb = new StringBuilder();
        int len = htmlCode.length();
        boolean inTag = false;
        for (int i = 0; i < len; i++) {
            char c = htmlCode.charAt(i);
            if (c == '<') {
                inTag = true;
                sb.append(c);
            } else if (c == '>') {
                inTag = false;
                sb.append(c);
            } else if (c == ' ' && !inTag) {
                sb.append("&nbsp;");
            } else if (c == '\t' && !inTag) {
                sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String highlightCode(String escapedCode, String lang) {
        if (lang == null) lang = "";
        lang = lang.toLowerCase().trim();

        List<String> protectedTokens = new ArrayList<>();
        boolean isHashComment = "python".equals(lang) || "bash".equals(lang) || "yaml".equals(lang) || "dockerfile".equals(lang);
        
        String regex;
        if (isHashComment) {
            regex = "(\"(?:\\\\\"|[^\"])*\")|('(?:\\\\'|[^'])*')|(#.*)";
        } else if ("xml".equals(lang) || "html".equals(lang)) {
            regex = "(<!--.*?-->)|(\"(?:\\\\\"|[^\"])*\")|('(?:\\\\'|[^'])*')";
        } else {
            regex = "(\"(?:\\\\\"|[^\"])*\")|('(?:\\\\'|[^'])*')|(\\/\\/.*)|(\\/\\*.*?\\*\\/)";
        }
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(escapedCode);
        
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            sb.append(escapedCode, lastEnd, matcher.start());
            String match = matcher.group();
            String styled;
            if (match.startsWith("//") || match.startsWith("#") || match.startsWith("/*") || match.startsWith("<!--")) {
                styled = "<font color=\"#6272A4\"><i>" + match + "</i></font>";
            } else {
                styled = "<font color=\"#50FA7B\">" + match + "</font>";
            }
            protectedTokens.add(styled);
            sb.append("%%TOKEN_PLACEHOLDER_").append(protectedTokens.size() - 1).append("%%");
            lastEnd = matcher.end();
        }
        sb.append(escapedCode, lastEnd, escapedCode.length());
        String codeText = sb.toString();

        if ("java".equals(lang) || "kotlin".equals(lang) || "groovy".equals(lang) || "cpp".equals(lang) || "javascript".equals(lang) || "typescript".equals(lang)) {
            codeText = codeText.replaceAll(
                "\\b(public|private|protected|class|interface|enum|extends|implements|import|package|static|final|volatile|transient|synchronized|native|fun|val|var|const|let|function|new|return|if|else|for|while|do|switch|case|break|continue|throw|throws|try|catch|finally|this|super|instanceof|void|int|double|float|long|short|byte|char|boolean|true|false|null)\\b",
                "<font color=\"#FF79C6\"><b>$1</b></font>"
            );
            codeText = codeText.replaceAll(
                "\\b(String|Override|Integer|Double|Float|Long|Boolean|Character|Byte|Short|List|ArrayList|Map|HashMap|Set|HashSet|View|TextView|LinearLayout|ViewGroup|Context|Intent|Activity|Bundle|Log|Build)\\b",
                "<font color=\"#8BE9FD\">$1</font>"
            );
            codeText = codeText.replaceAll(
                "(@[a-zA-Z0-9_]+)",
                "<font color=\"#F1FA8C\">$1</font>"
            );
        } else if ("python".equals(lang)) {
            codeText = codeText.replaceAll(
                "\\b(def|class|return|if|elif|else|for|while|break|continue|in|is|not|and|or|import|from|as|try|except|finally|raise|assert|global|nonlocal|lambda|pass|with|yield|None|True|False)\\b",
                "<font color=\"#FF79C6\"><b>$1</b></font>"
            );
            codeText = codeText.replaceAll(
                "\\b(print|len|str|int|float|list|dict|set|tuple|range|enumerate|zip|open|sum|max|min|abs|map|filter|any|all)\\b",
                "<font color=\"#8BE9FD\">$1</font>"
            );
        } else if ("bash".equals(lang)) {
            codeText = codeText.replaceAll(
                "\\b(if|then|elif|else|fi|for|in|do|done|while|until|case|esac|break|continue|exit|return|function|local|export|alias|echo|cd|ls|grep|sed|awk|cat|mkdir|rm|cp|mv|chmod|chown|ssh|git|adb|python|python3|pip|npm|npx|gradle|gradlew)\\b",
                "<font color=\"#FF79C6\"><b>$1</b></font>"
            );
            codeText = codeText.replaceAll(
                "(\\$[a-zA-Z_][a-zA-Z0-9_]*)",
                "<font color=\"#BD93F9\">$1</font>"
            );
        } else if ("json".equals(lang)) {
            codeText = codeText.replaceAll(
                "\\b(true|false|null)\\b",
                "<font color=\"#FF79C6\">$1</font>"
            );
        } else if ("xml".equals(lang) || "html".equals(lang)) {
            codeText = codeText.replaceAll(
                "(&lt;\\/?[a-zA-Z0-9:-]+)",
                "<font color=\"#FF79C6\"><b>$1</b></font>"
            );
            codeText = codeText.replaceAll(
                "(&gt;)",
                "<font color=\"#FF79C6\"><b>$1</b></font>"
            );
        }

        codeText = codeText.replaceAll(
            "\\b([0-9]+)\\b",
            "<font color=\"#BD93F9\">$1</font>"
        );

        for (int i = 0; i < protectedTokens.size(); i++) {
            codeText = codeText.replace("%%TOKEN_PLACEHOLDER_" + i + "%%", protectedTokens.get(i));
        }

        return codeText;
    }

    private static String renderAndHighlightCodeBlock(String code, String lang) {
        if (lang == null || lang.isEmpty()) {
            lang = detectLanguage(code, "");
        }
        String escaped = escapeHtml(code);
        String highlighted = highlightCode(escaped, lang);
        String formatted = replaceSpacesOutsideTags(highlighted)
                                      .replace("\n", "<br/>");
        return "<br/><font face=\"monospace\"><tt>" + formatted + "</tt></font><br/>";
    }

    private static String renderPlainMonospace(String text) {
        String escaped = escapeHtml(text);
        String formatted = replaceSpacesOutsideTags(escaped)
                                      .replace("\n", "<br/>");
        return "<font face=\"monospace\"><tt>" + formatted + "</tt></font>";
    }

    private static android.text.Spanned renderMarkdown(String markdown) {
        if (markdown == null) return new android.text.SpannableString("");

        // --- Step 1: Extract fenced code blocks (```...```) and protect them ---
        java.util.List<String> renderedCodeBlocks = new java.util.ArrayList<>();
        String text = markdown;

        int index = 0;
        while (true) {
            int startIdx = text.indexOf("```", index);
            if (startIdx == -1) break;

            int endIdx = text.indexOf("```", startIdx + 3);
            String blockContent;
            String lang = "";
            int nextIndex;

            if (endIdx != -1) {
                blockContent = text.substring(startIdx + 3, endIdx);
                nextIndex = endIdx + 3;
            } else {
                blockContent = text.substring(startIdx + 3);
                nextIndex = text.length();
            }

            int firstNewline = blockContent.indexOf('\n');
            if (firstNewline != -1) {
                lang = blockContent.substring(0, firstNewline).trim();
                blockContent = blockContent.substring(firstNewline + 1);
            }

            String rendered = renderAndHighlightCodeBlock(blockContent, lang);
            renderedCodeBlocks.add(rendered);

            String placeholder = "%%CODE_BLOCK_" + (renderedCodeBlocks.size() - 1) + "%%";
            text = text.substring(0, startIdx) + placeholder + text.substring(nextIndex);
            index = startIdx + placeholder.length();
        }

        // --- Step 2: Extract inline code (`...`) and protect them ---
        java.util.List<String> inlineCodeBlocks = new java.util.ArrayList<>();
        java.util.regex.Matcher inlineMatcher = java.util.regex.Pattern.compile("`([^`]+)`").matcher(text);
        StringBuilder sbInline = new StringBuilder();
        int lastEndInline = 0;
        while (inlineMatcher.find()) {
            sbInline.append(text, lastEndInline, inlineMatcher.start());
            String codeContent = inlineMatcher.group(1);
            String escaped = escapeHtml(codeContent);
            String rendered = "<font face=\"monospace\" color=\"#E6E6FA\"><tt>" + escaped + "</tt></font>";
            inlineCodeBlocks.add(rendered);
            sbInline.append("%%INLINE_CODE_").append(inlineCodeBlocks.size() - 1).append("%%");
            lastEndInline = inlineMatcher.end();
        }
        sbInline.append(text, lastEndInline, text.length());
        text = sbInline.toString();

        // --- Step 3: Strip <tts> tags ---
        text = text.replaceAll("<tts>", "").replaceAll("</tts>", "");

        // --- Step 4: Apply markdown formatting ---
        // Headers (process most specific first)
        String html = text.replaceAll("(?m)^###\\s+(.*?)\\r?$", "<br/><font color=\"#00F2FE\"><b>$1</b></font><br/>");
        html = html.replaceAll("(?m)^##\\s+(.*?)\\r?$", "<br/><font color=\"#00F2FE\"><b><big>$1</big></b></font><br/>");
        html = html.replaceAll("(?m)^#\\s+(.*?)\\r?$", "<br/><font color=\"#00F2FE\"><b><big><big>$1</big></big></b></font><br/>");

        // Bold (**text** and __text__) - must process before italic
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        html = html.replaceAll("__(.+?)__", "<b>$1</b>");

        // Italic (*text* and _text_) - use word boundary-aware patterns
        // Only match *text* when not preceded/followed by another *
        html = html.replaceAll("(?<![*])\\*([^*]+)\\*(?![*])", "<i>$1</i>");
        // Only match _text_ when preceded by whitespace or start-of-line and followed by whitespace/punctuation/end
        // This prevents matching underscores inside identifiers like replace_file_content
        html = html.replaceAll("(?<=^|\\s)_([^_]+)_(?=$|\\s|[.,;:!?)])", "<i>$1</i>");

        // Bullets (lines starting with - or * followed by space)
        html = html.replaceAll("(?m)^\\-\\s+(.*?)\\r?$", "&#8226; $1<br/>");
        html = html.replaceAll("(?m)^\\*\\s+(.*?)\\r?$", "&#8226; $1<br/>");

        // Numbered lists
        html = html.replaceAll("(?m)^(\\d+)\\.\\s+(.*?)\\r?$", "$1. $2<br/>");

        // Links: [text](url) -> <a href="url">text</a>
        html = html.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");

        // Convert newlines
        html = html.replaceAll("\\n", "<br/>");
        html = html.replaceAll("(<br/>\\s*){3,}", "<br/><br/>");

        // --- Step 5: Restore placeholders ---
        for (int i = 0; i < inlineCodeBlocks.size(); i++) {
            html = html.replace("%%INLINE_CODE_" + i + "%%", inlineCodeBlocks.get(i));
        }
        for (int i = 0; i < renderedCodeBlocks.size(); i++) {
            html = html.replace("%%CODE_BLOCK_" + i + "%%", renderedCodeBlocks.get(i));
        }

        android.text.Spanned spanned;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            spanned = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY);
        } else {
            spanned = android.text.Html.fromHtml(html);
        }
        return makeSpansInterceptable(spanned);
    }

    private static android.text.Spanned makeSpansInterceptable(android.text.Spanned spanned) {
        if (spanned == null) return null;
        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(spanned);
        android.text.style.URLSpan[] spans = ssb.getSpans(0, ssb.length(), android.text.style.URLSpan.class);
        for (android.text.style.URLSpan span : spans) {
            int start = ssb.getSpanStart(span);
            int end = ssb.getSpanEnd(span);
            int flags = ssb.getSpanFlags(span);
            final String url = span.getURL();
            
            android.text.style.ClickableSpan customSpan = new android.text.style.ClickableSpan() {
                @Override
                public void onClick(android.view.View widget) {
                    android.content.Context context = widget.getContext();
                    handleLinkClick(context, url);
                }
                
                @Override
                public void updateDrawState(android.text.TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(true);
                    ds.setColor(android.graphics.Color.parseColor("#00F2FE"));
                }
            };
            
            ssb.removeSpan(span);
            ssb.setSpan(customSpan, start, end, flags);
        }
        return ssb;
    }

    private static void handleLinkClick(android.content.Context context, String url) {
        openFileOrUrl(context, url);
    }

    private static void openFileOrUrl(android.content.Context context, String url) {
        if (url == null || url.trim().isEmpty()) return;
        Log.d(TAG, "openFileOrUrl: " + url);
        
        // 1. Check if Chrome target
        boolean isChromeTarget = false;
        if (url.startsWith("http://") || url.startsWith("https://")) {
            isChromeTarget = true;
        } else {
            String lower = url.toLowerCase();
            int qIdx = lower.indexOf('?');
            if (qIdx != -1) lower = lower.substring(0, qIdx);
            int hIdx = lower.indexOf('#');
            if (hIdx != -1) lower = lower.substring(0, hIdx);
            
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") 
                    || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")
                    || lower.endsWith(".pdf")) {
                isChromeTarget = true;
            }
        }
        
        if (isChromeTarget) {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                try {
                    android.content.Intent intent = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url)
                    );
                    intent.setPackage("com.android.chrome");
                    if (!(context instanceof android.app.Activity)) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    context.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open web URL in Chrome: " + url, e);
                    try {
                        android.content.Intent intent = new android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)
                        );
                        if (!(context instanceof android.app.Activity)) {
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
                        context.startActivity(intent);
                    } catch (Exception ex) {
                        Toast.makeText(context, "Error opening link", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                // Local file target (image/PDF) for Chrome
                String filePath = url;
                if (filePath.startsWith("file://")) {
                    filePath = filePath.substring(7);
                }
                
                int hashIdx = filePath.indexOf('#');
                if (hashIdx != -1) {
                    filePath = filePath.substring(0, hashIdx);
                }
                
                final String finalFilePath = filePath;
                final String filename = new java.io.File(filePath).getName();
                
                if (filePath.startsWith("/data/data/com.termux/files/")) {
                    // It's in Termux private dir. Copy to /sdcard/Download/ToggleTalk/ first
                    final String targetPath = "/sdcard/Download/ToggleTalk/" + filename;
                    
                    Intent runCommandIntent = new Intent();
                    runCommandIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
                    runCommandIntent.setAction("com.termux.RUN_COMMAND");
                    runCommandIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
                    runCommandIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{
                            "-c", "mkdir -p /sdcard/Download/ToggleTalk && cp '" + finalFilePath + "' /sdcard/Download/ToggleTalk/"
                    });
                    runCommandIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
                    runCommandIntent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
                    
                    Intent callbackIntent = new Intent(ACTION_COPY_ARTIFACT);
                    callbackIntent.putExtra("target_path", targetPath);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        flags |= PendingIntent.FLAG_MUTABLE;
                    }
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1001, callbackIntent, flags);
                    runCommandIntent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent);
                    
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(runCommandIntent);
                        } else {
                            context.startService(runCommandIntent);
                        }
                        Toast.makeText(context, "Copying and opening in Chrome...", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to copy file via Termux", e);
                        Toast.makeText(context, "Failed to copy file", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Already public. Open directly in Chrome using FileProvider.
                    try {
                        java.io.File file = new java.io.File(filePath);
                        android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "com.toggletalk.android.fileprovider",
                                file
                        );
                        android.content.Intent chromeIntent = new android.content.Intent(
                                android.content.Intent.ACTION_VIEW
                        );
                        chromeIntent.setDataAndType(fileUri, getMimeType(filePath));
                        chromeIntent.setPackage("com.android.chrome");
                        chromeIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        if (!(context instanceof android.app.Activity)) {
                            chromeIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
                        context.startActivity(chromeIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to open in Chrome directly: " + filePath, e);
                        Toast.makeText(context, "Failed to open in Chrome", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } else {
            // Termux target (markdown, text, and other text files)
            String filePath = url;
            if (filePath.startsWith("file://")) {
                filePath = filePath.substring(7);
            }
            
            String cleanPath = filePath;
            int hashIdx = filePath.indexOf('#');
            if (hashIdx != -1) {
                cleanPath = filePath.substring(0, hashIdx);
            }
            
            if (cleanPath.startsWith("/data/data/com.termux/files/") || new java.io.File(cleanPath).isAbsolute()) {
                Intent runCommandIntent = new Intent();
                runCommandIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
                runCommandIntent.setAction("com.termux.RUN_COMMAND");
                runCommandIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/glow");
                runCommandIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-p", cleanPath});
                runCommandIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
                runCommandIntent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(runCommandIntent);
                    } else {
                        context.startService(runCommandIntent);
                    }
                    Toast.makeText(context, "Opening in glow...", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch glow in Termux", e);
                    Toast.makeText(context, "Failed to launch glow in Termux", Toast.LENGTH_SHORT).show();
                }
            } else {
                try {
                    android.content.Intent intent = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url)
                    );
                    if (!(context instanceof android.app.Activity)) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    context.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open link: " + url, e);
                    Toast.makeText(context, "No app found to open link", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private static String getMimeType(String path) {
        String type = "*/*";
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(path);
        if (extension == null || extension.isEmpty()) {
            int lastDot = path.lastIndexOf('.');
            if (lastDot != -1) {
                extension = path.substring(lastDot + 1);
            }
        }
        if (extension != null) {
            String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            if (mime != null) {
                type = mime;
            }
        }
        return type;
    }

    private void applyWakeLockSetting(boolean enabled) {
        if (enabled) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Log.d(TAG, "FLAG_KEEP_SCREEN_ON added to window");
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Log.d(TAG, "FLAG_KEEP_SCREEN_ON cleared from window");
        }
    }

    private void openArtifactsPopup() {
        if (mArtifactsPopupRoot != null) {
            mArtifactsPopupRoot.setVisibility(View.VISIBLE);
            populateArtifactsList();
        }
    }

    private void closeArtifactsPopup() {
        if (mArtifactsPopupRoot != null) {
            mArtifactsPopupRoot.setVisibility(View.GONE);
        }
    }

    private void populateArtifactsList() {
        if (mLayoutArtifactsList == null) return;
        mLayoutArtifactsList.removeAllViews();

        List<ArtifactItem> artifacts = extractArtifacts();
        if (artifacts.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No artifacts found in this session.");
            emptyTv.setTextColor(Color.parseColor("#80E6E6FA"));
            emptyTv.setTextSize(14);
            emptyTv.setGravity(android.view.Gravity.CENTER);
            int padding = (int) (16 * getResources().getDisplayMetrics().density);
            emptyTv.setPadding(padding, padding, padding, padding);
            mLayoutArtifactsList.addView(emptyTv);
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int verticalPadding = (int) (12 * density);
        int horizontalPadding = (int) (16 * density);

        for (final ArtifactItem item : artifacts) {
            final android.widget.LinearLayout itemLayout = new android.widget.LinearLayout(this);
            itemLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
            itemLayout.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
            itemLayout.setClickable(true);
            itemLayout.setFocusable(true);

            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(Color.parseColor("#12000000"));
            gd.setCornerRadius(density * 8);
            gd.setStroke((int) density, Color.parseColor("#1AE6E6FA"));
            itemLayout.setBackground(gd);

            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, (int) (10 * density));
            itemLayout.setLayoutParams(lp);

            TextView titleTv = new TextView(this);
            titleTv.setText(item.label);
            titleTv.setTextColor(Color.parseColor("#E6E6FA"));
            titleTv.setTextSize(14);
            titleTv.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView urlTv = new TextView(this);
            String displayUrl = item.url;
            if (displayUrl.startsWith("file://")) {
                displayUrl = displayUrl.substring(7);
            }
            urlTv.setText(displayUrl);
            urlTv.setTextColor(Color.parseColor("#80E6E6FA"));
            urlTv.setTextSize(11);
            urlTv.setPadding(0, (int) (4 * density), 0, 0);

            itemLayout.addView(titleTv);
            itemLayout.addView(urlTv);

            itemLayout.setOnClickListener(v -> {
                closeArtifactsPopup();
                openFileOrUrl(MainActivity.this, item.url);
            });

            mLayoutArtifactsList.addView(itemLayout);
        }
    }

    private List<ArtifactItem> extractArtifacts() {
        List<ArtifactItem> artifacts = new ArrayList<>();
        java.util.Set<String> seenUrls = new java.util.HashSet<>();
        if (mCurrentSessionHistory == null) return artifacts;

        for (int i = 0; i < mCurrentSessionHistory.length(); i++) {
            try {
                org.json.JSONObject msg = mCurrentSessionHistory.getJSONObject(i);
                String role = msg.optString("role", "");
                if (!"user".equals(role)) {
                    String text = msg.optString("text", "");
                    if (text == null || text.isEmpty()) continue;
                    
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)").matcher(text);
                    while (matcher.find()) {
                        String label = matcher.group(1);
                        String url = matcher.group(2);
                        if (url != null && !seenUrls.contains(url)) {
                            seenUrls.add(url);
                            artifacts.add(new ArtifactItem(label, url));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting artifacts", e);
            }
        }
        return artifacts;
    }

    private final BroadcastReceiver mCopyArtifactReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_COPY_ARTIFACT.equals(intent.getAction())) {
                String targetPath = intent.getStringExtra("target_path");
                Log.d(TAG, "Copy artifact completed, launching Chrome for: " + targetPath);
                try {
                    java.io.File file = new java.io.File(targetPath);
                    android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "com.toggletalk.android.fileprovider",
                            file
                    );
                    
                    android.content.Intent chromeIntent = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW
                    );
                    
                    chromeIntent.setDataAndType(fileUri, getMimeType(targetPath));
                    chromeIntent.setPackage("com.android.chrome");
                    chromeIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    chromeIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    context.startActivity(chromeIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open in Chrome: " + targetPath, e);
                    try {
                        java.io.File file = new java.io.File(targetPath);
                        android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "com.toggletalk.android.fileprovider",
                                file
                        );
                        android.content.Intent fallbackIntent = new android.content.Intent(
                                android.content.Intent.ACTION_VIEW
                        );
                        fallbackIntent.setDataAndType(fileUri, getMimeType(targetPath));
                        fallbackIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        fallbackIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(fallbackIntent);
                    } catch (Exception ex) {
                        Toast.makeText(context, "Failed to open file in external app", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    };

    // --- Query Termux Antigravity Sessions list via RUN_COMMAND ---

    private void queryTermuxSessions() {
        mPbRightDrawerLoading.setVisibility(View.VISIBLE);
        mTvEmptySessions.setVisibility(View.GONE);
        mHelper.queryTermuxSessions();
    }

    private void parseAndPopulateSessions(String stdout) {
        List<SessionItem> list = new ArrayList<>();
        try {
            org.json.JSONArray array = new org.json.JSONArray(stdout);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject obj = array.getJSONObject(i);
                String id = obj.getString("id");
                String title = obj.getString("title");
                list.add(new SessionItem(id, title));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing sessions JSON", e);
        }

        mSessionsList.clear();
        mSessionsList.addAll(list);
        mSessionsAdapter.notifyDataSetChanged();
        updateActiveSessionLabel();
        
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

    private class SessionAdapter extends ArrayAdapter<SessionItem> {
        public SessionAdapter(Context context, List<SessionItem> sessions) {
            super(context, R.layout.item_directory, sessions);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_directory, parent, false);
            }
            SessionItem item = getItem(position);
            TextView tvDirName = convertView.findViewById(R.id.tv_dir_name);
            View viewIndicator = convertView.findViewById(R.id.view_indicator);
            ImageView imgFolder = convertView.findViewById(R.id.img_folder);
            
            tvDirName.setText(item.title);
            
            boolean isSelected = item.id.equals(mSelectedSessionId);
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
        mHelper.queryTermuxDirectories();
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

    private void killAllSessions() {
        new AlertDialog.Builder(this)
            .setTitle("Kill All Sessions")
            .setMessage("Are you sure you want to terminate all antigravity sessions and clear the TTS queue?")
            .setPositiveButton("Yes", (dialog, which) -> {
                Intent intent = new Intent(this, ToggleTalkService.class);
                intent.setAction(ToggleTalkService.ACTION_KILL_ALL);
                startService(intent);
                addSystemMessage("⛔ All sessions killed by user.", "#FF416C");
                closeSettings();
            })
            .setNegativeButton("No", null)
            .show();
    }

    // --- App States Visual Management ---

    private void onStateChanged(String state, String text) {
        boolean wasAtBottom = isScrolledToBottom();
        mCurrentState = state;
        if (mCbMockMode != null) {
            mCbMockMode.setChecked(mBypassAntigravity);
        }
        
        if (mUnresolvedQueueManager != null) {
            mUnresolvedQueueManager.onStateOrQueueChanged(mCurrentState, mPromptQueue);
        }

        clearAllAnimations();

        switch (state) {
            case "RECORDING":
                Log.d(TAG, "onStateChanged: RECORDING");
                mIsResuming = false;
                mIsAgentActive = false;
                mTvStatus.setText("LISTENING...");
                mTvStatus.setTextColor(Color.parseColor("#FF2D55"));
                mBtnMic.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF2D55")));
                mBtnMic.setImageTintList(ColorStateList.valueOf(Color.parseColor("#FFFFFF")));
                if (mLayoutThinkingContainer != null) {
                    mLayoutThinkingContainer.setVisibility(View.GONE);
                }
                
                // Keep screen on
                getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                startPulseAnimation(mRingInner, 1200, 0);
                startPulseAnimation(mRingMiddle, 1200, 400);
                startPulseAnimation(mRingOuter, 1200, 800);
                break;

            case "THINKING":
                Log.d(TAG, "onStateChanged: THINKING. Queue size: " + mPromptQueue.size());
                mIsResuming = false;
                mIsAgentActive = true;
                if ("Transcribing...".equals(text)) {
                    mTvStatus.setText("TRANSCRIBING...");
                } else {
                    mTvStatus.setText("THINKING...");
                }
                mTvStatus.setTextColor(Color.parseColor("#00F2FE"));
                mBtnMic.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0D1A2E")));
                mBtnMic.setImageTintList(ColorStateList.valueOf(Color.parseColor("#00F2FE")));
                if (mLayoutThinkingContainer != null) {
                    if ("Transcribing...".equals(text)) {
                        mLayoutThinkingContainer.setVisibility(View.GONE);
                    } else {
                        Log.d(TAG, "onStateChanged: Setting ThinkingContainer VISIBLE");
                        mLayoutThinkingContainer.setVisibility(View.VISIBLE);
                        mLayoutThinkingContainer.bringToFront();
                    }
                }
                
                // Keep screen on
                getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                // After loading a session, mActiveAgentTextView is null so new bubbles are fresh
                if (!"Transcribing...".equals(text) && !"Streaming updates...".equals(text)) {
                    if (mNewChatStartedBubble != null && mChatContainer != null) {
                        mChatContainer.removeView(mNewChatStartedBubble);
                        mNewChatStartedBubble = null;
                    }
                    // If we get a prompt that we didn't add manually (e.g. from voice transcription)
                    if (!text.isEmpty() && !text.equals(mUserPrompt) && !text.equals("Listening...") && !text.equals("Thinking...")) {
                        mUserPrompt = text;
                        addUserBubble(mUserPrompt);
                        addAgentBubble("...");
                    }
                }

                mRingInner.setAlpha(0f);
                mRingMiddle.setAlpha(0f);
                mRingOuter.setAlpha(0f);
                break;

            case "SPEAKING":
                Log.d(TAG, "onStateChanged: SPEAKING");
                mIsAgentActive = true;
                // User requested: "We dont need speaking displayed in the header"
                // So we keep it as THINKING or previous state, or just ensure it's not changed to SPEAKING.
                // We'll keep the cyan color to indicate activity.
                mTvStatus.setTextColor(Color.parseColor("#00F2FE"));
                mBtnMic.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0D1A2E")));
                mBtnMic.setImageTintList(ColorStateList.valueOf(Color.parseColor("#00F2FE")));
                
                if (mLayoutThinkingContainer != null) {
                    Log.d(TAG, "onStateChanged: SPEAKING - Keeping ThinkingContainer VISIBLE");
                    mLayoutThinkingContainer.setVisibility(View.VISIBLE);
                    mLayoutThinkingContainer.bringToFront();
                }
                
                // Keep screen on
                getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                if (mDisplayedStepKeys.isEmpty() && !"Speaking...".equals(text) && !"Streaming updates...".equals(text) && !text.equals(mUserPrompt)) {
                    if (mIsResuming) {
                        if (mActiveAgentTextView == null) {
                            addAgentBubble(text);
                        } else {
                            mActiveAgentTextView.setText(renderMarkdown(text));
                        }
                    } else {
                        streamAgentResponse(text);
                    }
                }
                mIsResuming = false;

                mRingInner.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CD964")));
                mRingMiddle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CD964")));
                startBreatheAnimation(mRingInner, 1.0f, 1.25f, 0.2f, 0.6f, 1000);
                startBreatheAnimation(mRingMiddle, 1.0f, 1.4f, 0.1f, 0.4f, 1000);
                break;

            case "FINISHED":
                Log.d(TAG, "onStateChanged: FINISHED");
                mIsResuming = false;
                mIsAgentActive = false;
                mTvStatus.setText("FINISHED");
                mTvStatus.setTextColor(Color.parseColor("#4CD964"));
                if (mLayoutThinkingContainer != null) {
                    mLayoutThinkingContainer.setVisibility(View.GONE);
                }
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                break;

            case "IDLE":
            default:
                Log.d(TAG, "onStateChanged: IDLE/DEFAULT");
                mIsResuming = false;
                mIsAgentActive = false;
                mTvStatus.setText("IDLE");
                mTvStatus.setTextColor(Color.parseColor("#8A8A8F"));
                mBtnMic.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2D1F54")));
                mBtnMic.setImageTintList(ColorStateList.valueOf(Color.parseColor("#E6E6FA")));
                if (mLayoutThinkingContainer != null) {
                    mLayoutThinkingContainer.setVisibility(View.GONE);
                }
                
                // Clear keep screen on
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                mRingInner.setAlpha(0f);
                mRingMiddle.setAlpha(0f);
                mRingOuter.setAlpha(0f);

                if (mActiveAgentTextView != null && "...".equals(mActiveAgentTextView.getText().toString())) {
                    mActiveAgentTextView.setText("No response received.");
                }

                // Auto-continue: if a session is selected, always continue
                mContinueSession = (mSelectedSessionId != null && !mSelectedSessionId.isEmpty());

                // Clear user prompt cache so same text can be sent again
                mUserPrompt = "";
                break;
        }

        updateSendButtonState();
        scrollToBottomIfNeeded(wasAtBottom);
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

    private void writeSessionToCache(String sessionId, String jsonContent) {
        if (sessionId == null || sessionId.isEmpty() || jsonContent == null || jsonContent.isEmpty()) {
            return;
        }
        try {
            java.io.File cacheDir = new java.io.File(getCacheDir(), "session_history_cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            java.io.File cacheFile = new java.io.File(cacheDir, sessionId + ".json");
            java.io.FileWriter writer = new java.io.FileWriter(cacheFile);
            writer.write(jsonContent);
            writer.close();
            Log.d(TAG, "Cached session history for: " + sessionId);
        } catch (Exception e) {
            Log.e(TAG, "Error writing session to cache", e);
        }
    }

    private void clearSessionHistoryCache() {
        // Delete cache files
        java.io.File cacheDir = new java.io.File(getCacheDir(), "session_history_cache");
        if (cacheDir.exists()) {
            java.io.File[] files = cacheDir.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    f.delete();
                }
            }
        }
        
        // Delete SD card history files
        try {
            java.io.File sdDir = new java.io.File("/sdcard/Android/media/com.toggletalk.android");
            if (sdDir.exists()) {
                java.io.File sessionHist = new java.io.File(sdDir, "session_history.json");
                if (sessionHist.exists()) sessionHist.delete();
                java.io.File streamHist = new java.io.File(sdDir, "stream_history.json");
                if (streamHist.exists()) streamHist.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting SD card history files", e);
        }
        
        Toast.makeText(this, "Session history cache cleared", Toast.LENGTH_SHORT).show();
        
        // Clear active session chat, reset memory variables and re-pull from transcript
        if (mSelectedSessionId != null && !mSelectedSessionId.isEmpty()) {
            loadSessionHistory(mSelectedSessionId);
        } else {
            if (mChatContainer != null) {
                mChatContainer.removeAllViews();
            }
            mNewChatStartedBubble = null;
            mActiveAgentTextView = null;
            mUserPrompt = "";
            mDisplayedStepKeys.clear();
            mCurrentSessionHistory = new org.json.JSONArray();
            mLastStreamedAgentText = "";
            mExpandedIndices.clear();
            mCollapsedIndices.clear();
            mExpandedSummaryIndices.clear();
            mRenderedTextCache.clear();
            mCachedRawTexts.clear();
        }
    }

    private static final int PICK_FILE_REQUEST = 2002;
    private static final int PICK_CAMERA_REQUEST = 2003;

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select File to Upload"), PICK_FILE_REQUEST);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openCameraPicker() {
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(intent, PICK_CAMERA_REQUEST);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Camera app not found.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            android.net.Uri uri = data.getData();
            handleSelectedFile(uri);
        } else if (requestCode == PICK_CAMERA_REQUEST && resultCode == RESULT_OK && data != null) {
            // For simple implementation, if data.getExtras().get("data") is present, it's a thumbnail bitmap
            android.graphics.Bitmap bitmap = (android.graphics.Bitmap) data.getExtras().get("data");
            if (bitmap != null) {
                handleCapturedBitmap(bitmap);
            }
        }
    }

    private void handleCapturedBitmap(android.graphics.Bitmap bitmap) {
        try {
            java.io.File cacheFile = new java.io.File(getCacheDir(), "camera_capture_" + System.currentTimeMillis() + ".jpg");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(cacheFile);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            
            // Now handle it like a selected file
            handleSelectedFile(android.net.Uri.fromFile(cacheFile));
        } catch (Exception e) {
            Log.e(TAG, "Error saving captured bitmap", e);
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSelectedFile(android.net.Uri uri) {
        try {
            String fileName = getFileName(uri);
            if (fileName == null) {
                fileName = "uploaded_file_" + System.currentTimeMillis();
            }
            
            String destDirStr = "/data/data/com.termux/files/home";
            if (mTargetDirectory != null && !mTargetDirectory.isEmpty() && !"Home".equals(mTargetDirectory)) {
                if (mTargetDirectory.startsWith("/")) {
                    destDirStr = mTargetDirectory;
                } else {
                    destDirStr = "/data/data/com.termux/files/home/" + mTargetDirectory;
                }
            }
            
            java.io.File destDir = new java.io.File(destDirStr);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            
            java.io.File destFile = new java.io.File(destDir, fileName);
            
            java.io.InputStream is = getContentResolver().openInputStream(uri);
            java.io.OutputStream os = new java.io.FileOutputStream(destFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            
            Toast.makeText(this, "Uploaded " + fileName + " to " + mTargetDirectory, Toast.LENGTH_LONG).show();
            
            String currentText = mEtMessage.getText().toString();
            String fileRef = "Passed file: [" + fileName + "](file://" + destFile.getAbsolutePath() + ")";
            if (currentText.isEmpty()) {
                mEtMessage.setText(fileRef + "\n");
            } else {
                mEtMessage.setText(currentText + "\n" + fileRef + "\n");
            }
            mEtMessage.setSelection(mEtMessage.getText().length());
            
        } catch (Exception e) {
            Log.e(TAG, "Error uploading file", e);
            Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getFileName(android.net.Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) {
                        result = cursor.getString(idx);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}
