cmake_minimum_required(VERSION 3.22.1)
project(pockettts_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
get_filename_component(POCKET_REPO_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)

# Do not leak developer workstation paths through __FILE__ strings or native
# debug metadata in distributable APKs.
add_compile_options(
    "-ffile-prefix-map=${POCKET_REPO_ROOT}=."
    "-fdebug-prefix-map=${POCKET_REPO_ROOT}=.")
if(DEFINED ANDROID_NDK)
    add_compile_options(
        "-ffile-prefix-map=${ANDROID_NDK}=<android-ndk>"
        "-fdebug-prefix-map=${ANDROID_NDK}=<android-ndk>")
endif()

set(POCKET_CPP "${CMAKE_CURRENT_LIST_DIR}/../../../../vendor/PocketTTS.cpp/pocket_tts.cpp")
set(ORT_LIB "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libonnxruntime.so")
set(ORT_HEADERS "${CMAKE_CURRENT_LIST_DIR}/../third_party/onnxruntime/include")

if(NOT EXISTS "${ORT_LIB}" OR NOT EXISTS "${ORT_HEADERS}/onnxruntime/core/session/onnxruntime_cxx_api.h")
    message(FATAL_ERROR "Native ONNX Runtime is missing. Run scripts/prepare_android_native_deps.sh first.")
endif()

include(FetchContent)
set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)
set(SPM_PROTOBUF_PROVIDER "internal" CACHE STRING "" FORCE)
set(SPM_ABSL_PROVIDER "internal" CACHE STRING "" FORCE)
set(SPM_ENABLE_NFKC_COMPILE OFF CACHE BOOL "" FORCE)
FetchContent_Declare(sentencepiece GIT_REPOSITORY https://github.com/google/sentencepiece.git GIT_TAG v0.2.1 GIT_SHALLOW TRUE)
FetchContent_Declare(dr_libs GIT_REPOSITORY https://github.com/mackron/dr_libs.git GIT_TAG 50bb723e6a459dbb781e26cefee4fd9ca6714d6a)
FetchContent_MakeAvailable(sentencepiece dr_libs)
target_compile_options(sentencepiece-static PRIVATE -w)
# SentencePiece's command-line helper targets are built by its upstream CMake
# file even though the app only uses the static library. On Android its
# protobuf logger requires liblog, so link it for those helpers as well.
foreach(spm_tool spm_decode spm_encode spm_export_vocab spm_normalize spm_train)
    if(TARGET ${spm_tool})
        target_link_libraries(${spm_tool} log)
    endif()
endforeach()

add_library(pockettts_jni SHARED "${POCKET_CPP}" jni_bridge.cpp)
target_compile_definitions(pockettts_jni PRIVATE PTT_SHARED_LIB)
target_include_directories(pockettts_jni PRIVATE
    "${ORT_HEADERS}"
    "${ORT_HEADERS}/onnxruntime/core/session"
    "${sentencepiece_SOURCE_DIR}/src"
    "${dr_libs_SOURCE_DIR}")
target_link_libraries(pockettts_jni PRIVATE "${ORT_LIB}" sentencepiece-static log android)
