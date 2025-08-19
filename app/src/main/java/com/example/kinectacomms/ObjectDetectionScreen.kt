package com.example.kinectacomms

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectDetectionScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val detector = remember { ObjectDetector(context) }

    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var detectionResult by remember { mutableStateOf<ObjectDetector.DetectionResultWithBitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var modelInitialized by remember { mutableStateOf(false) }

    // Image picker
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isLoading = true
            scope.launch {
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                    } else {
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }

                    selectedImage = bitmap

                    if (modelInitialized) {
                        detectionResult = detector.detectTopThree(bitmap)
                    } else {
                        errorMessage = "Model not initialized"
                    }
                } catch (e: Exception) {
                    errorMessage = "Failed to process image: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Initialize model
    LaunchedEffect(Unit) {
        val initialized = detector.initialize()
        if (!initialized) {
            errorMessage = "Failed to initialize model. Ensure model and labels exist in assets."
        } else modelInitialized = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Object Detection") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { imagePicker.launch("image/*") },
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Processing..." else "Select Image")
            }

            Spacer(modifier = Modifier.height(16.dp))

            selectedImage?.let { bitmap ->
                // Display bitmap with drawn boxes if detection ran
                val displayBitmap = detectionResult?.bitmap ?: bitmap

                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = "Selected Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show prediction results at the bottom
            detectionResult?.detections?.let { detections ->
                if (detections.isNotEmpty()) {
                    Text("Top Predictions:", style = MaterialTheme.typography.headlineSmall)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        detections.forEach { detection ->
                            Text(
                                "${detection.label} (${String.format("%.1f", detection.confidence * 100)}%)",
                                Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }

            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}



// Download dialog removed since we only load locally
    /*
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Model Required") },
            text = {
                Column {
                    Text("The object detection model needs to be downloaded.")
                    if (downloadProgress > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = downloadProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Downloading... ${(downloadProgress * 100).toInt()}%",
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        modelLoader.downloadModel(
                            onProgress = { progress -> downloadProgress = progress },
                            onSuccess = {
                                scope.launch {
                                    if (detector.initialize()) {
                                        showDownloadDialog = false
                                    } else {
                                        errorMessage = "Failed to initialize model"
                                    }
                                }
                            },
                            onError = { error ->
                                errorMessage = error
                                showDownloadDialog = false
                            }
                        )
                    }
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
   */


