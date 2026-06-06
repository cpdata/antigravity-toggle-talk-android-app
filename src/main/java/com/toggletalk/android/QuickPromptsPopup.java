package com.toggletalk.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class QuickPromptsPopup {

    public interface OnPromptSelectedListener {
        void onPromptSelected(String prompt);
    }

    private final View mRoot;
    private final View mDimBackground;
    private final EditText mEtNewPrompt;
    private final Button mBtnAdd;
    private final Button mBtnClose;
    private final ListView mLvPrompts;
    private final OnPromptSelectedListener mListener;
    private final List<String> mPrompts;
    private final ArrayAdapter<String> mAdapter;
    private final SharedPreferences mPrefs;

    private static final String PREFS_NAME = "quick_prompts_prefs";
    private static final String KEY_PROMPTS = "prompts_json";

    public QuickPromptsPopup(View root, OnPromptSelectedListener listener) {
        mRoot = root;
        mListener = listener;
        mDimBackground = root.findViewById(R.id.quick_prompts_popup_dim_background);
        mEtNewPrompt = root.findViewById(R.id.et_new_quick_prompt);
        mBtnAdd = root.findViewById(R.id.btn_add_quick_prompt);
        mLvPrompts = root.findViewById(R.id.lv_quick_prompts);
        mBtnClose = root.findViewById(R.id.btn_quick_prompts_close);

        mPrefs = root.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mPrompts = loadPrompts();

        mAdapter = new ArrayAdapter<>(root.getContext(), R.layout.item_quick_prompt, R.id.tv_quick_prompt_item, mPrompts);
        mLvPrompts.setAdapter(mAdapter);

        mDimBackground.setOnClickListener(v -> dismiss());
        mBtnClose.setOnClickListener(v -> dismiss());

        mBtnAdd.setOnClickListener(v -> {
            String text = mEtNewPrompt.getText().toString().trim();
            if (!text.isEmpty()) {
                mPrompts.add(0, text);
                savePrompts();
                mAdapter.notifyDataSetChanged();
                mEtNewPrompt.setText("");
            }
        });

        mLvPrompts.setOnItemClickListener((parent, view, position, id) -> {
            String prompt = mPrompts.get(position);
            if (mListener != null) {
                mListener.onPromptSelected(prompt);
            }
            dismiss();
        });

        mLvPrompts.setOnItemLongClickListener((parent, view, position, id) -> {
            mPrompts.remove(position);
            savePrompts();
            mAdapter.notifyDataSetChanged();
            Toast.makeText(mRoot.getContext(), "Prompt deleted", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    public void show() {
        mRoot.setVisibility(View.VISIBLE);
        mEtNewPrompt.requestFocus();
        mRoot.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) mRoot.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(mEtNewPrompt, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 100);
    }

    public void dismiss() {
        mRoot.setVisibility(View.GONE);
        InputMethodManager imm = (InputMethodManager) mRoot.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mEtNewPrompt.getWindowToken(), 0);
        }
    }

    private List<String> loadPrompts() {
        List<String> list = new ArrayList<>();
        String json = mPrefs.getString(KEY_PROMPTS, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.getString(i));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    private void savePrompts() {
        JSONArray arr = new JSONArray();
        for (String s : mPrompts) {
            arr.put(s);
        }
        mPrefs.edit().putString(KEY_PROMPTS, arr.toString()).apply();
    }
}
