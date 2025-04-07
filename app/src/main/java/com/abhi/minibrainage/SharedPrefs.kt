package com.abhi.minibrainage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SharedPrefs(context: Context) {
    private val fileName = "SharedPrefs"
    private val highScoreKey = "highScore"
    private val gamesPlayedKey = "gamesPlayed"
    private val lastVersionReviewedKey = "lastVersionReviewed"
    private val prefs: SharedPreferences = context.getSharedPreferences(fileName,
        Context.MODE_PRIVATE)

    var highScore: Long
        // Start with a high score of 0 by default, but will fetch from the leaderboards if signed in
        get() = prefs.getLong(highScoreKey, 0L)
        set(value) = prefs.edit { putLong(highScoreKey, value) }
    var gamesPlayed: Int
        get() = prefs.getInt(gamesPlayedKey, 0)
        set(value) = prefs.edit { putInt(gamesPlayedKey, value) }
    var lastVersionReviewed: Int
        get() = prefs.getInt(lastVersionReviewedKey, 0)
        set(value) = prefs.edit { putInt(lastVersionReviewedKey, value) }
}
