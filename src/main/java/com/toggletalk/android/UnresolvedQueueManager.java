package com.toggletalk.android;

import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.graphics.Color;
import java.util.List;

public class UnresolvedQueueManager {

    private final MainActivity mActivity;
    private final LinearLayout mLayoutUnresolvedActions;
    private final View mBtnSend;
    private final EditText mEtMessage;
    private final GlowAnimationHelper mGlowHelper;
    private final View mQueueContainer;
    
    private final Button mBtnResume;
    private final Button mBtnDelete;
    private final Button mBtnCombine;
    private final Button mBtnAdd;
    
    private boolean mIsUnresolved = false;
    private List<String> mCurrentQueue;

    public UnresolvedQueueManager(MainActivity activity, View root, View btnSend, EditText etMessage, View queueContainer) {
        mActivity = activity;
        mBtnSend = btnSend;
        mEtMessage = etMessage;
        mQueueContainer = queueContainer;
        mGlowHelper = new GlowAnimationHelper(queueContainer);
        
        mLayoutUnresolvedActions = root.findViewById(R.id.layout_unresolved_actions);
        mBtnResume = root.findViewById(R.id.btn_queue_resume);
        mBtnDelete = root.findViewById(R.id.btn_queue_delete);
        mBtnCombine = root.findViewById(R.id.btn_queue_combine);
        mBtnAdd = root.findViewById(R.id.btn_queue_add);
        
        setupListeners();
    }
    
    private void setupListeners() {
        if (mBtnResume != null) {
            mBtnResume.setOnClickListener(v -> {
                if (mCurrentQueue != null && !mCurrentQueue.isEmpty()) {
                    String firstPrompt = mCurrentQueue.get(0);
                    
                    // Delete from queue first
                    Intent delIntent = new Intent(mActivity, ToggleTalkService.class);
                    delIntent.setAction("com.toggletalk.android.ACTION_DELETE_PROMPT");
                    delIntent.putExtra("index", 0);
                    mActivity.startService(delIntent);
                    
                    // Send it to start execution with continue_session true
                    Intent sendIntent = new Intent(mActivity, ToggleTalkService.class);
                    sendIntent.setAction("com.toggletalk.android.ACTION_SEND_PROMPT");
                    sendIntent.putExtra("prompt", firstPrompt);
                    sendIntent.putExtra("continue_session", true);
                    mActivity.startService(sendIntent);
                } else {
                    // Just set continue true if queue is mysteriously empty
                    Intent continueIntent = new Intent(mActivity, ToggleTalkService.class);
                    continueIntent.setAction("com.toggletalk.android.ACTION_SET_CONTINUE");
                    continueIntent.putExtra("continue_session", true);
                    mActivity.startService(continueIntent);
                }
                
                clearUnresolvedState();
            });
        }
        
        if (mBtnDelete != null) {
            mBtnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(mActivity)
                    .setTitle("Clear Queue")
                    .setMessage("Are you sure you want to clear all prompts in the queue?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        Intent intent = new Intent(mActivity, ToggleTalkService.class);
                        intent.setAction("com.toggletalk.android.ACTION_CLEAR_QUEUE");
                        mActivity.startService(intent);
                        clearUnresolvedState();
                    })
                    .setNegativeButton("No", null)
                    .show();
            });
        }
        
        if (mBtnCombine != null) {
            mBtnCombine.setOnClickListener(v -> {
                if (mCurrentQueue == null || mCurrentQueue.isEmpty()) return;
                
                StringBuilder combined = new StringBuilder();
                for (int i = 0; i < mCurrentQueue.size(); i++) {
                    combined.append(mCurrentQueue.get(i));
                    if (i < mCurrentQueue.size() - 1) {
                        combined.append("\n\n");
                    }
                }
                
                String mainText = mEtMessage.getText().toString().trim();
                if (!mainText.isEmpty()) {
                    combined.append("\n\n").append(mainText);
                }
                
                mActivity.showPromptEditPopupForCombine(combined.toString());
            });
        }
        
        if (mBtnAdd != null) {
            mBtnAdd.setOnClickListener(v -> {
                String text = mEtMessage.getText().toString().trim();
                if (!text.isEmpty()) {
                    Intent intent = new Intent(mActivity, ToggleTalkService.class);
                    intent.setAction("com.toggletalk.android.ACTION_ENQUEUE_PROMPT");
                    intent.putExtra("prompt", text);
                    mActivity.startService(intent);
                    
                    mEtMessage.setText("");
                }
            });
        }
    }
    
    public void onStateOrQueueChanged(String state, List<String> queue) {
        mCurrentQueue = queue;
        
        boolean hasQueue = queue != null && !queue.isEmpty();
        boolean isIdle = "IDLE".equals(state);
        
        if (isIdle && hasQueue) {
            setUnresolvedState(true);
        } else {
            // Clear unresolved actions if executing (THINKING/SPEAKING) or if queue is empty
            setUnresolvedState(false);
        }
    }
    
    private void setUnresolvedState(boolean unresolved) {
        mIsUnresolved = unresolved;
        if (unresolved) {
            if (mBtnSend != null) mBtnSend.setVisibility(View.GONE);
            if (mLayoutUnresolvedActions != null) mLayoutUnresolvedActions.setVisibility(View.VISIBLE);
            mGlowHelper.startGlow();
        } else {
            if (mBtnSend != null) mBtnSend.setVisibility(View.VISIBLE);
            if (mLayoutUnresolvedActions != null) mLayoutUnresolvedActions.setVisibility(View.GONE);
            mGlowHelper.stopGlow();
        }
    }
    
    public void clearUnresolvedState() {
        setUnresolvedState(false);
    }
    
    public boolean isUnresolved() {
        return mIsUnresolved;
    }
}
