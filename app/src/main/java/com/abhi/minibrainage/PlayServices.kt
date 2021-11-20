package com.abhi.minibrainage

import android.app.Activity
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.games.Games
import com.google.android.gms.games.leaderboard.LeaderboardVariant

class PlayServices(private var activity: Activity, private var buttonGoogle: ImageButton) {
    // Holds all methods related to Google Play Services
    private val context = activity.applicationContext
    private val prefs = SharedPrefs(context)
    private var signedInAccount: GoogleSignInAccount? = null
    private val leaderboardIntentKey = "openLeaderboards"

    init {
        buttonGoogle.setOnClickListener {
            // If the user is signed out, sign them in, otherwise sign them out
            if (signedInAccount == null) {
                signInSilently(withPrompt = true)
            } else {
                signOut()
            }
        }
    }

    private fun onSignedIn(account: GoogleSignInAccount, openLeaderboard: Boolean) {
        // Tasks to perform when the user is signed in
        signedInAccount = account
        // Show in color that the user signed in
        buttonGoogle.setImageResource(R.drawable.googleg_standard_color_18)
        buttonGoogle.contentDescription = context.getString(R.string.sign_out)

        // Show a popup of the user connecting to Play Games
        val gamesClient = Games.getGamesClient(context, account)
        gamesClient.setViewForPopups(activity.findViewById(android.R.id.content))
        // Backup in case the popup doesn't show
        Toast.makeText(context, "Signed in to Google Play Games", Toast.LENGTH_SHORT).show()

        // Synchronize the local high score and the leaderboards high score
        syncLeaderboardScore()

        if (openLeaderboard) {
            // To be called if the user signs in after tapping the leaderboards button
            openLeaderboards()
        }
    }

    fun signInSilently(withPrompt: Boolean=false, withLeaderboard: Boolean=false) {
        // Sign in to Google in the background, intervene if necessary
        val signInOptions = GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN
        val account = GoogleSignIn.getLastSignedInAccount(context)

        if (GoogleSignIn.hasPermissions(account, *signInOptions.scopeArray)) {
            // Already signed in.
            // The signed in account is stored in the 'account' variable.
            onSignedIn(account!!, withLeaderboard)
        } else {
            // Haven't been signed-in before. Try the silent sign-in first.
            val signInClient = GoogleSignIn.getClient(context, signInOptions)
            signInClient.silentSignIn().addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    // The signed in account is stored in the task's result.
                    onSignedIn(task.result, withLeaderboard)
                } else if (withPrompt) {
                    /* If the user can't be signed in automatically, don't prompt them unless they
                     * want to view the leaderboards or press the sign-in button
                     */
                    startSignInIntent(withLeaderboard)
                }
            }
        }
    }

    private fun startSignInIntent(openLeaderboard: Boolean) {
        val signInClient = GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN
        )
        val intent = signInClient.signInIntent
        intent.putExtra(leaderboardIntentKey, openLeaderboard)
        signInResultLauncher.launch(intent)
    }

    private fun signOut() {
        val signInClient = GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN
        )
        signInClient.signOut().addOnCompleteListener(activity) {
            // At this point, the user is signed out.
            signedInAccount = null
            // Fade the Google logo to show that the user isn't signed in
            buttonGoogle.setImageResource(R.drawable.googleg_disabled_color_18)
            buttonGoogle.contentDescription = context.getString(R.string.sign_in)
            Toast.makeText(context, "Signed out of Google Play Games", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncLeaderboardScore() {
        // Ensure that the local and leaderboards high score match
        if (signedInAccount == null) return
        val maxResults = 1
        val leaderboardID = context.getString(R.string.leaderboard_id)

        // Get the player's high score from the leaderboards
        Games.getLeaderboardsClient(context, signedInAccount!!)
            .loadPlayerCenteredScores(
                leaderboardID,
                LeaderboardVariant.TIME_SPAN_ALL_TIME,
                LeaderboardVariant.COLLECTION_PUBLIC,
                maxResults
            )
            .addOnSuccessListener { leaderboardData ->
                leaderboardData.get()?.scores?.get(0)?.let { scoreResult ->
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
        signedInAccount?.let { account ->
            Games.getLeaderboardsClient(context, account)
                .submitScore(context.getString(R.string.leaderboard_id), score)
            Toast.makeText(context, "High score saved to the leaderboards!", Toast.LENGTH_SHORT).show()
        }
    }

    fun openLeaderboards() {
        if (signedInAccount == null) {
            // Prompt the user to sign in if they want to access the leaderboards, then open if successful
            signInSilently(withPrompt = true, withLeaderboard = true)
        } else {
            // Open the Play Games leaderboards
            Games.getLeaderboardsClient(context, signedInAccount!!)
                .getLeaderboardIntent(context.getString(R.string.leaderboard_id))
                .addOnSuccessListener { intent ->
                    leaderboardsResultLauncher.launch(intent)
                }
                .addOnFailureListener { err ->
                    println("Failed to open the leaderboards: ${err.localizedMessage}")
                }
        }
    }

    private val signInResultLauncher = (activity as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // There are no request codes
            val data = result.data
            println("Request succeeded, data: $data")

            if (data != null) {
                val signInResult = Auth.GoogleSignInApi.getSignInResultFromIntent(data)

                if (signInResult?.isSuccess == true) {
                    // The signed in account is stored in the result.
                    val openLeaderboard = data.extras?.getBoolean(leaderboardIntentKey) ?: false
                    onSignedIn(signInResult.signInAccount!!, openLeaderboard)
                } else {
                    // Show a toast message that the user failed to sign in
                    var message = signInResult?.status?.statusMessage

                    if (message == null || message.isEmpty()) {
                        message = "Sign-in failed"
                    }

                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Failed to sign in Google Play Games", Toast.LENGTH_SHORT).show()
            println(result)
        }
    }

    private val leaderboardsResultLauncher = (activity as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // The leaderboards intent doesn't return any data
        if (result.resultCode == Activity.RESULT_OK) {
            println("Successfully opened the leaderboards!")
            println("Data: ${result.data}")
        } else {
            Toast.makeText(
                context, "Failed to show the leaderboards. Are you signed in?",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
