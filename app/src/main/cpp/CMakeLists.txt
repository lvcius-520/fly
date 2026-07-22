cmake_minimum_required(VERSION 3.22.1)

project(yolo_ncnn_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(NCNN_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/ncnn-android-shared/ncnn-20260113-android-shared/${ANDROID_ABI}/include")
set(NCNN_SHARED_LIB "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libncnn.so")

add_library(ncnn SHARED IMPORTED)
set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION "${NCNN_SHARED_LIB}")

add_library(yolo_ncnn_jni SHARED yolo_ncnn_jni.cpp)

target_include_directories(yolo_ncnn_jni PRIVATE "${NCNN_INCLUDE_DIR}")

find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(
        yolo_ncnn_jni
        ncnn
        ${log-lib}
        ${android-lib}
        ${jnigraphics-lib}
)
