package com.aonon.foldcollage;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/** Centers its single child at the requested aspect ratio while the box itself fills its parent. */
public class AspectBox extends FrameLayout {
    private float aspectRatio = 1.0f; // width / height

    public AspectBox(Context context) {
        super(context);
        setForegroundGravity(Gravity.CENTER);
    }

    public AspectBox(Context context, AttributeSet attrs) {
        super(context, attrs);
        setForegroundGravity(Gravity.CENTER);
    }

    public void setAspectRatio(float ratio) {
        if (ratio <= 0f) return;
        aspectRatio = ratio;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int boxW = MeasureSpec.getSize(widthMeasureSpec);
        int boxH = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(boxW, boxH);

        if (getChildCount() == 0 || boxW <= 0 || boxH <= 0) return;

        int childW;
        int childH;
        if ((float) boxW / (float) boxH > aspectRatio) {
            childH = boxH;
            childW = Math.max(1, Math.round(childH * aspectRatio));
        } else {
            childW = boxW;
            childH = Math.max(1, Math.round(childW / aspectRatio));
        }
        int cw = MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY);
        int ch = MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY);
        getChildAt(0).measure(cw, ch);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() == 0) return;
        View child = getChildAt(0);
        int left = (getWidth() - child.getMeasuredWidth()) / 2;
        int top = (getHeight() - child.getMeasuredHeight()) / 2;
        child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
    }
}
