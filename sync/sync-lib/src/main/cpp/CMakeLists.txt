cmake_minimum_required(VERSION 3.4.1)
project("ddgcrypto")

add_library( # Sets the name of the library.
        ddgcrypto

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        sodium-jni.c
        )
find_package(sodium REQUIRED CONFIG)
target_link_libraries(ddgcrypto sodium:sodium)