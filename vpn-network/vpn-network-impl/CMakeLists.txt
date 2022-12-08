cmake_minimum_required(VERSION 3.4.1)
project("vpn-network")

add_library( netguard
             SHARED
             src/main/cpp/netguard/src/netguard.cpp
             src/main/cpp/netguard/src/session.cpp
             src/main/cpp/netguard/src/ip.cpp
             src/main/cpp/netguard/src/tls.cpp
             src/main/cpp/netguard/src/tcp.cpp
             src/main/cpp/netguard/src/udp.cpp
             src/main/cpp/netguard/src/icmp.cpp
             src/main/cpp/netguard/src/dns.cpp
             src/main/cpp/netguard/src/dhcp.cpp
             src/main/cpp/netguard/src/pcap.cpp
             src/main/cpp/netguard/src/memory.cpp
             src/main/cpp/netguard/src/socks5.cpp
             src/main/cpp/netguard/src/util.cpp
             src/main/cpp/netguard/src/android.cpp
             )

include_directories( src/main/cpp/netguard/src/ )

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

target_link_libraries( # Specifies the target library.
        netguard

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})

include(FetchContent)
FetchContent_Declare(
        googletest
        URL https://github.com/google/googletest/archive/609281088cfefc76f9d0ce82e1ff6c30cc3591e5.zip
)

# For Windows: Prevent overriding the parent project's compiler/linker settings
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)
enable_testing()

add_executable(
        test_tcp
        src/main/cpp/netguard/unit_tests/test_util.cpp
)

target_include_directories(test_tcp PRIVATE src/main/cpp/netguard/src/)

target_link_libraries(
        test_tcp
        gtest_main
        netguard
)

include(GoogleTest)