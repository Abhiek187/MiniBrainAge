package com.abhi.minibrainage

import android.content.res.Resources.getSystem

/**
 * Convert px to dp
 */
val Int.dp: Int get() = (this / getSystem().displayMetrics.density).toInt()

/**
 * Convert dp to px
 */
val Int.px: Int get() = (this * getSystem().displayMetrics.density).toInt()
