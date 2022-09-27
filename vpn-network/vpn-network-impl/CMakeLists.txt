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