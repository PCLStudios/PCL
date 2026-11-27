package com.pcl.chronoslock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

const val COFFEE_URL = "https://buymeacoffee.com/pclstudios"
const val GITHUB_URL = "https://github.com/PCLStudios"
const val TWITTER_URL = "https://x.com/pclglobal"

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}
