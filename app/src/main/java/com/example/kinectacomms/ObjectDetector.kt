package com.example.kinectacomms


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class ObjectDetector(context: Context) {

    companion object {
        private const val MIN_CONFIDENCE = 0.5f
        private const val MAX_DETECTIONS = 10
    }

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private val modelLoader = ModelLoader(context)

    private var inputWidth = 0
    private var inputHeight = 0
    private var inputChannels = 3

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!modelLoader.isModelAvailable()) {
                Log.e("ObjectDetector", "Model or labels not found in assets")
                return@withContext false
            }

            val model = modelLoader.loadModel()
            labels = modelLoader.loadLabels()

            interpreter = Interpreter(model, Interpreter.Options().apply {
                setNumThreads(max(1, Runtime.getRuntime().availableProcessors() - 1))
                setUseXNNPACK(true)
            }).also { interp ->
                val inputTensor = interp.getInputTensor(0)
                inputWidth = inputTensor.shape()[1]
                inputHeight = inputTensor.shape()[2]
                inputChannels = inputTensor.shape()[3]
                Log.d(
                    "ModelInfo",
                    "Input: ${inputWidth}x${inputHeight}x${inputChannels}, type: ${inputTensor.dataType()}"
                )
            }

            true
        } catch (e: Exception) {
            Log.e("ObjectDetector", "Initialization failed", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun detectTopThree(bitmap: Bitmap): DetectionResultWithBitmap = withContext(Dispatchers.Default) {
        if (interpreter == null) {
            Log.e("ObjectDetector", "Interpreter not initialized")
            return@withContext DetectionResultWithBitmap(emptyList(), bitmap)
        }

        try {
            val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else bitmap

            val resizedBitmap = Bitmap.createScaledBitmap(safeBitmap, inputWidth, inputHeight, true)

            // Allocate FLOAT32 input buffer
            val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * inputChannels)
                .order(ByteOrder.nativeOrder())

            // Fill input buffer with normalized RGB
            val pixels = IntArray(inputWidth * inputHeight)
            resizedBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
            for (pixel in pixels) {
                inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
                inputBuffer.putFloat((pixel and 0xFF) / 255.0f)
            }
            inputBuffer.rewind()

            // Output buffers (FLOAT32)
            val outputLocations = Array(1) { Array(MAX_DETECTIONS) { FloatArray(4) } } // [ymin, xmin, ymax, xmax]
            val outputClasses = Array(1) { FloatArray(MAX_DETECTIONS) }
            val outputScores = Array(1) { FloatArray(MAX_DETECTIONS) }
            val numDetections = FloatArray(1)

            val outputMap = mapOf(
                0 to outputLocations,
                1 to outputClasses,
                2 to outputScores,
                3 to numDetections
            )

            interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)

            // Parse detections
            val results = mutableListOf<DetectionResult>()
            val count = numDetections[0].toInt().coerceAtMost(MAX_DETECTIONS)
            for (i in 0 until count) {
                val score = outputScores[0][i]
                if (score >= MIN_CONFIDENCE) {
                    val classIdx = outputClasses[0][i].toInt()
                    val label = labels.getOrElse(classIdx) { "Unknown" }
                    val bbox = outputLocations[0][i]
                    results.add(DetectionResult(label, score, classIdx, bbox))
                }
            }

            val topThree = results.sortedByDescending { it.confidence }.take(3)

            // Draw boxes
            val annotatedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(annotatedBitmap)
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                color = Color.RED
                strokeWidth = 4f
            }
            val textPaint = Paint().apply {
                color = Color.RED
                textSize = 40f
                style = Paint.Style.FILL
            }
            val width = bitmap.width
            val height = bitmap.height

            topThree.forEach { det ->
                val box = det.boundingBox
                val rect = RectF(
                    box[1] * width, // xmin
                    box[0] * height, // ymin
                    box[3] * width, // xmax
                    box[2] * height  // ymax
                )
                canvas.drawRect(rect, paint)
                canvas.drawText("${det.label} ${(det.confidence * 100).toInt()}%", rect.left, rect.top - 10, textPaint)
            }

            DetectionResultWithBitmap(topThree, annotatedBitmap)

        } catch (e: Exception) {
            Log.e("ObjectDetector", "Detection failed", e)
            DetectionResultWithBitmap(emptyList(), bitmap)
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    data class DetectionResult(
        val label: String,
        val confidence: Float,
        val classIndex: Int,
        val boundingBox: FloatArray
    )

    data class DetectionResultWithBitmap(
        val detections: List<DetectionResult>,
        val bitmap: Bitmap
    )
}



