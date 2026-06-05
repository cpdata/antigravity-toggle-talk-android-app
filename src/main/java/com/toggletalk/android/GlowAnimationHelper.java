package com.toggletalk.android;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

public class GlowAnimationHelper {
    private final View mView;
    private final GradientDrawable mDrawable;
    private final int mStrokeWidth;
    private ValueAnimator mAnimator;
    private boolean mIsGlowing = false;

    public GlowAnimationHelper(View view) {
        mView = view;
        if (view.getBackground() instanceof GradientDrawable) {
            mDrawable = (GradientDrawable) view.getBackground().mutate();
            view.setBackground(mDrawable);
        } else {
            mDrawable = null;
        }
        float density = view.getContext().getResources().getDisplayMetrics().density;
        mStrokeWidth = Math.max(1, (int) (1 * density));
    }

    public void startGlow() {
        if (mDrawable == null || mIsGlowing) return;
        mIsGlowing = true;

        if (mAnimator != null) {
            mAnimator.cancel();
        }

        mAnimator = ValueAnimator.ofArgb(Color.parseColor("#33FF3B30"), Color.parseColor("#FFFF3B30"));
        mAnimator.setDuration(1000);
        mAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int animatedColor = (int) animation.getAnimatedValue();
                mDrawable.setStroke(mStrokeWidth, animatedColor);
            }
        });
        mAnimator.start();
    }

    public void stopGlow() {
        if (!mIsGlowing) return;
        mIsGlowing = false;

        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }

        if (mDrawable != null) {
            mDrawable.setStroke(mStrokeWidth, Color.parseColor("#26FFFFFF"));
        }
    }
}
