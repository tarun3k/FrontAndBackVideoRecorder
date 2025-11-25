package com.tarun3k.frontandbackvideorecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tarun3k.frontandbackvideorecorder.ui.screens.CameraScreen
import com.tarun3k.frontandbackvideorecorder.ui.screens.SavedVideosScreen
import com.tarun3k.frontandbackvideorecorder.ui.theme.FrontAndBackVideoRecorderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrontAndBackVideoRecorderTheme {
                DualCameraApp()
            }
        }
    }
}

@Composable
fun DualCameraApp() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "camera"
    ) {
        composable("camera") {
            CameraScreen(
                onNavigateToVideos = {
                    navController.navigate("videos")
                }
            )
        }
        composable("videos") {
            SavedVideosScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}