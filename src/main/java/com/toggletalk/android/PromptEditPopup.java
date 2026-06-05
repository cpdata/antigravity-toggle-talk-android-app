package com.toggletalk.android;

import android.app.AlertDialog;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class PromptEditPopup {

    public interface OnEditActionListener {
        void onUpdate(String text);
        void onDelete();
        void onSend(String text);
        void onCancel();
    }

    private final View mRoot;
    private final View mDimBackground;
    private final EditText mEtPrompt;
    private final Button mBtnDelete;
    private final Button mBtnCancel;
    private final Button mBtnUpdate;
    private final Button mBtnSend;
    private OnEditActionListener mListener;

    public PromptEditPopup(View root, OnEditActionListener listener) {
        mRoot = root;
        mListener = listener;
        mDimBackground = root.findViewById(R.id.prompt_edit_popup_dim_background);
        mEtPrompt = root.findViewById(R.id.et_edit_prompt);
        mBtnDelete = root.findViewById(R.id.btn_edit_delete);
        mBtnCancel = root.findViewById(R.id.btn_edit_cancel);
        mBtnUpdate = root.findViewById(R.id.btn_edit_update);
        mBtnSend = root.findViewById(R.id.btn_edit_send);

        mDimBackground.setOnClickListener(v -> dismiss());
        mBtnCancel.setOnClickListener(v -> dismiss());

        mBtnUpdate.setOnClickListener(v -> {
            String text = mEtPrompt.getText().toString();
            if (!text.trim().isEmpty()) {
                if (mListener != null) mListener.onUpdate(text);
                dismiss();
            }
        });

        mBtnSend.setOnClickListener(v -> {
            String text = mEtPrompt.getText().toString();
            if (!text.trim().isEmpty()) {
                if (mListener != null) mListener.onSend(text);
                dismiss();
            }
        });

        mBtnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(mRoot.getContext())
                .setTitle("Delete Prompt")
                .setMessage("Are you sure you want to delete this queued prompt?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    if (mListener != null) mListener.onDelete();
                    dismiss();
                })
                .setNegativeButton("No", null)
                .show();
        });

        mEtPrompt.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                // Insert newline
                int start = mEtPrompt.getSelectionStart();
                int end = mEtPrompt.getSelectionEnd();
                mEtPrompt.getText().replace(Math.min(start, end), Math.max(start, end), "\n", 0, 1);
                return true;
            }
            return false;
        });
    }

    public void show(String promptText, boolean showSendOnly) {
        if (showSendOnly) {
            mBtnSend.setVisibility(View.VISIBLE);
            mBtnUpdate.setVisibility(View.GONE);
            mBtnDelete.setVisibility(View.GONE);
        } else {
            mBtnSend.setVisibility(View.GONE);
            mBtnUpdate.setVisibility(View.VISIBLE);
            mBtnDelete.setVisibility(View.VISIBLE);
        }

        mEtPrompt.setText(promptText + "\n");
        mRoot.setVisibility(View.VISIBLE);
        mEtPrompt.requestFocus();
        mEtPrompt.setSelection(mEtPrompt.getText().length());

        mRoot.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) mRoot.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(mEtPrompt, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 100);
    }

    public void dismiss() {
        mRoot.setVisibility(View.GONE);
        InputMethodManager imm = (InputMethodManager) mRoot.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mEtPrompt.getWindowToken(), 0);
        }
        if (mListener != null) mListener.onCancel();
    }
}
