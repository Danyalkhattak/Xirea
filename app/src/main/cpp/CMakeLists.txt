cmake_minimum_required(VERSION 3.22.1)

project("xirea")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android NDK paths
if(ANDROID)
    include_directories(${ANDROID_NDK}/toolchains/llvm/prebuilt/${ANDROID_HOST_TAG}/sysroot/usr/include)
endif()

# Set llama.cpp source directory
set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

# Build llama.cpp as a static library
set(BUILD_SHARED_LIBS OFF)
set(LLAMA_STATIC ON)
set(LLAMA_NATIVE OFF)
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_SERVER OFF)
set(GGML_OPENMP OFF)

# Add llama.cpp subdirectory
add_subdirectory(${LLAMA_DIR} ${CMAKE_BINARY_DIR}/llama.cpp)

# Create our JNI library
add_library(${CMAKE_PROJECT_NAME} SHARED
    llama_jni.cpp
)

# Include directories
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/ggml/include
)

# Link libraries
target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    ggml
    android
    log
)

# Compiler flags for optimization
target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
    -O3
    -ffast-math
    -fno-finite-math-only
)
