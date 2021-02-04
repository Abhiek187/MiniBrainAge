package com.example.minibrainage

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.minibrainage.ml.MnistAug
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DigitClassifier(private val context: Context) {
    private lateinit var model: MnistAug

    var isInitialized = false
        private set

    /** Executor to run inference task in the background. */
    private val executorService: ExecutorService = Executors.newCachedThreadPool()

    fun initialize(): Task<Void> {
        val task = TaskCompletionSource<Void>()
        executorService.execute {
            try {
                initializeInterpreter()
                task.setResult(null)
            } catch (e: IOException) {
                task.setException(e)
            }
        }
        return task.task
    }

    private fun initializeInterpreter() {
        // Load the TF Lite model from the binding.
        model = MnistAug.newInstance(context)
        isInitialized = true
        Log.d(TAG, "Initialized TFLite interpreter.")
    }

    private fun classify(bitmap: Bitmap): List<Pair<Int, Float>> {
        check(isInitialized) { "TF Lite Interpreter is not initialized yet." }

        // Pre-processing: resize the input image to match the model input shape.
        val resizedImage = Bitmap.createScaledBitmap(
            bitmap,
            IMAGE_WIDTH,
            IMAGE_HEIGHT,
            true
        )
        val byteBuffer = convertBitmapToByteBuffer(resizedImage)

        // Create inputs for reference.
        val inputFeature0 = TensorBuffer.createFixedSize(
            // [training examples, height, width, channels]
            intArrayOf(1, IMAGE_HEIGHT, IMAGE_WIDTH, PIXEL_SIZE), DataType.FLOAT32)
        inputFeature0.loadBuffer(byteBuffer)

        // Run model inference and get the result.
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer.floatArray

        // Return the two digits with the highest confidence
        val result = outputFeature0.mapIndexed { index, conf -> Pair(index, conf) }
            .sortedByDescending { (_, value) -> value }
        return result.take(2)
    }

    fun classifyAsync(bitmap: Bitmap): Task<List<Pair<Int, Float>>> {
        val task = TaskCompletionSource<List<Pair<Int, Float>>>()
        executorService.execute {
            val result = classify(bitmap)
            task.setResult(result)
        }
        return task.task
    }

    fun close() {
        executorService.execute {
            // Release the model resources when no longer used.
            model.close()
            Log.d(TAG, "Closed TFLite interpreter.")
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(MODEL_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(IMAGE_WIDTH * IMAGE_HEIGHT)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixelValue in pixels) {
            val r = (pixelValue shr 16 and 0xFF)
            val g = (pixelValue shr 8 and 0xFF)
            val b = (pixelValue and 0xFF)

            // Convert RGB to grayscale and normalize pixel value to [0..1].
            val normalizedPixelValue = (r + g + b) / 3.0f / 255.0f
            byteBuffer.putFloat(normalizedPixelValue)
        }

        return byteBuffer
    }

    companion object {
        private const val TAG = "DigitClassifier"

        private const val FLOAT_TYPE_SIZE = 4
        private const val PIXEL_SIZE = 1
        private const val IMAGE_WIDTH = 28
        private const val IMAGE_HEIGHT = 28
        private const val MODEL_SIZE = FLOAT_TYPE_SIZE * IMAGE_WIDTH * IMAGE_HEIGHT * PIXEL_SIZE
    }
}
