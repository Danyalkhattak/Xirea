# Xirea

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" alt="Xirea Logo" width="120"/>
</p>

<p align="center">
  <b>Offline AI Chat Assistant for Android</b>
</p>

<p align="center">
  <a href="https://github.com/Danyalkhattak/xirea/releases"><img src="https://img.shields.io/github/v/release/Danyalkhattak/xirea?style=for-the-badge&logo=github&color=6366F1" alt="Release"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Danyalkhattak/xirea?style=for-the-badge&logo=opensourceinitiative&logoColor=white&color=10B981" alt="License"/></a>
  <a href="#"><img src="https://img.shields.io/badge/Platform-Android-34A853?style=for-the-badge&logo=android&logoColor=white" alt="Platform"/></a>
  <a href="#"><img src="https://img.shields.io/badge/API-26+-FF6F00?style=for-the-badge&logo=android&logoColor=white" alt="API"/></a>
  <a href="#"><img src="https://img.shields.io/badge/Offline-100%25-EF4444?style=for-the-badge&logo=shieldsdotio&logoColor=white" alt="Offline"/></a>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#screenshots">Screenshots</a> •
  <a href="#installation">Installation</a> •
  <a href="#building">Building</a> •
  <a href="#tech-stack">Tech Stack</a> •
  <a href="#license">License</a>
</p>

---

## Overview

**Xirea** is a fully offline AI chat assistant that runs lightweight language models directly on your Android device. No internet required, no API keys, no data leaving your phone — your conversations stay completely private.

Powered by [llama.cpp](https://github.com/ggerganov/llama.cpp) for efficient on-device inference with GGUF models.

---

## Features

- <img src="https://img.shields.io/badge/-Offline-EF4444?style=flat-square&logo=wifioff&logoColor=white" height="18"/> **100% Offline** — All AI processing happens on-device
- <img src="https://img.shields.io/badge/-Fast-F59E0B?style=flat-square&logo=bolt&logoColor=white" height="18"/> **Fast Inference** — Optimized for mobile with dynamic RAM scaling
- <img src="https://img.shields.io/badge/-Chat-3B82F6?style=flat-square&logo=googlechat&logoColor=white" height="18"/> **Chat History** — Persistent local storage with Room database
- <img src="https://img.shields.io/badge/-Models-8B5CF6?style=flat-square&logo=huggingface&logoColor=white" height="18"/> **Model Management** — Download, switch, and delete AI models
- <img src="https://img.shields.io/badge/-Dark_Mode-1E293B?style=flat-square&logo=darkreader&logoColor=white" height="18"/> **Dark Mode** — Beautiful Material3 light and dark themes
- <img src="https://img.shields.io/badge/-Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" height="18"/> **Modern UI** — Built with Jetpack Compose
- <img src="https://img.shields.io/badge/-Private-10B981?style=flat-square&logo=shieldsdotio&logoColor=white" height="18"/> **Privacy First** — No data collection, no servers, no tracking

---

## Screenshots

| Home | Chat | Models | Settings |
|------|------|--------|----------|
| ![Home](screenshots/home.jpeg) | ![Chat](screenshots/chat.jpeg) | ![Models](screenshots/models.jpeg) | ![Settings](screenshots/settings.jpeg) |

---

## Installation

### Requirements

- Android 8.0+ (API 26)
- ARM64 device (arm64-v8a)
- At least 4GB RAM recommended
- Storage space for AI models (500MB - 4GB per model)

### Download

<p align="center">
  <a href="https://github.com/Danyalkhattak/xirea/releases/latest/download/Xirea-v1.0.0.apk">
    <img src="https://img.shields.io/badge/Download_APK-v1.0.0-6366F1?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" height="50"/>
  </a>
</p>

Or browse all versions on the [Releases](https://github.com/Danyalkhattak/xirea/releases) page.

---

## Building

### Prerequisites

- Android Studio Hedgehog or newer
- Android NDK 29.0.14206865
- CMake 3.22.1
- JDK 17

### Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/Danyalkhattak/xirea.git
   cd xirea
   ```

2. **Open in Android Studio**
   - Open the project folder in Android Studio
   - Wait for Gradle sync to complete

3. **Build Debug APK**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Build Release APK** (requires signing keystore)
   ```bash
   ./gradlew assembleRelease
   ```

The APK will be generated at `app/build/outputs/apk/`

---

## Tech Stack

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose"/>
  <img src="https://img.shields.io/badge/llama.cpp-000000?style=for-the-badge&logo=cplusplus&logoColor=white" alt="llama.cpp"/>
  <img src="https://img.shields.io/badge/Room_DB-4285F4?style=for-the-badge&logo=sqlite&logoColor=white" alt="Room"/>
  <img src="https://img.shields.io/badge/Material3-6750A4?style=for-the-badge&logo=materialdesign&logoColor=white" alt="Material3"/>
  <img src="https://img.shields.io/badge/Coroutines-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Coroutines"/>
  <img src="https://img.shields.io/badge/MVVM-FF6F00?style=for-the-badge&logo=android&logoColor=white" alt="MVVM"/>
</p>

---

## Supported Models

Xirea works with GGUF format models. Recommended models for mobile:

| Model | Size | RAM Required |
|-------|------|--------------|
| Qwen2.5 0.5B Q4 | ~400MB | 4GB |
| Qwen2.5 1.5B Q4 | ~1GB | 6GB |
| Llama 3.2 1B Q4 | ~700MB | 4GB |
| Phi-3 Mini Q4 | ~2GB | 8GB |
| Gemma 2B Q4 | ~1.5GB | 6GB |

---

## Performance Optimization

Xirea automatically optimizes for your device:

| Device RAM | Context Size | Batch Size |
|------------|--------------|------------|
| 4GB | 512 | 128 |
| 6GB | 768 | 256 |
| 8GB | 1024 | 256 |
| 12GB+ | 2048 | 512 |

- **CPU-only inference** for maximum compatibility
- **Memory-mapped model loading** for reduced RAM usage
- **Pre-allocated batch buffers** for zero-allocation generation
- **Near-greedy sampling** for faster token generation

---

## Project Structure

```
xirea/
├── app/
│   ├── src/main/
│   │   ├── java/com/dannyk/xirea/
│   │   │   ├── ai/          # AI engine & llama.cpp wrapper
│   │   │   ├── data/        # Room database & repositories
│   │   │   ├── service/     # Download service
│   │   │   └── ui/          # Compose UI screens
│   │   ├── cpp/             # Native C++ code
│   │   │   ├── llama.cpp/   # llama.cpp library
│   │   │   └── llama_jni.cpp # JNI bridge
│   │   └── res/             # Resources
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml   # Version catalog
└── build.gradle.kts
```

---

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Author

**Danyal Khattak**

<p>
  <a href="https://github.com/Danyalkhattak"><img src="https://img.shields.io/badge/GitHub-Danyalkhattak-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub"/></a>
  <a href="https://instagram.com/dannyk_739"><img src="https://img.shields.io/badge/Instagram-dannyk__739-E4405F?style=for-the-badge&logo=instagram&logoColor=white" alt="Instagram"/></a>
</p>

---

## Acknowledgments

- [llama.cpp](https://github.com/ggerganov/llama.cpp) — Excellent C++ inference engine
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — Modern Android UI toolkit
- [Material3](https://m3.material.io/) — Design system

---

<p align="center">
  Made with <img src="https://img.shields.io/badge/-%E2%9D%A4-EF4444?style=flat-square&logoColor=white" height="16"/> by <b>Danyal Khattak</b>
</p>
