package com.example.minibrainage

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.example.minibrainage.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // UI elements
        val layoutPage = binding.layoutPage
        val buttonClassify = binding.buttonClassify
        val buttonReset = binding.buttonReset

        // Add CanvasView to ConstraintLayout
        val canvasView = CanvasView(this)
        layoutPage.addView(canvasView)

        buttonClassify.setOnClickListener {
            // Classify the number drawn
            Toast.makeText(this, "You drew something.", Toast.LENGTH_SHORT).show()
        }

        buttonReset.setOnClickListener {
            // Clear the canvas
            canvasView.clear()
        }
    }
}
