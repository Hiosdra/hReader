package com.hiosdra.hreader.presentation.theme

import android.animation.ValueAnimator

internal object MotionDuration {
    const val QUICK = 140
    const val STANDARD = 180
    const val EXIT = 120

    fun scaled(durationMillis: Int): Int =
        if (ValueAnimator.areAnimatorsEnabled()) durationMillis else 0

    fun areAnimationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()
}
