package com.example.minibrainage

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.drawToBitmap
import com.example.minibrainage.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private var answer = 0
    private val digitClassifier = DigitClassifier(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // UI elements
        val layoutPage = binding.layoutPage
        val buttonClassify = binding.buttonClassify
        val buttonReset = binding.buttonReset
        val textViewMath = binding.textViewMath

        // Generate a random math equation to solve
        textViewMath.text = generateRandomEquation()

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
        constraintSet.connect(canvasView.id, ConstraintSet.START, layoutPage.id, ConstraintSet.START, 8)
        constraintSet.connect(canvasView.id, ConstraintSet.END, layoutPage.id, ConstraintSet.END, 8)
        constraintSet.connect(canvasView.id, ConstraintSet.TOP, buttonClassify.id, ConstraintSet.BOTTOM, 8)
        constraintSet.connect(canvasView.id, ConstraintSet.BOTTOM, textViewMath.id, ConstraintSet.TOP, 8)
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
                        Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { err ->
                        Toast.makeText(this,"Couldn't classify drawing: ${err.localizedMessage}",
                            Toast.LENGTH_SHORT).show()
                    }
            }

            Toast.makeText(this, "The correct answer is $answer.", Toast.LENGTH_SHORT).show()
        }

        buttonReset.setOnClickListener {
            // Clear the canvas
            canvasView.clear()
            // Generate a new equation
            textViewMath.text = generateRandomEquation()
        }
    }

    override fun onDestroy() {
        digitClassifier.close() // stop the classifier before closing the app
        super.onDestroy()
    }

    private fun generateRandomEquation(): String {
        /* Start with an equation: a op b = c,
         * where a is in [-99, 99], op is in {+, -, *}, and b is in [0, 9].
         * Then apply the opposite op (oop) to get the following equation: b = c oop a.
         * This way, the result is always a single digit number.
         * Ex: 4 + 8 = 12, 8 = 12 - 4, -14 - 7 = -21, 7 = -14 + 21, 20 * 3 = 60, 3 = 60 / 20
         */
        val a = (-99..99).random() // -99-99
        answer = (0..9).random() // 0-9
        val c: Int

        return when ((0..2).random()) {
            0 -> {
                // op = '+'
                c = a + answer
                "$c ${if (a >= 0) "- $a" else "+ ${-a}"} = ?"
            }
            1 -> {
                // op = '-'
                c = a - answer
                "$a ${if (c >= 0) "- $c" else "+ ${-c}"} = ?"
            }
            else -> {
                // op = '*'
                c = a * answer
                "$c / $a = ?"
            }
        }
    }
}
