# Native Build Troubleshooting Checklist

## IntelliSense Errors (C++ Extension)

### Problem
VS Code shows "cannot open source file" errors for `jni.h`, `android/log.h`, etc.

### Solution
This is an IntelliSense issue, not a build issue. The actual build will work fine.

**Fix:**
1. Install **C/C++ Extension Pack** by Microsoft (if not already installed)
2. Close VS Code completely
3. Reopen the workspace
4. Press Ctrl+Shift+P → "C/C++: Edit Configurations (JSON)"
5. Verify `.vscode/c_cpp_properties.json` has proper includePath
6. Press Ctrl+Shift+P → "Developer: Reload Window"

**Verify fix:**
- Errors should disappear after reload
- Build should still work in Android Studio

---

## NDK Issues

### Problem
"CMake Error: Android NDK not found" or similar

### Solution
1. Open Android Studio → Tools → SDK Manager
2. Go to SDK Tools tab
3. Check "NDK (Side by side)"
4. Install version 26.1.10909125
5. Click "Apply" and wait for download to complete
6. Sync Gradle in Android Studio: File → Sync Now
7. Clean project: Build → Clean Project
8. Rebuild: Build → Make Project

---

## CMake Errors

### Problem
"CMake version not supported" or CMake not found

### Solution
1. Android Studio → Tools → SDK Manager
2. SDK Tools tab → Find "CMake"
3. Install version 3.22.1+
4. In Android Studio: Build → Clean Project
5. Rebuild: Build → Make Project

---

## "llama.cpp not found" or "llama.cpp is empty"

### Problem
Build fails saying llama.cpp directory is missing or empty

### Solution
1. Navigate to `app/src/main/cpp/` in PowerShell
2. Run: `git clone https://github.com/ggerganov/llama.cpp.git`
3. Wait for clone to complete
4. Return to project root
5. In Android Studio: Build → Clean Project
6. Rebuild: Build → Make Project

### Verify
Check that these files exist:
- `app/src/main/cpp/llama.cpp/include/llama.h`
- `app/src/main/cpp/llama.cpp/ggml/include/ggml.h`
- `app/src/main/cpp/llama.cpp/CMakeLists.txt`

---

## "common library not found" Error

### Problem
```
error: target 'common' referenced as dependency of target 'xirea' does not exist
```

### Solution (Temporary)
This happens with older llama.cpp versions. Either:

**Option A - Update llama.cpp (Recommended)**
```powershell
cd app/src/main/cpp/llama.cpp
git pull origin master
cd ../../../..
```

**Option B - Remove common from CMakeLists.txt**
Edit `app/src/main/cpp/CMakeLists.txt` and change:
```cmake
target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    ggml
    # common      # <- Comment this out
    android
    log
)
```

---

## Build Takes Very Long Time

### Cause
First-time native build compiles llama.cpp from source (~5-10 minutes normal)

### Optimization
- Use Release build type if available
- Build only for one ABI at a time during development:
  - Edit `app/build.gradle.kts`:
    ```kotlin
    ndk {
        abiFilters += "arm64-v8a"  // Only build for current device
    }
    ```

---

## "JNI.h not found" IntelliSense Only

### Problem
VS Code shows jni.h error, but build succeeds

### Cause
Android SDK/NDK headers not in IntelliSense path

### Solution (Already Done)
- `.vscode/c_cpp_properties.json` is pre-configured
- If still seeing errors: Reload VS Code

### Manual Fix if Needed
1. Find your NDK location: `echo $env:ANDROID_NDK_HOME` (PowerShell)
2. Edit `.vscode/c_cpp_properties.json`:
   ```json
   "includePath": [
       "${workspaceFolder}/app/src/main/cpp",
       "${workspaceFolder}/app/src/main/cpp/llama.cpp/include",
       "C:/Users/YourName/AppData/Local/Android/Sdk/ndk/26.1.10909125/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/include"
   ]
   ```

---

## "Cannot find llama_jni.cpp"

### Problem
CMake says llama_jni.cpp not found

### Cause
CMake path is wrong

### Solution
1. Verify `llama_jni.cpp` exists at: `app/src/main/cpp/llama_jni.cpp`
2. In Android Studio: Build → Clean Project
3. Rebuild: Build → Make Project

---

## Build Succeeds but App Crashes on Model Load

### Problem
App crashes when loading a model with "undefined reference" errors

### Cause
Missing native library linking

### Solution
1. Verify native build completed: 
   - Check for `.so` files in `app/build/intermediates/library_assets/debug/out/lib/`
   - Should see files like `libxirea.so`
2. Clean and rebuild:
   ```
   Build → Clean Project
   Build → Make Project
   ```

---

## Testing the Build

### Verify Native Library Built Successfully

1. After successful build, check:
   ```
   app/build/intermediates/library_assets/debug/out/lib/
   ```
   Should contain:
   - `arm64-v8a/libxirea.so`
   - `armeabi-v7a/libxirea.so`
   - `x86_64/libxirea.so`

2. Check Logcat for native initialization:
   ```
   Filter: "LlamaJNI"
   Expected: "I/LlamaJNI: Llama backend initialized"
   ```

### Test App

1. Build and run on device/emulator: `./gradlew installDebug`
2. Open app → Models screen
3. Download a small model (Qwen2 0.5B recommended for testing)
4. Go to chat and send a message
5. Check Logcat for success:
   ```
   I/LlamaJNI: Model loaded successfully
   ```

---

## Getting Help

### Check These First
1. Look at Android Studio "Build Output" tab for exact error
2. Check Logcat for runtime errors
3. Verify all prerequisites are installed

### Clean Build Process
```powershell
# Full clean
./gradlew clean
./gradlew build --refresh-dependencies

# Or from Android Studio
Build → Clean Project
Build → Make Project
```

### Still Stuck?
- Check llama.cpp GitHub issues: https://github.com/ggerganov/llama.cpp/issues
- Look at your specific error in build output
- Try building with `./gradlew assembleDebug -v` for verbose output

---

## Quick Commands Reference

```powershell
# Clean everything
./gradlew clean

# Build APK
./gradlew assembleDebug

# Install to device
./gradlew installDebug

# Build and run
./gradlew installDebug && adb shell am start -n com.dannyk.xirea/.MainActivity

# View native build output
./gradlew assembleDebug -v

# Full clean & build
./gradlew clean build
```
