# Xirea - llama.cpp Setup Guide

This guide explains how to set up llama.cpp for the Xirea Android app.

## Prerequisites

1. **Android Studio** (latest version)
2. **NDK 26.1.10909125** installed via SDK Manager
3. **CMake 3.22.1+** installed via SDK Manager
4. **Git** installed on your system
5. **VS Code** (optional, for native code editing)

## Quick Start

### 1. Clone llama.cpp

Open PowerShell in the project root and run:

```powershell
cd app/src/main/cpp
git clone https://github.com/ggerganov/llama.cpp.git
cd ../../../..
```

### 2. Set Up VS Code (Optional)

If using VS Code for C++ development:

1. Install the **C/C++ Extension Pack** by Microsoft
2. Open the workspace folder in VS Code
3. The `.vscode/c_cpp_properties.json` file is already configured for you
4. IntelliSense should now recognize the NDK headers

### 3. Build in Android Studio

1. Open the project in Android Studio
2. File → Sync with Gradle Files
3. Build → Make Project

The native library will compile for:
- `arm64-v8a` (64-bit ARM - most modern devices)
- `armeabi-v7a` (32-bit ARM - older devices)
- `x86_64` (Intel emulator)

## Verify Directory Structure

After cloning, your structure should be:

```
app/src/main/cpp/
├── CMakeLists.txt
├── llama_jni.cpp
├── .clang-format
└── llama.cpp/
    ├── CMakeLists.txt
    ├── include/
    │   ├── llama.h
    │   └── llama-cpp-completion.h
    ├── ggml/
    │   ├── include/
    │   │   ├── ggml.h
    │   │   ├── ggml-alloc.h
    │   │   └── ggml-cuda.h
    ├── common/
    │   ├── common.h
    │   └── log.h
    └── src/
        ├── llama.cpp
        └── ggml.c
```

## Build Configuration

The build is configured in `app/build.gradle.kts`:

```kotlin
ndkVersion = "26.1.10909125"
externalNativeBuild {
    cmake {
        cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti", "-O3")
        arguments += listOf(
            "-DANDROID_STL=c++_shared",
            "-DLLAMA_NATIVE=OFF",
            "-DLLAMA_BUILD_TESTS=OFF"
        )
    }
}
```

## Troubleshooting

### IntelliSense Errors in VS Code

If you see "cannot open source file" errors for `jni.h` or `android/log.h`:

1. Install **C/C++ Extension Pack** by Microsoft
2. The `.vscode/c_cpp_properties.json` is already configured
3. Reload VS Code: Ctrl+Shift+P → Developer: Reload Window

### NDK Not Found

1. Open Android Studio → Tools → SDK Manager
2. Switch to "SDK Tools" tab
3. Check "NDK (Side by side)"
4. Install version 26.1.10909125

### CMake Errors

1. Ensure CMake 3.22.1+ is installed: Tools → SDK Manager → SDK Tools
2. Clean the project: Build → Clean Project
3. Rebuild: Build → Make Project

### Build Fails with "common library not found"

If the build fails because `libcommon` cannot be found:

1. Update llama.cpp: 
   ```powershell
   cd app/src/main/cpp/llama.cpp
   git pull origin master
   cd ../../../..
   ```

2. The issue is that older versions of llama.cpp don't have the common target. If it persists, modify `CMakeLists.txt`:

```cmake
# Link libraries (remove if common library not available)
target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    ggml
    # common      # <-- Comment this out if build fails
    android
    log
)
```

## Testing the Build

### 1. Run the App

```powershell
# Build and install to device/emulator
./gradlew installDebug
```

Or use Android Studio: Run → Run 'app'

### 2. Test in the App

1. Open the app
2. Go to Models screen
3. Download a model (e.g., Qwen2 0.5B for fastest testing)
4. Load the model
5. Create a new chat and send a message
6. Verify the AI responds

### 3. Monitor Build Output

In Android Studio, open Logcat (View → Tool Windows → Logcat) and filter for:
```
LlamaJNI
```

You should see:
```
I/LlamaJNI: Llama backend initialized
I/LlamaJNI: Loading model from: /data/data/com.dannyk.xirea/files/models/...
I/LlamaJNI: Model loaded successfully
```

## Performance Optimization

### Thread Configuration

The app automatically uses:
- **Thread count**: Half of available CPU cores (2-8 threads)
- **Context size**: 2048 tokens
- **Max generation**: 512 tokens

### Model Selection for Different Devices

| Device | RAM | Recommended Model | Size |
|--------|-----|-------------------|------|
| Budget (2GB RAM) | 2GB | Qwen2 0.5B | 400 MB |
| Mid-range (4GB RAM) | 4GB | TinyLlama 1.1B | 669 MB |
| High-end (8GB+ RAM) | 8GB+ | Phi-2 2.7B | 1.6 GB |

### Battery & Thermal

- Inference is **CPU-intensive** - expect ~20-30% battery drain per hour
- Device may **thermal throttle** on extended use
- Recommend 5-10 minute usage windows for best performance

## Model Files

The app supports GGUF format models. Pre-configured models are:

1. **Qwen2 0.5B** - Ultra-lightweight, fastest (400 MB)
2. **TinyLlama 1.1B** - Good balance (669 MB)
3. **Phi-2 2.7B** - Better reasoning (1.6 GB)
4. **Gemma 2B** - Google's optimized (1.5 GB)

All download from Hugging Face automatically.

## Advanced Configuration

### Adding Custom Models

Edit `ModelRepository.kt`:

```kotlin
AIModel(
    id = "custom-model",
    name = "My Custom Model",
    description = "Custom description",
    fileName = "custom-model.gguf",
    downloadUrl = "https://your-url/model.gguf",
    fileSize = 1_000_000_000L, // 1GB
    version = "1.0"
)
```

### Adjusting Context Size

In `AIEngine.kt`:

```kotlin
private val contextSize = 2048  // Change this value
```

## API Reference

### LlamaCpp.kt

```kotlin
// Load model from file
loadModel(modelPath: String, nCtx: Int, nThreads: Int): Boolean

// Generate text with streaming
generate(prompt: String, maxTokens: Int, callback: TokenCallback): String

// Get model info
getModelInfo(): String

// Check status
isModelLoaded(): Boolean
isGenerating(): Boolean

// Control
stopGeneration()
unloadModel()
```

### AIEngine.kt

```kotlin
// High-level API
suspend fun loadModel(model: AIModel, modelFile: File): Result<Unit>
fun generateResponse(prompt: String, chatHistory: List<Pair<String, Boolean>>): Flow<String>
```

## Debugging

### Enable Verbose Logging

In `llama_jni.cpp`, add:

```cpp
LOGI("Detailed debug message");
LOGE("Error message");
```

View in Android Studio Logcat.

### Common Issues

1. **Model won't load**: Check file permissions in `/files/models/`
2. **Slow responses**: Verify thread count with `getOptimalThreadCount()`
3. **Out of memory**: Reduce context size or use smaller model
4. **Bad responses**: Try different temperature/sampling parameters

## Contributing

To improve the implementation:

1. Update llama.cpp to latest version
2. Test with different models
3. Report issues on GitHub

## Resources

- [llama.cpp GitHub](https://github.com/ggerganov/llama.cpp)
- [Hugging Face Models](https://huggingface.co/models?library=ggml)
- [GGUF Format](https://huggingface.co/docs/hub/gguf)
- [Android NDK Docs](https://developer.android.com/ndk)

