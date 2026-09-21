package com.hiosdra.hreader.presentation.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
internal fun SecureWindowEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val wasSecure = window?.let { it.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 } == true
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (window != null && !wasSecure) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return current as? Activity
}
