package com.tarun3k.frontandbackvideorecorder.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tarun3k.frontandbackvideorecorder.viewmodel.CameraViewModel
import com.tarun3k.frontandbackvideorecorder.viewmodel.SavedVideo
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedVideosScreen(
    viewModel: CameraViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val savedVideos by viewModel.savedVideos.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    var showDeleteDialog by remember { mutableStateOf<SavedVideo?>(null) }
    
    // Storage permission launcher - handles multiple permissions
    var pendingVideoToSave by remember { mutableStateOf<SavedVideo?>(null) }
    
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        if (allGranted && pendingVideoToSave != null) {
            pendingVideoToSave?.let { video ->
                viewModel.saveToGallery(context, video)
                pendingVideoToSave = null
            }
        } else if (!allGranted) {
            pendingVideoToSave = null
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.loadSavedVideos()
    }
    
    // Show snackbar for errors
    val snackbarHostState = remember { SnackbarHostState() }
    
    errorMessage?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Dismiss"
            )
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Videos") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        if (savedVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No saved videos",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(savedVideos) { video ->
                    VideoItem(
                        video = video,
                        onSaveToGallery = {
                            val storagePermissions = com.tarun3k.frontandbackvideorecorder.utils.PermissionUtils.getStoragePermissionsForGallery()
                            
                            if (storagePermissions.isEmpty()) {
                                // Android 11+ doesn't need storage permissions for MediaStore
                                viewModel.saveToGallery(context, video)
                            } else {
                                val hasAllPermissions = storagePermissions.all {
                                    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                                }
                                
                                if (hasAllPermissions) {
                                    viewModel.saveToGallery(context, video)
                                } else {
                                    // Request permissions and save video reference
                                    pendingVideoToSave = video
                                    storagePermissionLauncher.launch(storagePermissions)
                                }
                            }
                        },
                        onDelete = {
                            showDeleteDialog = video
                        }
                    )
                }
            }
        }
        
        // Delete confirmation dialog
        showDeleteDialog?.let { video ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Delete Video") },
                text = { Text("Are you sure you want to delete this video? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteVideo(video)
                            showDeleteDialog = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun VideoItem(
    video: SavedVideo,
    onSaveToGallery: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(video.timestamp))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dual Camera Video",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Front: ${video.frontVideoPath.split("/").lastOrNull() ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Back: ${video.backVideoPath.split("/").lastOrNull() ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSaveToGallery) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save to Gallery")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

