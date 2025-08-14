package com.example.kinectacomms

import android.content.Context
import com.google.android.play.core.aipacks.AiPackManager
import com.google.android.play.core.aipacks.AiPackManagerFactory
import com.google.android.play.core.aipacks.AiPackState
import com.google.android.play.core.aipacks.AiPackStateUpdateListener
import com.google.android.play.core.aipacks.model.AiPackStatus
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ModelLoader(private val context: Context) {

    companion object {
        private const val AI_PACK_NAME = "ai_model_pack"
        private const val MODEL_FILE = "model_int8_qat.tflite"
        private const val LABELS_FILE = "labels.txt"
    }

    private val aiPackManager: AiPackManager = AiPackManagerFactory.getInstance(context)

    fun isModelAvailable(): Boolean =
        aiPackManager.getPackLocation(AI_PACK_NAME) != null

    @Throws(IOException::class)
    fun loadModel(): MappedByteBuffer {
        val packLocation = aiPackManager.getPackLocation(AI_PACK_NAME)
            ?: throw IOException("AI pack not downloaded")

        val modelFile = File(packLocation.assetsPath(), MODEL_FILE)
        FileInputStream(modelFile).use { inputStream ->
            val channel = inputStream.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }

    @Throws(IOException::class)
    fun loadLabels(): List<String> {
        val packLocation = aiPackManager.getPackLocation(AI_PACK_NAME)
            ?: throw IOException("AI pack not downloaded")

        val labelsFile = File(packLocation.assetsPath(), LABELS_FILE)
        return labelsFile.bufferedReader().use { it.readLines() }
    }

    fun downloadModel(
        onProgress: (Float) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val listener = object : AiPackStateUpdateListener {
            override fun onStateUpdate(state: AiPackState) {
                when (state.status()) {
                    AiPackStatus.DOWNLOADING -> {
                        val progress = if (state.totalBytesToDownload() > 0) {
                            state.bytesDownloaded().toFloat() / state.totalBytesToDownload()
                        } else 0f
                        onProgress(progress)
                    }
                    AiPackStatus.COMPLETED -> {
                        onSuccess()
                        aiPackManager.unregisterListener(this)
                    }
                    AiPackStatus.FAILED -> {
                        onError("Download failed: ${state.errorCode()}")
                        aiPackManager.unregisterListener(this)
                    }
                    else -> {
                        // Handle other states if needed
                    }
                }
            }
        }

        aiPackManager.registerListener(listener)

        aiPackManager.fetch(listOf(AI_PACK_NAME))
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    onError("Fetch failed: ${task.exception?.message}")
                    aiPackManager.unregisterListener(listener)
                }
            }
    }
}


