package com.tarun3k.frontandbackvideorecorder.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

class DualCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val TAG = "DualCameraManager"
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var frontCameraSelector: CameraSelector? = null
    private var backCameraSelector: CameraSelector? = null
    
    // Create separate lifecycle owners for each camera
    private val frontLifecycleOwner = object : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)

        fun updateState(state: Lifecycle.State) {
            lifecycleRegistry.currentState = state
        }

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry
    }
    
    private val backLifecycleOwner = object : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)

        fun updateState(state: Lifecycle.State) {
            lifecycleRegistry.currentState = state
        }

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry
    }
    private var frontPreview: Preview? = null
    private var backPreview: Preview? = null
    private var frontRecorder: Recorder? = null
    private var backRecorder: Recorder? = null
    private var frontVideoCapture: VideoCapture<Recorder>? = null
    private var backVideoCapture: VideoCapture<Recorder>? = null
    private var frontRecording: Recording? = null
    private var backRecording: Recording? = null
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()
    
    private var recordingStartTime: Long = 0
    
    // Callbacks for recording completion
    private var onFrontRecordingComplete: ((Boolean) -> Unit)? = null
    private var onBackRecordingComplete: ((Boolean) -> Unit)? = null
    private var frontRecordingCompleted = false
    private var backRecordingCompleted = false
    
    suspend fun initialize(): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)

                if (capabilities?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
                    ) == true
                ) {
                    Log.d("DualCameraManager", "Supports multi-camera: $id")
                } else {
                    Log.d("DualCameraManager", "Does NOT support multi-camera: $id")
                }
            }
            val camera2Interop = Camera2Interop.Extender(Preview.Builder())
            camera2Interop.setCaptureRequestOption(
                CaptureRequest.CONTROL_MODE,
                CameraMetadata.CONTROL_MODE_AUTO
            )
            cameraProvider = ProcessCameraProvider.getInstance(context).get()

            cameraProvider?.availableCameraInfos?.forEach {
                Log.d("DualCameraManager", "camera state = ${it.cameraState} , zoomState= ${it.zoomState},exposureState= ${it.exposureState} , lensFacing= ${it.lensFacing} " +
                        "${it.cameraSelector.toString()} check for the state  ${ it.implementationType}")
            }

            Log.d("DualCameraManager", "total cameras info ${cameraProvider?.availableCameraInfos}")
            // Check if device supports multiple cameras by trying to get camera info
            val hasFrontCamera = try {
                cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
            } catch (e: Exception) {
                false
            }
            
            val hasBackCamera = try {
                cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
            } catch (e: Exception) {
                false
            }
            
            if (!hasFrontCamera || !hasBackCamera) {
                Log.e(TAG, "Device does not support both front and back cameras")
                return false
            }
            
            frontCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            backCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera provider", e)
            false
        }
    }
    
    fun setupPreviews(
        frontPreviewView: PreviewView,
        backPreviewView: PreviewView
    ) {
        val provider = cameraProvider ?: return
        
        try {
            // Unbind any existing cameras first
            provider.unbindAll()
            
            // Sync lifecycle states with main lifecycle owner
            // Cameras need to be in at least CREATED state to bind, but RESUMED to actually start
            val mainState = lifecycleOwner.lifecycle.currentState
            val targetState = when {
                mainState.isAtLeast(Lifecycle.State.RESUMED) -> Lifecycle.State.RESUMED
                mainState.isAtLeast(Lifecycle.State.STARTED) -> Lifecycle.State.STARTED
                mainState.isAtLeast(Lifecycle.State.CREATED) -> Lifecycle.State.CREATED
                else -> Lifecycle.State.CREATED
            }
            
            Log.d(TAG, "Main lifecycle state: $mainState, Setting camera lifecycle to: $targetState")
            frontLifecycleOwner.updateState(targetState)
            backLifecycleOwner.updateState(targetState)
            
            // Observe main lifecycle to keep both in sync
            lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    val newState = source.lifecycle.currentState
                    // Sync the state, but ensure we're at least in CREATED state
                    val syncState = when {
                        newState.isAtLeast(Lifecycle.State.RESUMED) -> Lifecycle.State.RESUMED
                        newState.isAtLeast(Lifecycle.State.STARTED) -> Lifecycle.State.STARTED
                        newState.isAtLeast(Lifecycle.State.CREATED) -> Lifecycle.State.CREATED
                        else -> Lifecycle.State.CREATED
                    }
                    Log.d(TAG, "Lifecycle event: $event, syncing cameras to state: $syncState")
                    frontLifecycleOwner.updateState(syncState)
                    backLifecycleOwner.updateState(syncState)
                }
            })
            
            // Setup front camera preview
            frontPreview = Preview.Builder().build().also {
                it.setSurfaceProvider(frontPreviewView.surfaceProvider)
            }
            
            // Setup back camera preview
            backPreview = Preview.Builder().build().also {
                it.setSurfaceProvider(backPreviewView.surfaceProvider)
            }
            
            // Setup front camera recorder
            frontRecorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            frontVideoCapture = VideoCapture.withOutput(frontRecorder!!)
            
            // Setup back camera recorder
            backRecorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            backVideoCapture = VideoCapture.withOutput(backRecorder!!)
            
            // Bind front camera to its own lifecycle owner
            val frontUseCaseGroup = UseCaseGroup.Builder()
                .addUseCase(frontPreview!!)
                .addUseCase(frontVideoCapture!!)
                .build()
            
            Log.d(TAG, "Binding front camera with lifecycle state: ${frontLifecycleOwner.lifecycle.currentState}")
            provider.bindToLifecycle(
                frontLifecycleOwner,
                frontCameraSelector!!,
                frontUseCaseGroup
            )
            Log.d(TAG, "Front camera bound successfully")
            
            // Bind back camera to its own lifecycle owner
            val backUseCaseGroup = UseCaseGroup.Builder()
                .addUseCase(backPreview!!)
                .addUseCase(backVideoCapture!!)
                .build()
            
            Log.d(TAG, "Binding back camera with lifecycle state: ${backLifecycleOwner.lifecycle.currentState}")
            provider.bindToLifecycle(
                backLifecycleOwner,
                backCameraSelector!!,
                backUseCaseGroup
            )
            Log.d(TAG, "Back camera bound successfully")
            
            Log.d(TAG, "Camera previews setup successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup camera previews", e)
            throw e
        }
    }
    
    fun startRecording(
        frontOutputFile: FileOutputOptions,
        backOutputFile: FileOutputOptions,
        executor: Executor,
        onRecordingComplete: ((Boolean, Boolean) -> Unit)? = null
    ) {
        if (_isRecording.value) {
            Log.w(TAG, "Recording already in progress")
            return
        }
        
        try {
            val frontVideoCapture = this.frontVideoCapture ?: return
            val backVideoCapture = this.backVideoCapture ?: return
            
            // Reset completion flags
            frontRecordingCompleted = false
            backRecordingCompleted = false
            
            // Start front camera recording
            frontRecording = frontVideoCapture.output
                .prepareRecording(context, frontOutputFile)
                .withAudioEnabled()
                .start(executor) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            Log.d(TAG, "Front camera recording started")
                        }
                        is VideoRecordEvent.Finalize -> {
                            frontRecordingCompleted = true
                            if (!event.hasError()) {
                                Log.d(TAG, "Front camera recording saved: ${event.outputResults.outputUri}")
                                checkBothRecordingsComplete(onRecordingComplete)
                            } else {
                                Log.e(TAG, "Front camera recording error: ${event.error}")
                                checkBothRecordingsComplete(onRecordingComplete)
                            }
                        }
                    }
                }
            
            // Start back camera recording
            backRecording = backVideoCapture.output
                .prepareRecording(context, backOutputFile)
                .withAudioEnabled()
                .start(executor) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            Log.d(TAG, "Back camera recording started")
                        }
                        is VideoRecordEvent.Finalize -> {
                            backRecordingCompleted = true
                            if (!event.hasError()) {
                                Log.d(TAG, "Back camera recording saved: ${event.outputResults.outputUri}")
                                checkBothRecordingsComplete(onRecordingComplete)
                            } else {
                                Log.e(TAG, "Back camera recording error: ${event.error}")
                                checkBothRecordingsComplete(onRecordingComplete)
                            }
                        }
                    }
                }
            
            _isRecording.value = true
            recordingStartTime = System.currentTimeMillis()
            Log.d(TAG, "Recording started for both cameras")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
        }
    }
    
    private fun checkBothRecordingsComplete(onRecordingComplete: ((Boolean, Boolean) -> Unit)?) {
        if (frontRecordingCompleted && backRecordingCompleted) {
            val frontSuccess = true // We'll check file existence in ViewModel
            val backSuccess = true
            onRecordingComplete?.invoke(frontSuccess, backSuccess)
        }
    }
    
    fun stopRecording() {
        if (!_isRecording.value) {
            return
        }
        
        try {
            frontRecording?.stop()
            backRecording?.stop()
            frontRecording = null
            backRecording = null
            
            _isRecording.value = false
            _recordingDuration.value = 0
            Log.d(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
        }
    }
    
    fun updateRecordingDuration() {
        if (_isRecording.value) {
            _recordingDuration.value = System.currentTimeMillis() - recordingStartTime
        }
    }
    
    fun release() {
        stopRecording()
        cameraProvider?.unbindAll()
        cameraProvider  = null
        frontPreview = null
        backPreview = null
        frontRecorder = null
        backRecorder = null
        frontVideoCapture = null
        backVideoCapture = null
    }
}

