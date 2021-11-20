package com.abhi.minibrainage

import android.content.Context
import android.content.SharedPreferences

class SharedPrefs(context: Context) {
    private val fileName = "SharedPrefs"
    private val highScoreKey = "highScore"
    private val prefs: SharedPreferences = context.getSharedPreferences(fileName,
        Context.MODE_PRIVATE)

    var highScore: Long
        // Start with a high score of 0 by default, but will fetch from the leaderboards if signed in
        get() = prefs.getLong(highScoreKey, 0L)
        set(value) = prefs.edit().putLong(highScoreKey, value).apply()
}
