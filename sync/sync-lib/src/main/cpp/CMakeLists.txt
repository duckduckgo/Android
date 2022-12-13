cmake_minimum_required(VERSION 3.4.1)
project(libsodium C)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c11 -Wall")

macro(add_sodium_arch _KEY _VALUE)
    set("SODIUM_ARCH_${_KEY}" "${_VALUE}")
endmacro(add_sodium_arch)

add_sodium_arch("arm64-v8a" "armv8-a+crypto")
add_sodium_arch("x86" "i686")
add_sodium_arch("x86_64" "westmere")
set(SODIUM_ARCH "${SODIUM_ARCH_${ANDROID_ABI}}")

add_library(sodium STATIC IMPORTED)
set_property(TARGET sodium PROPERTY IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/third-party/libsodium-android-${SODIUM_ARCH}/lib/libsodium.a")

add_library(sodium-jni SHARED sodium-jni.c)
target_include_directories(sodium-jni PRIVATE ${CMAKE_SOURCE_DIR}/third-party/libsodium-android-${SODIUM_ARCH}/include)
target_link_libraries(sodium-jni sodium)