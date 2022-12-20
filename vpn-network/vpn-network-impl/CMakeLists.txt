cmake_minimum_required(VERSION 3.4.1)
project("vpn-network")

add_library( netguard
             SHARED
             src/main/cpp/netguard/src/netguard.c
             src/main/cpp/netguard/src/session.c
             src/main/cpp/netguard/src/ip.c
             src/main/cpp/netguard/src/tls.c
             src/main/cpp/netguard/src/tcp.c
             src/main/cpp/netguard/src/udp.c
             src/main/cpp/netguard/src/icmp.c
             src/main/cpp/netguard/src/dns.c
             src/main/cpp/netguard/src/dhcp.c
             src/main/cpp/netguard/src/pcap.c
             src/main/cpp/netguard/src/memory.c
             src/main/cpp/netguard/src/socks5.c
             src/main/cpp/netguard/src/util.c
             src/main/cpp/netguard/src/uid_mapping.c
             src/main/cpp/netguard/src/android.c
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

# Makefile requires this to be with "/" to link correctly
file(TO_CMAKE_PATH ${CMAKE_LIBRARY_OUTPUT_DIRECTORY} OUT_PATH_CMAKE)
add_custom_target(libwg-go.so WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/wireguard" COMMENT "Building wireguard-go" VERBATIM COMMAND make
        ANDROID_ARCH_NAME=${ANDROID_ARCH_NAME}
        ANDROID_C_COMPILER=${ANDROID_C_COMPILER}
        ANDROID_TOOLCHAIN_ROOT=${ANDROID_TOOLCHAIN_ROOT}
        ANDROID_LLVM_TRIPLE=${ANDROID_LLVM_TRIPLE}
        ANDROID_SYSROOT=${ANDROID_SYSROOT}
        ANDROID_PACKAGE_NAME=${ANDROID_PACKAGE_NAME}
        GRADLE_USER_HOME=${GRADLE_USER_HOME}
        CFLAGS=${CMAKE_C_FLAGS}\ -Wno-unused-command-line-argument
        LDFLAGS=${CMAKE_SHARED_LINKER_FLAGS}\ -fuse-ld=gold
        DESTDIR=${OUT_PATH_CMAKE}
        BUILDDIR=${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/../generated-src
        )

add_dependencies(libwg-go.so netguard)
