package com.tarun3k.frontandbackvideorecorder.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.video.FileOutputOptions
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tarun3k.frontandbackvideorecorder.camera.DualCameraManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class SavedVideo(
    val id: String,
    val frontVideoPath: String,
    val backVideoPath: String,
    val timestamp: Long,
    val frontVideoUri: android.net.Uri? = null,
    val backVideoUri: android.net.Uri? = null
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private var cameraManager: DualCameraManager? = null

    /**
     * Use external files directory for scoped storage
     * Path: /storage/emulated/0/Android/data/com.tarun3k.frontandbackvideorecorder/files/videos/
     *
     * Benefits:
     * - Accessible via file managers (visible in Android/data/)
     * - No storage permissions required on Android 10+
     * - Automatically deleted when app is uninstalled
     * - Falls back to internal storage if external storage is unavailable
     */
    private val videosDir = File(
        application.getExternalFilesDir(null) ?: application.filesDir,
        "videos"
    )

    private val _savedVideos = MutableStateFlow<List<SavedVideo>>(emptyList())
    val savedVideos: StateFlow<List<SavedVideo>> = _savedVideos.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    init {
        // Create videos directory if it doesn't exist
        if (!videosDir.exists()) {
            videosDir.mkdirs()
        }
        loadSavedVideos()
    }

    fun setCameraManager(manager: DualCameraManager) {
        cameraManager = manager
    }

    private var currentVideoDir: File? = null
    private var currentFrontFile: File? = null
    private var currentBackFile: File? = null

    fun startRecording(context: Context) {
        viewModelScope.launch {
            try {
                val manager = cameraManager ?: run {
                    _errorMessage.value = "Camera manager not initialized"
                    return@launch
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val videoId = "video_$timestamp"

                // Create directory for this video
                val videoDir = File(videosDir, videoId)
                videoDir.mkdirs()

                // Save to app storage
                val frontAppFile = File(videoDir, "front.mp4")
                val backAppFile = File(videoDir, "back.mp4")

                // Store current recording files
                currentVideoDir = videoDir
                currentFrontFile = frontAppFile
                currentBackFile = backAppFile

                Log.d("CameraViewModel", "Starting recording to: ${frontAppFile.absolutePath}")
                Log.d("CameraViewModel", "Starting recording to: ${backAppFile.absolutePath}")

                // Create File output options for app storage
                val frontOutputOptions = FileOutputOptions.Builder(frontAppFile).build()
                val backOutputOptions = FileOutputOptions.Builder(backAppFile).build()

                manager.startRecording(
                    frontOutputOptions,
                    backOutputOptions,
                    java.util.concurrent.Executors.newSingleThreadExecutor()
                ) { frontSuccess, backSuccess ->
                    // This callback is called when both recordings are finalized
                    // Use Dispatchers.Main to safely access ViewModel scope
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        Log.d(
                            "CameraViewModel",
                            "Recording finalized - Front: $frontSuccess, Back: $backSuccess"
                        )

                        // Wait for file system to sync and verify files exist with retries
                        var retries = 5
                        var frontExists = false
                        var backExists = false

                        while (retries > 0 && (!frontExists || !backExists)) {
                            kotlinx.coroutines.delay(300)
                            frontExists = currentFrontFile?.exists() == true
                            backExists = currentBackFile?.exists() == true

                            Log.d(
                                "CameraViewModel",
                                "Checking files (retries left: $retries) - Front: $frontExists, Back: $backExists"
                            )
                            Log.d(
                                "CameraViewModel",
                                "Front file path: ${currentFrontFile?.absolutePath}"
                            )
                            Log.d(
                                "CameraViewModel",
                                "Back file path: ${currentBackFile?.absolutePath}"
                            )

                            if (frontExists && backExists) {
                                break
                            }
                            retries--
                        }

                        if (frontExists && backExists) {
                            Log.d("CameraViewModel", "Both video files saved successfully")
                            // Reload saved videos list
                            viewModelScope.launch {
                                loadSavedVideos()
                            }
                        } else {
                            val errorMsg =
                                "Video files were not saved properly - Front exists: $frontExists, Back exists: $backExists"
                            _errorMessage.value = errorMsg
                            Log.e("CameraViewModel", errorMsg)
                        }
                    }
                }

                // Only set recording to true if start was successful
                _isRecording.value = true

            } catch (e: Exception) {
                _errorMessage.value = "Failed to start recording: ${e.message}"
                _isRecording.value = false
                Log.e("CameraViewModel", "Error starting recording", e)
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            try {
                Log.d("CameraViewModel", "Stopping recording...")
                cameraManager?.stopRecording()
                _isRecording.value = false
                _recordingDuration.value = 0

                // Note: loadSavedVideos() will be called from the recording completion callback
                // We don't call it here immediately because files might not be written yet
                Log.d("CameraViewModel", "Recording stopped, waiting for files to be saved...")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to stop recording: ${e.message}"
                _isRecording.value = false
                Log.e("CameraViewModel", "Error stopping recording", e)
            }
        }
    }

    fun updateRecordingDuration() {
        cameraManager?.updateRecordingDuration()
        cameraManager?.recordingDuration?.value?.let {
            _recordingDuration.value = it
        }
    }

    fun loadSavedVideos() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val videos = mutableListOf<SavedVideo>()

                if (videosDir.exists() && videosDir.isDirectory) {
                    videosDir.listFiles()?.forEach { videoDir ->
                        if (videoDir.isDirectory) {
                            val frontFile = File(videoDir, "front.mp4")
                            val backFile = File(videoDir, "back.mp4")

                            Log.d(
                                "Tarun3kDebug",
                                "front file location is : ${frontFile.absolutePath}"
                            )
                            Log.d(
                                "Tarun3kDebug",
                                "back file location is : ${backFile.absolutePath}"
                            )

                            if (frontFile.exists() || backFile.exists()) {
                                videos.add(
                                    SavedVideo(
                                        id = videoDir.name,
                                        frontVideoPath = frontFile.absolutePath,
                                        backVideoPath = backFile.absolutePath,
                                        timestamp = videoDir.lastModified()
                                    )
                                )
                            }
                        }
                    }
                }

                _savedVideos.value = videos.sortedByDescending { it.timestamp }
            }
        }
    }

    fun deleteVideo(video: SavedVideo) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val videoDir = File(videosDir, video.id)
                    if (videoDir.exists()) {
                        videoDir.deleteRecursively()
                        loadSavedVideos()
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to delete video: ${e.message}"
                }
            }
        }
    }

    fun saveToGallery(context: Context, video: SavedVideo) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val frontFile = File(video.frontVideoPath)
                    val backFile = File(video.backVideoPath)
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                        .format(Date(video.timestamp))
                    if (frontFile.exists()) {
                        // Save front video to gallery
                        saveVideoToGallery(context, frontFile, "dual_front_$timestamp")
                    }
                    if (backFile.exists()) {
                        // Save back video to gallery
                        saveVideoToGallery(context, backFile, "dual_back_$timestamp")
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to save to gallery: ${e.message}"
                }
            }
        }
    }

    private fun saveVideoToGallery(context: Context, file: File, displayName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DualCamera")
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager?.release()
    }
}


