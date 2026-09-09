cmake_minimum_required(VERSION 3.22.1)

project("hreader_voxcpm" LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "VoxCPM2 requires the arm64-v8a Android ABI")
endif()

set(GGML_SYSTEM_ARCH ARM CACHE STRING "" FORCE)
set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
set(GGML_OPENMP ON CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

set(LLAMA_SRC "${CMAKE_CURRENT_LIST_DIR}/../../../../external/llama.cpp-omni")
get_filename_component(LLAMA_SRC "${LLAMA_SRC}" ABSOLUTE)
if(NOT EXISTS "${LLAMA_SRC}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp-omni submodule is not initialized at ${LLAMA_SRC}")
endif()

add_subdirectory("${LLAMA_SRC}" "${CMAKE_BINARY_DIR}/build-llama")

add_library(voxcpm2_llm STATIC
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_llm.cpp"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_llm.h"
)
target_link_libraries(voxcpm2_llm PUBLIC llama llama-common)
target_include_directories(voxcpm2_llm PUBLIC
    "${LLAMA_SRC}/tools/omni"
    "${LLAMA_SRC}/tools/omni/voxcpm2"
)
target_include_directories(voxcpm2_llm PRIVATE "${LLAMA_SRC}")
target_compile_features(voxcpm2_llm PRIVATE cxx_std_17)

add_library(voxcpm2_fsq STATIC
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_fsq.cpp"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_fsq.h"
)
target_link_libraries(voxcpm2_fsq PUBLIC ggml llama-common)
target_include_directories(voxcpm2_fsq PUBLIC
    "${LLAMA_SRC}/tools/omni"
    "${LLAMA_SRC}/tools/omni/voxcpm2"
)
target_include_directories(voxcpm2_fsq PRIVATE "${LLAMA_SRC}")
target_compile_features(voxcpm2_fsq PRIVATE cxx_std_17)

add_library(voxcpm2_acoustic STATIC
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_transformer.cpp"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_transformer.h"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_localenc.cpp"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_localenc.h"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_locdit.cpp"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_locdit.h"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_cfm.cpp"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_cfm.h"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_components.cpp"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_components.h"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_audiovae.cpp"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_audiovae.h"
)
target_link_libraries(voxcpm2_acoustic PUBLIC ggml llama-common)
target_include_directories(voxcpm2_acoustic PUBLIC
    "${LLAMA_SRC}/tools/omni"
    "${LLAMA_SRC}/tools/omni/voxcpm2"
)
target_include_directories(voxcpm2_acoustic PRIVATE "${LLAMA_SRC}")
target_compile_features(voxcpm2_acoustic PRIVATE cxx_std_17)

add_library(voxcpm2_runtime STATIC
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_runtime.cpp"
    "${LLAMA_SRC}/tools/omni/voxcpm2/voxcpm2_runtime.h"
)
target_link_libraries(voxcpm2_runtime PUBLIC
    voxcpm2_llm
    voxcpm2_fsq
    voxcpm2_acoustic
    llama
    ggml
    llama-common
)
target_include_directories(voxcpm2_runtime PUBLIC
    "${LLAMA_SRC}/tools/omni"
    "${LLAMA_SRC}/tools/omni/voxcpm2"
)
target_include_directories(voxcpm2_runtime PRIVATE "${LLAMA_SRC}")
target_compile_features(voxcpm2_runtime PRIVATE cxx_std_17)

add_library(${CMAKE_PROJECT_NAME} SHARED voxcpm_jni.cpp)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
    GGML_SYSTEM_ARCH=ARM
    GGML_CPU_KLEIDIAI=1
    GGML_OPENMP=1
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    "${LLAMA_SRC}"
    "${LLAMA_SRC}/common"
    "${LLAMA_SRC}/include"
    "${LLAMA_SRC}/ggml/include"
    "${LLAMA_SRC}/ggml/src"
    "${LLAMA_SRC}/tools/omni"
    "${LLAMA_SRC}/tools/omni/voxcpm2"
    "${LLAMA_SRC}/vendor"
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    llama-common
    voxcpm2_runtime
    android
    log
)
