package com.example.kinectacomms

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max


class ObjectDetector(context: Context) {
    companion object {
        private const val INPUT_WIDTH = 256
        private const val INPUT_HEIGHT = 256
        private const val INPUT_CHANNELS = 3
    }

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private val modelLoader = ModelLoader(context)

    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!modelLoader.isModelAvailable()) return@withContext false

                val model = modelLoader.loadModel()
                labels = modelLoader.loadLabels()

                interpreter = Interpreter(model, Interpreter.Options().apply {
                    setNumThreads(max(1, Runtime.getRuntime().availableProcessors() - 1))
                })
                true
            } catch (e: Exception) {
                Log.e("ObjectDetector", "Initialization failed", e)
                false
            }
        }
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val inputBuffer = preprocessImage(bitmap)
        val outputScores = FloatArray(labels.size)

        interpreter?.run(inputBuffer, outputScores)

        return outputScores.mapIndexed { index, score ->
            DetectionResult(
                label = labels.getOrElse(index) { "Unknown" },
                confidence = score,
                classIndex = index
            )
        }.filter { it.confidence >= 0.5f }
            .sortedByDescending { it.confidence }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true)
        val inputBuffer = ByteBuffer.allocateDirect(INPUT_WIDTH * INPUT_HEIGHT * INPUT_CHANNELS)
        inputBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
        resized.getPixels(pixels, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)

        for (pixel in pixels) {
            inputBuffer.put(((pixel shr 16) and 0xFF).toByte())
            inputBuffer.put(((pixel shr 8) and 0xFF).toByte())
            inputBuffer.put((pixel and 0xFF).toByte())
        }

        return inputBuffer
    }

    fun close() {
        interpreter?.close()
    }

    data class DetectionResult(
        val label: String,
        val confidence: Float,
        val classIndex: Int
    )
}