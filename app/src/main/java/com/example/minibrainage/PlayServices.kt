package com.example.minibrainage

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
    private var context = activity.applicationContext
    private val prefs = SharedPrefs(context)
    private var refusedSignIn = false // if true, don't prompt the player to sign in
    private var signedInAccount: GoogleSignInAccount? = null

    init {
        buttonGoogle.setOnClickListener {
            // If the user is signed out, sign them in, otherwise sign them out
            if (signedInAccount == null) {
                refusedSignIn = false
                signInSilently()
            } else {
                signOut()
            }
        }
    }

    fun signInSilently() {
        // Sign in to Google in the background, intervene if necessary
        val signInOptions = GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN
        val account = GoogleSignIn.getLastSignedInAccount(context)

        if (GoogleSignIn.hasPermissions(account, *signInOptions.scopeArray)) {
            // Already signed in.
            // The signed in account is stored in the 'account' variable.
            signedInAccount = account
            buttonGoogle.contentDescription = context.getString(R.string.sign_out)
//            val gamesClient = Games.getGamesClient(this, signedInAccount!!)
//            gamesClient.setViewForPopups(findViewById(android.R.id.content))
            Toast.makeText(context, "Signed in to Google", Toast.LENGTH_SHORT).show()
            updateHighScoreFromLeaderboards()
        } else {
            // Haven't been signed-in before. Try the silent sign-in first.
            val signInClient = GoogleSignIn.getClient(context, signInOptions)
            signInClient.silentSignIn().addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    // The signed in account is stored in the task's result.
                    signedInAccount = task.result
                    buttonGoogle.contentDescription = context.getString(R.string.sign_out)
                    val gamesClient = Games.getGamesClient(context, task.result)
                    gamesClient.setViewForPopups(activity.findViewById(android.R.id.content))
                    updateHighScoreFromLeaderboards()
                    //Toast.makeText(this, "Signed in to Google", Toast.LENGTH_SHORT).show()
                } else if (!refusedSignIn) {
                    // Don't constantly prompt the user to sign in to Google Play Games
                    // Player will need to sign-in explicitly using via UI.
                    startSignInIntent()
                }
            }
        }
    }

    private fun startSignInIntent() {
        val signInClient = GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN
        )
        val intent = signInClient.signInIntent
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
            buttonGoogle.contentDescription = context.getString(R.string.sign_in)
            Toast.makeText(context, "Signed out of Google", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateHighScoreFromLeaderboards() {
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
                    // Update the local high score if the leaderboards high score is greater
                    if (scoreResult.rawScore > prefs.highScore) {
                        prefs.highScore = scoreResult.rawScore
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
            // Prompt the user to sign in if they want to access the leaderboards
            refusedSignIn = false
            startSignInIntent()
        } else {
            // Open the Google Play leaderboards
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
                    signedInAccount = signInResult.signInAccount
                    buttonGoogle.contentDescription = context.getString(R.string.sign_out)
                    Toast.makeText(context, "Signed in to Google", Toast.LENGTH_SHORT).show()
                    updateHighScoreFromLeaderboards()
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
            Toast.makeText(context, "Failed to sign in to Google", Toast.LENGTH_SHORT).show()
            refusedSignIn = true
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
                context,
                "Failed to show the leaderboards. Are you signed in?",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
