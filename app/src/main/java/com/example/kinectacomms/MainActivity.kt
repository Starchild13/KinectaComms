package com.example.kinectacomms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.example.kinectacomms.ui.theme.KinectaCommsTheme
import androidx.navigation.compose.composable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KinectaCommsTheme {
                KinectaCommsApp()
            }
        }
    }
}

@Composable
fun KinectaCommsApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "object_screen") {
        composable("object_screen") { backStackEntry: NavBackStackEntry ->
            ObjectDetectionScreen(navController)
        }
    }
}









