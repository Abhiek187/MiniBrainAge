package com.abhi.minibrainage

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.games.*
import com.google.android.gms.games.leaderboard.LeaderboardVariant

class PlayServices(private var activity: Activity, private var buttonGoogle: ImageButton) {
    // Holds all methods related to Google Play Services
    private val context = activity.applicationContext
    private val prefs = SharedPrefs(context)
    private var isAuthenticated = false
    private var openLeaderboard = false

    init {
        PlayGamesSdk.initialize(context)
        val gamesSignInClient = PlayGames.getGamesSignInClient(activity)

        gamesSignInClient.isAuthenticated.addOnCompleteListener { isAuthenticatedTask ->
            isAuthenticated = isAuthenticatedTask.isSuccessful &&
                    isAuthenticatedTask.result.isAuthenticated

            if (isAuthenticated) {
                // Continue with Play Games Services
                // Hide the sign-in button
                buttonGoogle.visibility = View.INVISIBLE
                // Backup in case the popup doesn't show
                Toast.makeText(context, "Signed in to Google Play Games", Toast.LENGTH_SHORT).show()

                // Synchronize the local high score and the leaderboards high score
                syncLeaderboardScore()

                if (openLeaderboard) {
                    // To be called if the user signs in after tapping the leaderboards button
                    openLeaderboards()
                    openLeaderboard = false
                }
            } else {
                // Disable your integration with Play Games Services or show a
                // login button to ask players to sign-in. Clicking it should
                // call GamesSignInClient.signIn().
                buttonGoogle.visibility = View.VISIBLE

                buttonGoogle.setOnClickListener {
                    gamesSignInClient.signIn()
                }
            }
        }
    }

    @SuppressLint("VisibleForTests")
    private fun syncLeaderboardScore() {
        // Ensure that the local and leaderboards high score match
        val maxResults = 1
        val leaderboardID = context.getString(R.string.leaderboard_id)

        // Get the player's high score from the leaderboards
        PlayGames.getLeaderboardsClient(activity)
            .loadPlayerCenteredScores(
                leaderboardID,
                LeaderboardVariant.TIME_SPAN_ALL_TIME,
                LeaderboardVariant.COLLECTION_PUBLIC,
                maxResults
            )
            .addOnSuccessListener { leaderboardData ->
                val scores = leaderboardData.get()?.scores
                // Check if there is a leaderboard score
                if (scores != null && scores.count > 0) {
                    val scoreResult = scores[0]

                    if (scoreResult.rawScore > prefs.highScore) {
                        // Update the local high score if the leaderboards high score is greater
                        prefs.highScore = scoreResult.rawScore
                    } else if (prefs.highScore > scoreResult.rawScore) {
                        // If the local high score is greater, submit the score to the leaderboards
                        saveToLeaderboards(prefs.highScore)
                    }
                }
            }
            .addOnFailureListener { err ->
                println("Failed to retrieve high score from leaderboard: ${err.localizedMessage}")
            }
    }

    fun saveToLeaderboards(score: Long) {
        // Save the score to the leaderboards
        // If the current leaderboard score is greater, the API call isn't made
        PlayGames.getLeaderboardsClient(activity)
            .submitScore(context.getString(R.string.leaderboard_id), score)
        Toast.makeText(context, "High score saved to the leaderboards!", Toast.LENGTH_SHORT).show()
    }

    fun openLeaderboards() {
        // Open the Play Games leaderboards
        PlayGames.getLeaderboardsClient(activity)
            .getLeaderboardIntent(context.getString(R.string.leaderboard_id))
            .addOnSuccessListener { intent ->
                leaderboardsResultLauncher.launch(intent)
            }
            .addOnFailureListener { err ->
                println("Failed to open the leaderboards: ${err.localizedMessage}")
            }
    }

    private val leaderboardsResultLauncher = (activity as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // The leaderboards intent doesn't return any data
        // Activity.RESULT_CANCELED would normally occur when exiting the leaderboards
        println("Leaderboards result code: ${result.resultCode}")
        println("Leaderboards result data: ${result.data}")
    }
}
