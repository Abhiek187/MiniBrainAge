package com.example.minibrainage

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.drawToBitmap
import com.example.minibrainage.databinding.ActivityMainBinding
import kotlin.math.floor
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private var answer = 0
    private val digitClassifier = DigitClassifier(this)
    private lateinit var textViewScore: TextView
    private lateinit var textViewMath: TextView

    // Connect the score variable to its corresponding TextView
    private var score = 0
        set(value) {
            field = value
            textViewScore.text = getString(R.string.score, value)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // UI elements
        val layoutPage = binding.layoutPage
        val buttonClassify = binding.buttonClassify
        val buttonReset = binding.buttonReset
        val textViewTimer = binding.textViewTimer
        textViewScore = binding.textViewScore
        textViewMath = binding.textViewMath
        val imageViewCheck = binding.imageViewCheck

        // Generate a random math equation to solve
        generateRandomEquation()

        // Add CanvasView to ConstraintLayout
        val canvasView = CanvasView(this)
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
        constraintSet.connect(canvasView.id, ConstraintSet.END, layoutPage.id, ConstraintSet.END, 8)
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
            if (digitClassifier.isInitialized) {
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

        // Create a 1-minute timer
        val timer = object: CountDownTimer(60000, 1000) {
            override fun onTick(msRemain: Long) {
                val seconds = floor(msRemain / 1000.0).roundToInt()
                val minutes = seconds / 60
                textViewTimer.text = String.format("%01d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                Toast.makeText(applicationContext, "Time's up!", Toast.LENGTH_SHORT).show()
                textViewTimer.text = String.format("0:00")
            }
        }

        timer.start()
    }

    override fun onDestroy() {
        digitClassifier.close() // stop the classifier before closing the app
        super.onDestroy()
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
}
