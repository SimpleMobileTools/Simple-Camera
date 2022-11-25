package com.simplemobiletools.camera.models

import com.simplemobiletools.camera.R

enum class TimerMode(val millisInFuture: Long) {
    OFF(0),
    TIMER_3(3000),
    TIMER_5(5000),
    TIMER_10(10000);

    fun getTimerModeResId(): Int {
        return when (this) {
            OFF -> R.id.timer_off
            TIMER_3 -> R.id.timer_3s
            TIMER_5 -> R.id.timer_5s
            TIMER_10 -> R.id.timer_10_s
        }
    }

    fun getTimerModeDrawableRes(): Int {
        return when (this) {
            OFF -> R.drawable.ic_timer_off_vector
            TIMER_3 -> R.drawable.ic_timer_3_vector
            TIMER_5 -> R.drawable.ic_timer_5_vector
            TIMER_10 -> R.drawable.ic_timer_10_vector
        }
    }
}
