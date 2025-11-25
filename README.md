# 📹 Front & Back Video Recorder

<div align="center">

**Record from both cameras simultaneously on Android**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-blue.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-24%2B-green.svg)](https://www.android.com/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Latest-orange.svg)](https://developer.android.com/jetpack/compose)
[![CameraX](https://img.shields.io/badge/CameraX-1.3.3-red.svg)](https://developer.android.com/training/camerax)

</div>

---

## ✨ Features

- 🎥 **Dual Camera Recording** - Record from front and back cameras simultaneously
- 📱 **Real-time Preview** - See both camera feeds side-by-side in real-time
- 🎬 **High-Quality Video** - Records at the highest available quality
- 🎤 **Audio Recording** - Captures audio along with video from both cameras
- 💾 **Video Gallery** - Browse and manage your saved recordings
- 🎨 **Modern UI** - Beautiful Material 3 design with Jetpack Compose
- 🔄 **Lifecycle Aware** - Properly handles camera lifecycle and permissions

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Camera Library**: CameraX
- **Architecture**: MVVM with ViewModel
- **Navigation**: Navigation Compose
- **Image Loading**: Coil
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 35

## 📋 Requirements

- Android device with **both front and back cameras**
- Android 7.0 (API 24) or higher
- Permissions:
  - `CAMERA` - Required for camera access
  - `RECORD_AUDIO` - Required for audio recording
  - `READ_MEDIA_VIDEO` - Required for accessing saved videos (Android 13+)
  - `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` - Required for older Android versions

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 11 or higher
- Android SDK with API 35

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/FrontAndBackVideoRecorder.git
   cd FrontAndBackVideoRecorder
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory

3. **Sync Gradle**
   - Android Studio will automatically sync Gradle dependencies
   - Wait for the sync to complete

4. **Run the app**
   - Connect an Android device or start an emulator
   - Click the "Run" button or press `Shift + F10`
   - Grant camera and audio permissions when prompted

## 📱 Usage

1. **Launch the app** - The camera screen will open automatically
2. **Grant permissions** - Allow camera and microphone access when prompted
3. **Start recording** - Tap the record button to start recording from both cameras
4. **Stop recording** - Tap the stop button to end the recording
5. **View saved videos** - Navigate to the saved videos screen to see all your recordings

## 🏗️ Architecture

The app follows a clean architecture pattern:

```
app/src/main/java/com/tarun3k/frontandbackvideorecorder/
├── camera/
│   └── DualCameraManager.kt      # Manages dual camera operations
├── ui/
│   ├── screens/
│   │   ├── CameraScreen.kt        # Main camera recording screen
│   │   └── SavedVideosScreen.kt   # Gallery of saved videos
│   └── theme/
│       ├── Color.kt               # App color scheme
│       ├── Theme.kt               # Material 3 theme
│       └── Type.kt                # Typography
├── utils/
│   └── PermissionUtils.kt         # Permission handling utilities
├── viewmodel/
│   └── CameraViewModel.kt         # ViewModel for camera state
└── MainActivity.kt                 # Main activity with navigation
```

### Key Components

- **DualCameraManager**: Handles initialization, preview setup, and recording for both cameras
- **CameraViewModel**: Manages UI state and coordinates with DualCameraManager
- **CameraScreen**: Main UI for camera preview and recording controls
- **SavedVideosScreen**: Displays list of recorded videos

## 🔧 How It Works

The app uses CameraX's `ProcessCameraProvider` to bind two separate camera instances:

1. **Front Camera**: Bound to its own lifecycle owner with preview and video capture use cases
2. **Back Camera**: Bound to a separate lifecycle owner with preview and video capture use cases

Both cameras record simultaneously to separate video files, allowing you to capture different perspectives at the same time.

## 📸 Screenshots

_Add screenshots of your app here_

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 👤 Author

**Tarun Kumar**

- GitHub: [@tarun3k](https://github.com/tarun3k)

## 🙏 Acknowledgments

- [CameraX](https://developer.android.com/training/camerax) - For the excellent camera library
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - For the modern UI framework
- [Material 3](https://m3.material.io/) - For the design system

---

<div align="center">

**Made with ❤️ using Kotlin and Jetpack Compose**

⭐ Star this repo if you find it helpful!

</div>

