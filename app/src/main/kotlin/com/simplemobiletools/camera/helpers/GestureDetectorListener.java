package com.simplemobiletools.camera.helpers;

import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

public class GestureDetectorListener extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onFling(@Nullable MotionEvent e1, @Nullable MotionEvent e2, float velocityX, float velocityY) {
        return super.onFling(e1, e2, velocityX, velocityY);
    }
}
