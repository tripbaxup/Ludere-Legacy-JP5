cmake_minimum_required(VERSION 3.10)
project(ludere_jni C)

# Build the JNI bridge shared library
add_library(
    ludere_jni
    SHARED
    ludere_jni.c
)

# Link against Android system libraries
find_library(log-lib  log)
find_library(dl-lib   dl)
find_library(android-lib android)

target_link_libraries(
    ludere_jni
    ${log-lib}
    ${dl-lib}
    ${android-lib}
)

# Include the libretro header
target_include_directories(ludere_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
