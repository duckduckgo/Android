#ifndef NETGUARD_H
#define NETGUARD_H

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <time.h>
#include <unistd.h>
#include <pthread.h>
#include <setjmp.h>
#include <errno.h>
#include <fcntl.h>
#include <dirent.h>
#include <poll.h>
#include <sys/types.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/epoll.h>
#include <dlfcn.h>
#include <sys/stat.h>
#include <sys/resource.h>

#include <netdb.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/in6.h>
#include <netinet/ip.h>
#include <netinet/ip6.h>
#include <netinet/udp.h>
#include <netinet/tcp.h>
#include <netinet/ip_icmp.h>
#include <netinet/icmp6.h>

#include <android/log.h>
#include <sys/system_properties.h>

#include "global.h"
#include "memory.h"
#include "util.h"
#include "platform.h"
#include "icmp.h"
#include "udp.h"
#include "session.h"
#include "pcap.h"

// #define PROFILE_JNI 5
// #define PROFILE_MEMORY

struct context {
    pthread_mutex_t lock;
    int pipefds[2];
    int stopping;
    int sdk;
    struct ng_session *ng_session;
};

struct arguments {
    JNIEnv *env;
    jobject instance;
    int tun;
    jboolean fwd53;
    jint rcode;
    struct context *ctx;
};

typedef struct packet {
    uint64_t time;
    uint32_t version;
    uint32_t protocol;
    const char *flags;
    const char *source;
    uint32_t sport;
    const char *dest;
    uint32_t dport;
    const char *data;
    uint32_t uid;
    uint8_t allowed;
} packet_t;

struct allowed {
    char raddr[INET6_ADDRSTRLEN + 1];
    uint16_t rport; // host notation
};

// IPv6

struct ip6_hdr_pseudo {
    struct in6_addr ip6ph_src;
    struct in6_addr ip6ph_dst;
    u_int32_t ip6ph_len;
    uint8_t ip6ph_zero[3];
    uint8_t ip6ph_nxt;
} __packed;

// Prototypes

void handle_signal(int sig, siginfo_t *info, void *context);

void *handle_events(void *a);

void report_exit(const struct arguments *args, const char *fmt, ...);

void report_error(const struct arguments *args, jint error, const char *fmt, ...);

void clear(struct context *ctx);

int check_icmp_session(const struct arguments *args,
                       struct ng_session *s,
                       int sessions, int maxsessions);

int check_udp_session(const struct arguments *args,
                      struct ng_session *s,
                      int sessions, int maxsessions);

int check_tcp_session(const struct arguments *args,
                      struct ng_session *s,
                      int sessions, int maxsessions);

int monitor_tcp_session(const struct arguments *args, struct ng_session *s, int epoll_fd);

int get_icmp_timeout(const struct icmp_session *u, int sessions, int maxsessions);

int get_udp_timeout(const struct udp_session *u, int sessions, int maxsessions);

int get_tcp_timeout(const struct tcp_session *t, int sessions, int maxsessions);

uint16_t get_mtu();

uint16_t get_default_mss(int version);

int check_tun(const struct arguments *args,
              const struct epoll_event *ev,
              const int epoll_fd,
              int sessions, int maxsessions);

void check_icmp_socket(const struct arguments *args, const struct epoll_event *ev);

void check_udp_socket(const struct arguments *args, const struct epoll_event *ev);


/**
* @brief Parses the DNS response and returns "1" if marked as blocked or "0" if not.
*/
int parse_dns_response(const struct arguments *args, const struct ng_session *session,
                        const uint8_t *data, size_t *datalen);

void check_tcp_socket(const struct arguments *args,
                      const struct epoll_event *ev,
                      const int epoll_fd);

void sni_resolved(const struct arguments *args, const char *name, const char *daddr);

void tls_sni_inspection(const struct arguments *args,
                        const uint8_t *pkt,
                        size_t length,
                        void *daddr,
                        uint8_t version,
                        uint8_t *tcp_payload
);

jboolean handle_icmp(const struct arguments *args,
                     const uint8_t *pkt, size_t length,
                     const uint8_t *payload,
                     int uid,
                     const int epoll_fd);

int has_udp_session(const struct arguments *args, const uint8_t *pkt, const uint8_t *payload);

void block_udp(const struct arguments *args,
               const uint8_t *pkt, size_t length,
               const uint8_t *payload,
               int uid);

jboolean handle_udp(const struct arguments *args,
                    const uint8_t *pkt, size_t length,
                    const uint8_t *payload,
                    int uid, struct allowed *redirect,
                    const int epoll_fd);

int check_dhcp(const struct arguments *args, const struct udp_session *u,
               const uint8_t *data, const size_t datalen);

void clear_tcp_data(struct tcp_session *cur);

jboolean handle_tcp(const struct arguments *args,
                    const uint8_t *pkt, size_t length,
                    const uint8_t *payload,
                    int uid, int allowed, struct allowed *redirect,
                    const int epoll_fd);

void write_rst(const struct arguments *args, struct tcp_session *cur);

ssize_t write_icmp(const struct arguments *args, const struct icmp_session *cur,
                   uint8_t *data, size_t datalen);

ssize_t write_udp(const struct arguments *args, const struct udp_session *cur,
                  uint8_t *data, size_t datalen);

jint get_uid(const int version, const int protocol,
             const void *saddr, const uint16_t sport,
             const void *daddr, const uint16_t dport);

jint get_uid_sub(const int version, const int protocol,
                 const void *saddr, const uint16_t sport,
                 const void *daddr, const uint16_t dport,
                 const char *source, const char *dest,
                 long now);

int protect_socket(const struct arguments *args, int socket);

jobject jniGlobalRef(JNIEnv *env, jobject cls);

jclass jniFindClass(JNIEnv *env, const char *name);

jmethodID jniGetMethodID(JNIEnv *env, jclass cls, const char *name, const char *signature);

jfieldID jniGetFieldID(JNIEnv *env, jclass cls, const char *name, const char *type);

jobject jniNewObject(JNIEnv *env, jclass cls, jmethodID constructor, const char *name);

int jniCheckException(JNIEnv *env);

void log_packet(const struct arguments *args, const packet_t *packet);

void dns_resolved(const struct arguments *args,
                  const char *qname, const char *aname, const char *resource, int ttl);

jboolean is_domain_blocked(const struct arguments *args, const char *name, jint uid);

jint get_uid_q(const struct arguments *args,
               jint version,
               jint protocol,
               const char *source,
               jint sport,
               const char *dest,
               jint dport);

struct allowed *is_address_allowed(const struct arguments *args, const packet_t *packet);

void account_usage(const struct arguments *args, uint32_t version, uint32_t protocol,
                   const char *daddr, uint32_t dport, uint32_t uid, uint64_t sent, uint64_t received);

#endif // NETGUARD_H
