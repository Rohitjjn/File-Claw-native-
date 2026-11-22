package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Unwrap themed/Compose context wrappers to reach the host Activity. */
internal fun Context.findHostActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
