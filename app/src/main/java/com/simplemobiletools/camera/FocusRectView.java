package com.simplemobiletools.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.view.ViewGroup;

public class FocusRectView extends ViewGroup {
    private static final int RECT_SIZE = 50;
    private static final int RECT_DURATION = 500;

    private static Paint mPaint;
    private static Rect mRect;
    private static Handler mHandler;

    private static boolean mDrawRect;
    private static int mPrimaryColor;

    public FocusRectView(Context context) {
        super(context);
        setWillNotDraw(false);
        mHandler = new Handler();
        mPrimaryColor = getResources().getColor(R.color.colorPrimary);
        setupPaint();
    }

    private void setupPaint() {
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(mPrimaryColor);
        mPaint.setStrokeWidth(2);
    }

    public void drawFocusRect(int x, int y) {
        mRect = new Rect(x - RECT_SIZE, y - RECT_SIZE, x + RECT_SIZE, y + RECT_SIZE);
        toggleRect(true);

        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                toggleRect(false);
            }
        }, RECT_DURATION);
    }

    private void toggleRect(boolean show) {
        mDrawRect = show;
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDrawRect) {
            canvas.drawRect(mRect, mPaint);
        }
    }
}
