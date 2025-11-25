package com.tarun3k.frontandbackvideorecorder.ui.screens

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tarun3k.frontandbackvideorecorder.camera.DualCameraManager
import com.tarun3k.frontandbackvideorecorder.viewmodel.CameraViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel(),
    onNavigateToVideos: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var cameraManager by remember { mutableStateOf<DualCameraManager?>(null) }
    var frontPreviewView by remember { mutableStateOf<PreviewView?>(null) }
    var backPreviewView by remember { mutableStateOf<PreviewView?>(null) }
    var isInitialized by remember { mutableStateOf(false) }
    var initializationError by remember { mutableStateOf<String?>(null) }
    
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    
    // Permissions - Only camera and audio needed for recording
    // Storage permissions not needed since we save to app storage
    val permissions = com.tarun3k.frontandbackvideorecorder.utils.PermissionUtils.getCameraPermissions()
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        if (allGranted) {
            scope.launch {
                val manager = DualCameraManager(context, lifecycleOwner)
                if (manager.initialize()) {
                    cameraManager = manager
                    viewModel.setCameraManager(manager)
                    
                    frontPreviewView?.let { frontView ->
                        backPreviewView?.let { backView ->
                            manager.setupPreviews(frontView, backView)
                            isInitialized = true
                        }
                    }
                } else {
                    initializationError = "Failed to initialize cameras"
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        val hasPermissions = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (hasPermissions) {
            val manager = DualCameraManager(context, lifecycleOwner)
            if (manager.initialize()) {
                cameraManager = manager
                viewModel.setCameraManager(manager)
            } else {
                initializationError = "Failed to initialize cameras"
            }
        } else {
            permissionLauncher.launch(permissions)
        }
    }
    
    LaunchedEffect(frontPreviewView, backPreviewView, cameraManager) {
        if (frontPreviewView != null && backPreviewView != null && cameraManager != null && !isInitialized) {
            cameraManager?.setupPreviews(frontPreviewView!!, backPreviewView!!)
            isInitialized = true
        }
    }
    
    // Update recording duration
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                kotlinx.coroutines.delay(100)
                viewModel.updateRecordingDuration()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dual Camera Recorder") },
                actions = {
                    IconButton(onClick = onNavigateToVideos) {
                        Icon(Icons.Default.Favorite, contentDescription = "Saved Videos")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (initializationError != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = initializationError!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        initializationError = null
                        permissionLauncher.launch(permissions)
                    }) {
                        Text("Retry")
                    }
                }
            } else {
                // Split screen camera preview
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Front camera (top)
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also {
                                frontPreviewView = it
                                if (backPreviewView != null && cameraManager != null) {
                                    cameraManager?.setupPreviews(it, backPreviewView!!)
                                    isInitialized = true
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    
                    // Back camera (bottom)
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also {
                                backPreviewView = it
                                if (frontPreviewView != null && cameraManager != null) {
                                    cameraManager?.setupPreviews(frontPreviewView!!, it)
                                    isInitialized = true
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                
                // Recording controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Recording duration
                    if (isRecording) {
                        Text(
                            text = formatDuration(recordingDuration),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Record button
                    FloatingActionButton(
                        onClick = {
                            if (isRecording) {
                                viewModel.stopRecording()
                            } else {
                                viewModel.startRecording(context)
                            }
                        },
                        modifier = Modifier.size(64.dp),
                        containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Close else Icons.Default.Star,
                            contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

