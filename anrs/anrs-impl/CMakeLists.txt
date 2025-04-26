project(crash-ndk)
cmake_minimum_required(VERSION 3.4.1)

add_library( # Sets the name of the library.
        crash-ndk

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        src/main/cpp/ndk-crash.cpp
        src/main/cpp/jni.cpp
        src/main/cpp/pixel.cpp
        )

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

target_link_libraries(
        # Specifies the target library.
       crash-ndk

       # Links the target library to the log library
       # included in the NDK.
       ${log-lib} )

target_link_options(
       crash-ndk
       PRIVATE "-Wl,-z,max-page-size=16384")