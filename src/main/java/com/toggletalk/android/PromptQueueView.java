package com.toggletalk.android;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

public class PromptQueueView {
    public interface OnPromptActionListener {
        void onPromptClick(int index, String text);
        void onPromptDelete(int index);
        void onExpansionToggled(boolean isExpanded);
    }

    private final View mContainer;
    private final View mCollapsedLayout;
    private final ScrollView mExpandedScroll;
    private final LinearLayout mExpandedList;
    private final TextView mBadgeTv;
    private final TextView mCollapsedTextTv;
    
    private final GlowAnimationHelper mGlowHelper;
    private OnPromptActionListener mListener;
    private boolean mIsExpanded = false;

    public PromptQueueView(View container, OnPromptActionListener listener) {
        mContainer = container;
        mListener = listener;
        
        mCollapsedLayout = container.findViewById(R.id.layout_queue_collapsed);
        mExpandedScroll = container.findViewById(R.id.scroll_queue_expanded);
        mExpandedList = container.findViewById(R.id.layout_queue_expanded_list);
        mBadgeTv = container.findViewById(R.id.tv_queue_badge);
        mCollapsedTextTv = container.findViewById(R.id.tv_queue_collapsed_text);
        
        mGlowHelper = new GlowAnimationHelper(mContainer);
        
        // Setup toggle click listener
        mContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsExpanded = !mIsExpanded;
                if (mListener != null) {
                    mListener.onExpansionToggled(mIsExpanded);
                }
            }
        });
        
        mCollapsedLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsExpanded = !mIsExpanded;
                if (mListener != null) {
                    mListener.onExpansionToggled(mIsExpanded);
                }
            }
        });
    }

    public boolean isExpanded() {
        return mIsExpanded;
    }

    public void setExpanded(boolean expanded) {
        mIsExpanded = expanded;
    }

    public void render(List<String> queue, boolean isExpanded) {
        mIsExpanded = isExpanded;
        if (queue == null || queue.isEmpty()) {
            mContainer.setVisibility(View.GONE);
            mGlowHelper.stopGlow();
            return;
        }

        mContainer.setVisibility(View.VISIBLE);
        mGlowHelper.startGlow();

        if (!mIsExpanded) {
            mCollapsedLayout.setVisibility(View.VISIBLE);
            mExpandedScroll.setVisibility(View.GONE);

            mBadgeTv.setText(String.valueOf(queue.size()));
            
            String mostRecent = queue.get(queue.size() - 1);
            if (mostRecent != null) {
                String firstLine = mostRecent.split("\n")[0];
                mCollapsedTextTv.setText(firstLine);
            } else {
                mCollapsedTextTv.setText("");
            }
        } else {
            mCollapsedLayout.setVisibility(View.GONE);
            mExpandedScroll.setVisibility(View.VISIBLE);

            mExpandedList.removeAllViews();
            Context context = mContainer.getContext();
            float density = context.getResources().getDisplayMetrics().density;

            ViewGroup.LayoutParams scrollParams = mExpandedScroll.getLayoutParams();
            if (queue.size() >= 4) {
                scrollParams.height = (int) (120 * density);
            } else {
                scrollParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            mExpandedScroll.setLayoutParams(scrollParams);

            int size = queue.size();
            for (int i = 0; i < size; i++) {
                final int index = i;
                final String promptText = queue.get(i);

                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding((int) (4 * density), (int) (6 * density), (int) (4 * density), (int) (6 * density));
                
                TextView tv = new TextView(context);
                LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                tv.setLayoutParams(tvParams);
                tv.setTextColor(Color.parseColor("#E6E6FA"));
                tv.setTextSize(11);
                tv.setText(promptText);

                if (size == 1) {
                    tv.setMaxLines(3);
                    tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                } else if (size == 2) {
                    if (i == 0) {
                        tv.setMaxLines(2);
                    } else {
                        tv.setMaxLines(1);
                    }
                    tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                } else if (size == 3) {
                    tv.setMaxLines(1);
                    tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                } else if (size >= 4) {
                    tv.setMaxLines(1);
                    tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                }

                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mListener != null) {
                            mListener.onPromptClick(index, promptText);
                        }
                    }
                });

                row.addView(tv);

                ImageButton btnDel = new ImageButton(context);
                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams((int) (24 * density), (int) (24 * density));
                btnDel.setLayoutParams(btnParams);
                btnDel.setBackground(null);
                btnDel.setImageResource(android.R.drawable.ic_menu_delete);
                btnDel.setColorFilter(Color.parseColor("#FF3B30"), PorterDuff.Mode.SRC_ATOP);
                btnDel.setPadding(0, 0, 0, 0);
                
                btnDel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mListener != null) {
                            mListener.onPromptDelete(index);
                        }
                    }
                });

                row.addView(btnDel);
                mExpandedList.addView(row);
            }
        }
    }
}
