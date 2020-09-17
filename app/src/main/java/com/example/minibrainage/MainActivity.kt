package com.example.minibrainage

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintSet
import com.example.minibrainage.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private var answer = 0

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
        canvasView.setBackgroundColor(Color.WHITE)
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

        buttonClassify.setOnClickListener {
            // Classify the number drawn
            Toast.makeText(this, "The correct answer is $answer.", Toast.LENGTH_SHORT).show()
        }

        buttonReset.setOnClickListener {
            // Clear the canvas
            canvasView.clear()
            // Generate a new equation
            textViewMath.text = generateRandomEquation()
        }
    }

    private fun generateRandomEquation(): String {
        // Make the first number, second number, and operator random and save the answer
        val a = (0..99).random() // 0-99
        val b = (0..99).random()
        val op: Char

        when ((0..4).random()) {
            0 -> {
                op = '+'
                answer = a + b
            }
            1 -> {
                op = '-'
                answer = a - b
            }
            2 -> {
                op = '*'
                answer = a * b
            }
            3 -> {
                op = '/'
                answer = a / b
            }
            else -> {
                op = '%'
                answer = a % b
            }
        }

        return "$a $op $b = ?"
    }
}
