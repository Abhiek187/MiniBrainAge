package com.example.minibrainage

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.drawToBitmap
import com.example.minibrainage.databinding.ActivityMainBinding
import com.example.minibrainage.databinding.PopupPlayAgainBinding
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.games.Games
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import kotlin.math.floor
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private var answer = 0
    private val digitClassifier = DigitClassifier(this)
    private var timer: CountDownTimer? = null
    private lateinit var layoutPage: ConstraintLayout
    private lateinit var canvasView: CanvasView
    private lateinit var buttonGoogle: ImageButton
    private lateinit var textViewTimer: TextView
    private lateinit var textViewScore: TextView
    private lateinit var textViewMath: TextView

    // Make scores long to directly submit them to the leaderboards
    private var score = 0L
        set(value) {
            field = value
            // Connect the score variable to its corresponding TextView
            textViewScore.text = getString(R.string.score, value)
        }

    private var oldTextColor = 0 // color code
    private val startTime = 5000L // TODO: change back
    private var remainingTime = startTime
    private var resumeTime = remainingTime
    private var highScore = 0L // default, but will fetch from the leaderboards if signed in
    private var gameOver = false
    private var refusedSignIn = false // if true, don't prompt the player to sign in
    private var signedInAccount: GoogleSignInAccount? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // UI elements
        layoutPage = binding.layoutPage
        val buttonClassify = binding.buttonClassify
        val buttonReset = binding.buttonReset
        buttonGoogle = binding.imageButtonGoogle
        textViewTimer = binding.textViewTimer
        textViewScore = binding.textViewScore
        textViewMath = binding.textViewMath
        val imageViewCheck = binding.imageViewCheck

        oldTextColor = textViewTimer.currentTextColor
        textViewScore.text = getString(R.string.score, score) // show initial score

        // Generate a random math equation to solve
        generateRandomEquation()

        // Add CanvasView to ConstraintLayout
        canvasView = CanvasView(this)
        canvasView.id = View.generateViewId() // generate id to apply constraints
        canvasView.setBackgroundColor(Color.BLACK)
        layoutPage.addView(canvasView)

        // Make CanvasView square, height = phone width - margins, middle of screen
        val constraintSet = ConstraintSet()
        constraintSet.clone(layoutPage)
        // Set width and height to match constraint
        constraintSet.constrainWidth(canvasView.id, 0)
        constraintSet.constrainHeight(canvasView.id, 0)
        // Put view in the middle of the screen
        constraintSet.connect(
            canvasView.id,
            ConstraintSet.START,
            layoutPage.id,
            ConstraintSet.START,
            8
        )
        constraintSet.connect(
            canvasView.id,
            ConstraintSet.END,
            layoutPage.id,
            ConstraintSet.END,
            8
        )
        constraintSet.connect(
            canvasView.id,
            ConstraintSet.TOP,
            textViewTimer.id,
            ConstraintSet.BOTTOM,
            8
        )
        constraintSet.connect(
            canvasView.id,
            ConstraintSet.BOTTOM,
            textViewMath.id,
            ConstraintSet.TOP,
            8
        )
        // Make view a square
        constraintSet.setDimensionRatio(canvasView.id, "1:1")
        constraintSet.applyTo(layoutPage)

        // Initialize the digit classifier
        digitClassifier.initialize().addOnFailureListener { err ->
            println("Failed to set up digit classifier: ${err.localizedMessage}")
        }

        buttonClassify.setOnClickListener {
            // Classify the number drawn
            if (digitClassifier.isInitialized && !gameOver) {
                digitClassifier.classifyAsync(canvasView.drawToBitmap())
                    .addOnSuccessListener { result ->
                        val (num1, conf1) = result[0]
                        val (num2, conf2) = result[1]

                        when {
                            // 2 points for the best answer, 1 point for the 2nd best answer
                            num1 == answer -> {
                                score += 2
                                imageViewCheck.setImageResource(android.R.drawable.checkbox_on_background)
                                imageViewCheck.contentDescription = getString(R.string.correct)
                            }
                            num2 == answer -> {
                                score++
                                imageViewCheck.setImageResource(android.R.drawable.checkbox_on_background)
                                imageViewCheck.contentDescription = getString(R.string.correct)
                            }
                            else -> {
                                imageViewCheck.setImageResource(android.R.drawable.ic_delete)
                                imageViewCheck.contentDescription = getString(R.string.wrong)
                            }
                        }

                        imageViewCheck.visibility = View.VISIBLE
                        // See exact classification results (for debugging purposes)
                        val confStr1 = String.format("%.2f", conf1 * 100)
                        val confStr2 = String.format("%.2f", conf2 * 100)
                        println("That's either $num1 ($confStr1%) or $num2 ($confStr2%).")
                        // Clear the canvas
                        canvasView.clear()
                        // Generate a new equation
                        generateRandomEquation()
                    }
                    .addOnFailureListener { err ->
                        Toast.makeText(
                            this, "Couldn't classify drawing: ${err.localizedMessage}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }

        buttonReset.setOnClickListener {
            // Clear the canvas
            canvasView.clear()
            // Generate a new equation
            generateRandomEquation()
        }

        buttonGoogle.setOnClickListener {
            // If the user is signed out, sign them in, otherwise sign them out
            if (signedInAccount == null) {
                refusedSignIn = false
                signInSilently()
            } else {
                signOut()
            }
        }

        // Create a 1-minute timer
        startTimer(60000)
    }

    override fun onPause() {
        super.onPause()
        // Since remainingTime continues to tick, use another variable instead
        resumeTime = remainingTime
        timer?.cancel() // stop the timer
    }

    override fun onResume() {
        super.onResume()
        signInSilently()

        if (!gameOver) {
            startTimer(resumeTime) // resume with the same number of seconds remaining
        }
    }

    override fun onDestroy() {
        digitClassifier.close() // stop the classifier before closing the app
        super.onDestroy()
    }

    private fun signInSilently() {
        // Sign in to Google in the background, intervene if necessary
        val signInOptions = GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN
        val account = GoogleSignIn.getLastSignedInAccount(this)

        if (GoogleSignIn.hasPermissions(account, *signInOptions.scopeArray)) {
            // Already signed in.
            // The signed in account is stored in the 'account' variable.
            signedInAccount = account
            buttonGoogle.contentDescription = getString(R.string.sign_out)
//            val gamesClient = Games.getGamesClient(this, signedInAccount!!)
//            gamesClient.setViewForPopups(findViewById(android.R.id.content))
            Toast.makeText(this, "Signed in to Google", Toast.LENGTH_SHORT).show()
            updateHighScoreFromLeaderboards()
        } else {
            // Haven't been signed-in before. Try the silent sign-in first.
            val signInClient = GoogleSignIn.getClient(this, signInOptions)
            signInClient.silentSignIn().addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // The signed in account is stored in the task's result.
                    signedInAccount = task.result
                    buttonGoogle.contentDescription = getString(R.string.sign_out)
                    val gamesClient = Games.getGamesClient(this, task.result)
                    gamesClient.setViewForPopups(findViewById(android.R.id.content))
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
            this,
            GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN
        )
        val intent = signInClient.signInIntent
        signInResultLauncher.launch(intent)
    }

    private fun signOut() {
        val signInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN
        )
        signInClient.signOut().addOnCompleteListener(this) {
            // At this point, the user is signed out.
            signedInAccount = null
            buttonGoogle.contentDescription = getString(R.string.sign_in)
            Toast.makeText(this, "Signed out of Google", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateHighScoreFromLeaderboards() {
//        if (signedInAccount == null) return
//        val maxResults = 1
//        val leaderboardID = getString(R.string.leaderboard_id)
//
//        // Get the player's high score from the leaderboards
//        Games.getLeaderboardsClient(this, signedInAccount!!)
//            .loadPlayerCenteredScores(
//                leaderboardID,
//                LeaderboardVariant.TIME_SPAN_ALL_TIME,
//                LeaderboardVariant.COLLECTION_PUBLIC,
//                maxResults
//            )
//            .addOnSuccessListener { leaderboardData ->
//                leaderboardData.get()?.scores?.get(0)?.let { scoreResult ->
//                    highScore = scoreResult.rawScore
//                }
//            }
//            .addOnFailureListener { err ->
//                println("Failed to retrieve high score from leaderboard: ${err.message}")
//            }
    }

    private fun startTimer(milliseconds: Long) {
        timer?.cancel() // make sure a timer instance isn't already running

        timer = object: CountDownTimer(milliseconds, 1000) {
            override fun onTick(msRemain: Long) {
                remainingTime = msRemain
                val seconds = floor(msRemain / 1000.0).roundToInt()
                val minutes = seconds / 60

                if (seconds <= 10) {
                    textViewTimer.setTextColor(Color.RED)
                }

                textViewTimer.text = getString(R.string.time, minutes, seconds)
            }

            override fun onFinish() {
                gameOver = true
                Toast.makeText(applicationContext, "Time's up!", Toast.LENGTH_SHORT).show()
                textViewTimer.text = getString(R.string.time, 0, 0)
                textViewTimer.setTextColor(oldTextColor) // it's not black

                // Show the play again popup
                showPopup()
            }
        }

        timer?.start()
    }

    private fun generateRandomEquation() {
        /* Start with an equation: a op b = c,
         * where a is in [-99, 99], op is in {+, -, *}, and b is in [0, 9].
         * Then apply the opposite op (oop) to get the following equation: b = c oop a.
         * This way, the result is always a single digit number.
         * Ex: 4 + 8 = 12, 8 = 12 - 4, -14 - 7 = -21, 7 = -14 + 21, 20 * 3 = 60, 3 = 60 / 20
         */
        val a = (-99..99).random() // -99-99
        answer = (0..9).random() // 0-9
        val c: Int

        textViewMath.text = when ((0..2).random()) {
            0 -> {
                // op = '+'
                c = a + answer
                if (a >= 0) {
                    getString(R.string.equation, c, '-', a, '?')
                } else {
                    getString(R.string.equation, c, '+', -a, '?')
                }
            }
            1 -> {
                // op = '-'
                c = a - answer
                if (c >= 0) {
                    getString(R.string.equation, a, '-', c, '?')
                } else {
                    getString(R.string.equation, a, '+', -c, '?')
                }
            }
            else -> {
                // op = '*'
                c = a * answer
                getString(R.string.equation, c, '/', a, '?')
            }
        }
    }

    private fun showPopup() {
        val width = LinearLayout.LayoutParams.MATCH_PARENT
        val height = LinearLayout.LayoutParams.WRAP_CONTENT
        val popupView = PopupPlayAgainBinding.inflate(layoutInflater)
        val popupWindow = PopupWindow(popupView.root, width, height)
        popupWindow.showAtLocation(layoutPage, Gravity.CENTER, 0, 0)

        val textViewHighScore = popupView.textViewHighScore
        val buttonPlayAgain = popupView.imageButtonPlayAgain
        val buttonLeaderboards = popupView.imageButtonLeaderboards

        // Show the highest score in this session
        if (score > highScore) {
            textViewHighScore.text = getString(R.string.high_score, score)
            // Make additional text red
            if (Build.VERSION.SDK_INT < 24) {
                @Suppress("DEPRECATION")
                textViewHighScore.append(
                    Html.fromHtml("&nbsp;<font color=\"red\">New record!</font>"))
            } else {
                textViewHighScore.append(
                    Html.fromHtml("&nbsp;<font color=\"red\">New record!</font>",
                        Html.FROM_HTML_MODE_COMPACT))
            }

            highScore = score

            // Save the score to the leaderboards
            signedInAccount?.let { account ->
                Games.getLeaderboardsClient(this, account)
                    .submitScore(getString(R.string.leaderboard_id), score)
                Toast.makeText(this, "High score saved to the leaderboards!", Toast.LENGTH_SHORT).show()
            }
        } else {
            textViewHighScore.text = getString(R.string.high_score, highScore)
        }

        // Don't let the user tap outside the area until they play again
        buttonPlayAgain.setOnClickListener {
            // Reset the game
            canvasView.clear()
            generateRandomEquation()
            score = 0

            gameOver = false
            popupWindow.dismiss()
            startTimer(startTime) // restart the timer
        }

        buttonLeaderboards.setOnClickListener {
            if (signedInAccount == null) {
                // Prompt the user to sign in if they want to access the leaderboards
                refusedSignIn = false
                startSignInIntent()
            } else {
                // Open the Google Play leaderboards
                Games.getLeaderboardsClient(this, signedInAccount!!)
                    .getLeaderboardIntent(getString(R.string.leaderboard_id))
                    .addOnSuccessListener { intent ->
                        leaderboardsResultLauncher.launch(intent)
                    }
                    .addOnFailureListener { err ->
                        println("Failed to open the leaderboards: ${err.message}")
                    }
            }
        }
    }

    private val signInResultLauncher = registerForActivityResult(
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
                    buttonGoogle.contentDescription = getString(R.string.sign_out)
                    Toast.makeText(this, "Signed in to Google", Toast.LENGTH_SHORT).show()
                    updateHighScoreFromLeaderboards()
                } else {
                    // Show a toast message that the user failed to sign in
                    var message = signInResult?.status?.statusMessage

                    if (message == null || message.isEmpty()) {
                        message = "Sign-in failed"
                    }

                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Failed to sign in to Google", Toast.LENGTH_SHORT).show()
            refusedSignIn = true
        }
    }

    private val leaderboardsResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // The leaderboards intent doesn't return any data
        if (result.resultCode == Activity.RESULT_OK) {
            println("Successfully opened the leaderboards!")
            println("Data: ${result.data}")
        } else {
            Toast.makeText(
                this,
                "Failed to show the leaderboards. Are you signed in?",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
