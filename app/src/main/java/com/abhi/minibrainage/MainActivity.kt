package com.abhi.minibrainage

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.drawToBitmap
import androidx.viewbinding.ViewBinding
import com.abhi.minibrainage.databinding.ActivityMainBinding
import com.abhi.minibrainage.databinding.PopupPlayAgainBinding
import com.abhi.minibrainage.databinding.PopupStartBinding
import kotlin.math.floor
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    // UI elements
    private lateinit var layoutPage: ConstraintLayout
    private lateinit var canvasView: CanvasView
    private lateinit var textViewTimer: TextView
    private lateinit var textViewScore: TextView
    private lateinit var textViewMath: TextView

    // Timer variables
    private var timer: CountDownTimer? = null
    private var oldTextColor = 0 // color code
    private val startTime = 60000L // 1-minute timer
    private var remainingTime = startTime
    private var resumeTime = remainingTime

    // Score variables
    private lateinit var prefs: SharedPrefs
    // Make scores long to directly submit them to the leaderboards
    private var score = 0L
        set(value) {
            field = value
            // Connect the score variable to its corresponding TextView
            textViewScore.text = getString(R.string.score, value)
        }

    // Other game variables
    private lateinit var digitClassifier: DigitClassifier
    private var answer = 0
    private var gameOver = true
    private lateinit var playServices: PlayServices

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the UI elements
        layoutPage = binding.layoutPage
        val buttonSubmit = binding.buttonSubmit
        val buttonClear = binding.buttonClear
        textViewTimer = binding.textViewTimer
        textViewScore = binding.textViewScore
        textViewMath = binding.textViewMath
        val imageViewCheck = binding.imageViewCheck
        val drawIcon = binding.imageViewDrawIcon

        oldTextColor = textViewTimer.currentTextColor
        textViewScore.text = getString(R.string.score, score) // show initial score

        // Initialize all external classes
        prefs = SharedPrefs(this)
        digitClassifier = DigitClassifier(this)
        playServices = PlayServices(this, binding.imageButtonGoogle)

        // Generate a random math equation to solve
        generateRandomEquation()

        // Add a CanvasView to the ConstraintLayout
        canvasView = CanvasView(this)
        canvasView.drawIcon = drawIcon
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
            textViewMath.id,
            ConstraintSet.BOTTOM,
            8
        )
        constraintSet.connect(
            canvasView.id,
            ConstraintSet.BOTTOM,
            buttonSubmit.id,
            ConstraintSet.TOP,
            8
        )
        // Make the canvas a square
        constraintSet.setDimensionRatio(canvasView.id, "1:1")
        constraintSet.applyTo(layoutPage)

        // Initialize the digit classifier
        digitClassifier.initialize().addOnFailureListener { err ->
            Toast.makeText(
                this, "Failed to set up digit classifier: ${err.localizedMessage}",
                Toast.LENGTH_SHORT
            ).show()
        }

        buttonSubmit.setOnClickListener {
            // Classify the number drawn
            if (digitClassifier.isInitialized && !gameOver) {
                /* To prevent users from spamming the submit button, only count attempts where the
                 * user drew something on the canvas
                 */
                if (canvasView.skipped()) {
                    // Skip the question, clear the canvas, and generate a new equation
                    canvasView.clear()
                    generateRandomEquation()
                    return@setOnClickListener
                }

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

        buttonClear.setOnClickListener {
            // Clear the canvas
            canvasView.clear()
        }

        // Show the start screen
        showPopupStart()
    }

    override fun onPause() {
        super.onPause()
        // Since remainingTime continues to tick, use another variable instead
        resumeTime = remainingTime
        timer?.cancel() // stop the timer
    }

    override fun onResume() {
        super.onResume()
        playServices.signInSilently()

        if (!gameOver) {
            startTimer(resumeTime) // resume with the same number of seconds remaining
        }
    }

    override fun onDestroy() {
        digitClassifier.close() // stop the classifier before closing the app
        super.onDestroy()
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
                showPopupPlayAgain()
            }
        }

        timer?.start()
    }

    // All the possible operations to calculate the answer
    enum class Op {
        ADD,
        SUBTRACT,
        MULTIPLY
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

        // If a = 0, don't multiply since dividing by 0 is invalid
        val op = if (a == 0) {
            Op.values().filterNot { it == Op.MULTIPLY }.random() // + or -
        } else {
            Op.values().random() // +, -, or *
        }

        textViewMath.text = when (op) {
            Op.ADD -> {
                c = a + answer
                if (a >= 0) {
                    getString(R.string.equation, c, '-', a, '?')
                } else {
                    getString(R.string.equation, c, '+', -a, '?')
                }
            }
            Op.SUBTRACT -> {
                c = a - answer
                if (c >= 0) {
                    getString(R.string.equation, a, '-', c, '?')
                } else {
                    getString(R.string.equation, a, '+', -c, '?')
                }
            }
            else -> {
                c = a * answer
                getString(R.string.equation, c, '/', a, '?')
            }
        }
    }

    private fun showPopup(popupView: ViewBinding): PopupWindow {
        // Show the popup at the center of the screen
        val width = LinearLayout.LayoutParams.MATCH_PARENT
        val height = LinearLayout.LayoutParams.WRAP_CONTENT
        val popupWindow = PopupWindow(popupView.root, width, height)

        // If starting the app, wait until the main layout has initialized
        layoutPage.post {
            popupWindow.showAtLocation(layoutPage, Gravity.CENTER, 0, 0)
        }

        return popupWindow
    }

    private fun restartGame(popupWindow: PopupWindow) {
        // Reset the game
        canvasView.clear()
        generateRandomEquation()
        score = 0

        gameOver = false
        popupWindow.dismiss()
        startTimer(startTime) // restart the timer
    }

    private fun showPopupStart() {
        val popupView = PopupStartBinding.inflate(layoutInflater)
        val popupWindow = showPopup(popupView)

        val buttonStart = popupView.imageButtonStart

        // Don't let the user tap outside the area until they hit start
        buttonStart.setOnClickListener {
            restartGame(popupWindow)
            canvasView.didStart = true
        }
    }

    private fun showPopupPlayAgain() {
        val popupView = PopupPlayAgainBinding.inflate(layoutInflater)
        val popupWindow = showPopup(popupView)

        val textViewHighScore = popupView.textViewHighScore
        val buttonPlayAgain = popupView.imageButtonPlayAgain
        val buttonLeaderboards = popupView.imageButtonLeaderboards

        // Show the highest score in this session
        if (score > prefs.highScore) {
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

            prefs.highScore = score
            playServices.saveToLeaderboards(score)
        } else {
            textViewHighScore.text = getString(R.string.high_score, prefs.highScore)
        }

        // Don't let the user tap outside the area until they play again
        buttonPlayAgain.setOnClickListener {
            restartGame(popupWindow)
        }

        buttonLeaderboards.setOnClickListener {
            playServices.openLeaderboards()
        }
    }
}
