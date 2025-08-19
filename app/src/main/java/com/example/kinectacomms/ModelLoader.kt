package com.example.kinectacomms

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.android.play.core.aipacks.AiPackManager
import com.google.android.play.core.aipacks.AiPackManagerFactory
import com.google.android.play.core.aipacks.AiPackState
import com.google.android.play.core.aipacks.AiPackStateUpdateListener
import com.google.android.play.core.aipacks.model.AiPackStatus
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel




class ModelLoader(private val context: Context) {

    companion object {
        private const val MODEL_FILE = "model_int8_qat.tflite"
        private const val LABELS_FILE = "labels.txt"
    }

    fun isModelAvailable(): Boolean {
        val files = context.assets.list("")?.toList() ?: emptyList()
        Log.d("ModelLoader", "Assets files: $files") // Check Logcat
        return files.contains(MODEL_FILE) && files.contains(LABELS_FILE)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Throws(IOException::class)
    fun loadModel(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = fileDescriptor.createInputStream()
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    @Throws(IOException::class)
    fun loadLabels(): List<String> {
        context.assets.open(LABELS_FILE).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                return reader.readLines()
            }
        }
    }
}




    // Commented out Play Store download logic
    /*
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
    */




