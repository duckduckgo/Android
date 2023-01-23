cmake_minimum_required(VERSION 3.4.1)
project("vpn-network")

find_package(netguard REQUIRED CONFIG)

# A bit ugly, but we need to copy the headers so a GO preprocess command can find them
# Since this is all temporary, leaving the copying as-is
get_property(NETGUARD_PATH TARGET netguard::netguard PROPERTY LOCATION)
message("NETGUARD_PATH=${NETGUARD_PATH}")
get_filename_component(NETGUARD_DIR ${NETGUARD_PATH} DIRECTORY CACHE)
file(COPY ${NETGUARD_DIR}/../../include/
        DESTINATION ${CMAKE_CURRENT_LIST_DIR}/build/netguard/
        )

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
        NETGUARD_PATH=${NETGUARD_PATH}
        )
