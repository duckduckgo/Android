#ifndef SESSION_H
#define SESSION_H

#include <jni.h> // TODO remove this
#include <stdint.h>
#include <sys/types.h>

#define EPOLL_MIN_CHECK 100 // milliseconds

struct tcp_session {
    jint uid;
    time_t time;
    int version;
    uint16_t mss;
    uint8_t recv_scale;
    uint8_t send_scale;
    uint32_t recv_window; // host notation, scaled
    uint32_t send_window; // host notation, scaled
    uint16_t unconfirmed; // packets

    uint32_t remote_seq; // confirmed bytes received, host notation
    uint32_t local_seq; // confirmed bytes sent, host notation
    uint32_t remote_start;
    uint32_t local_start;

    uint32_t acked; // host notation
    long long last_keep_alive;

    uint64_t sent;
    uint64_t received;

    union {
        __be32 ip4; // network notation
        struct in6_addr ip6;
    } saddr;
    __be16 source; // network notation

    union {
        __be32 ip4; // network notation
        struct in6_addr ip6;
    } daddr;
    __be16 dest; // network notation

    uint8_t state;
    uint8_t socks5;
    struct segment *forward;
};

struct udp_session {
    time_t time;
    jint uid;
    int version;
    uint16_t mss;

    uint64_t sent;
    uint64_t received;

    union {
        __be32 ip4; // network notation
        struct in6_addr ip6;
    } saddr;
    __be16 source; // network notation

    union {
        __be32 ip4; // network notation
        struct in6_addr ip6;
    } daddr;
    __be16 dest; // network notation

    uint8_t state;
};

struct icmp_session {
    time_t time;
    jint uid;
    int version;

    union {
        __be32 ip4; // network notation
        struct in6_addr ip6;
    } saddr;

    union {
        __be32 ip4; // network notation
        struct in6_addr ip6;
    } daddr;

    uint16_t id;

    uint8_t stop;
};

struct ng_session {
    uint8_t protocol;
    union {
        struct icmp_session icmp;
        struct udp_session udp;
        struct tcp_session tcp;
    };
    jint socket;
    struct epoll_event ev;
    struct ng_session *next;
};

#endif // SESSION_H