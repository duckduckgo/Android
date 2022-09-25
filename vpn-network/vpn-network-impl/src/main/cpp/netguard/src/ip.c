/*
    This file is part of NetGuard.

    NetGuard is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NetGuard is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NetGuard.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2015-2019 by Marcel Bokhorst (M66B)
*/

#include "netguard.h"

///////////////////////////////////////////////////////////////////////////////
// Definitions
///////////////////////////////////////////////////////////////////////////////

#define UID_MAX_AGE 30000 // milliseconds

struct uid_cache_entry {
    uint8_t version;
    uint8_t protocol;
    uint8_t saddr[16];
    uint16_t sport;
    uint8_t daddr[16];
    uint16_t dport;
    jint uid;
    long time;
};

static int is_lower_layer(int protocol);

static int is_upper_layer(int protocol);

static void handle_ip(const struct arguments *args,
               const uint8_t *buffer, size_t length,
               const int epoll_fd,
               int sessions, int maxsessions);

///////////////////////////////////////////////////////////////////////////////

int max_tun_msg = 0;

uint16_t get_mtu() {
    return 10000;
}

uint16_t get_default_mss(int version) {
    if (version == 4)
        return (uint16_t) (get_mtu() - sizeof(struct iphdr) - sizeof(struct tcphdr));
    else
        return (uint16_t) (get_mtu() - sizeof(struct ip6_hdr) - sizeof(struct tcphdr));
}

int check_tun(const struct arguments *args,
              const struct epoll_event *ev,
              const int epoll_fd,
              int sessions, int maxsessions) {
    // Check tun error
    if (ev->events & EPOLLERR) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "tun %d exception", args->tun);
        if (fcntl(args->tun, F_GETFL) < 0) {
            log_print(PLATFORM_LOG_PRIORITY_ERROR, "fcntl tun %d F_GETFL error %d: %s",
                        args->tun, errno, strerror(errno));
            report_exit(args, "fcntl tun %d F_GETFL error %d: %s",
                        args->tun, errno, strerror(errno));
        } else
            report_exit(args, "tun %d exception", args->tun);
        return -1;
    }

    // Check tun read
    if (ev->events & EPOLLIN) {
        uint8_t *buffer = ng_malloc(get_mtu(), "tun read");
        ssize_t length = read(args->tun, buffer, get_mtu());
        if (length < 0) {
            ng_free(buffer, __FILE__, __LINE__);

            log_print(PLATFORM_LOG_PRIORITY_ERROR, "tun %d read error %d: %s",
                        args->tun, errno, strerror(errno));
            if (errno == EINTR || errno == EAGAIN)
                // Retry later
                return 0;
            else {
                report_exit(args, "tun %d read error %d: %s",
                            args->tun, errno, strerror(errno));
                return -1;
            }
        } else if (length > 0) {
            // Write pcap record
            if (pcap_file != NULL)
                write_pcap_rec(buffer, (size_t) length);

            if (length > max_tun_msg) {
                max_tun_msg = length;
                log_print(PLATFORM_LOG_PRIORITY_WARN, "Maximum tun msg length %d", max_tun_msg);
            }

            // Handle IP from tun
            handle_ip(args, buffer, (size_t) length, epoll_fd, sessions, maxsessions);

            ng_free(buffer, __FILE__, __LINE__);
        } else {
            // tun eof
            ng_free(buffer, __FILE__, __LINE__);

            log_print(PLATFORM_LOG_PRIORITY_ERROR, "tun %d empty read", args->tun);
            report_exit(args, "tun %d empty read", args->tun);
            return -1;
        }
    }

    return 0;
}

// https://en.wikipedia.org/wiki/IPv6_packet#Extension_headers
// http://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml
static int is_lower_layer(int protocol) {
    // No next header = 59
    return (protocol == 0 || // Hop-by-Hop Options
            protocol == 60 || // Destination Options (before routing header)
            protocol == 43 || // Routing
            protocol == 44 || // Fragment
            protocol == 51 || // Authentication Header (AH)
            protocol == 50 || // Encapsulating Security Payload (ESP)
            protocol == 60 || // Destination Options (before upper-layer header)
            protocol == 135); // Mobility
}

static int is_upper_layer(int protocol) {
    return (protocol == IPPROTO_TCP ||
            protocol == IPPROTO_UDP ||
            protocol == IPPROTO_ICMP ||
            protocol == IPPROTO_ICMPV6);
}

static void handle_ip(const struct arguments *args,
               const uint8_t *pkt, const size_t length,
               const int epoll_fd,
               int sessions, int maxsessions) {
    uint8_t protocol;
    void *saddr;
    void *daddr;
    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];
    char flags[10];
    char data[16];
    int flen = 0;
    uint8_t *payload;

    // Get protocol, addresses & payload
    uint8_t version = (*pkt) >> 4;
    if (version == 4) {
        if (length < sizeof(struct iphdr)) {
            log_print(PLATFORM_LOG_PRIORITY_WARN, "IP4 packet too short length %d", length);
            return;
        }

        struct iphdr *ip4hdr = (struct iphdr *) pkt;

        protocol = ip4hdr->protocol;
        saddr = &ip4hdr->saddr;
        daddr = &ip4hdr->daddr;

        if (ip4hdr->frag_off & IP_MF) {
            log_print(PLATFORM_LOG_PRIORITY_ERROR, "IP fragment offset %u",
                        (ip4hdr->frag_off & IP_OFFMASK) * 8);
            return;
        }

        uint8_t ipoptlen = (uint8_t) ((ip4hdr->ihl - 5) * 4);
        payload = (uint8_t *) (pkt + sizeof(struct iphdr) + ipoptlen);

        if (ntohs(ip4hdr->tot_len) != length) {
            log_print(PLATFORM_LOG_PRIORITY_ERROR, "Invalid length %u header length %u",
                        length, ntohs(ip4hdr->tot_len));
            return;
        }

        if (loglevel < PLATFORM_LOG_PRIORITY_WARN) {
            if (!calc_checksum(0, (uint8_t *) ip4hdr, sizeof(struct iphdr))) {
                log_print(PLATFORM_LOG_PRIORITY_ERROR, "Invalid IP checksum");
                return;
            }
        }
    } else if (version == 6) {
        if (length < sizeof(struct ip6_hdr)) {
            log_print(PLATFORM_LOG_PRIORITY_WARN, "IP6 packet too short length %d", length);
            return;
        }

        struct ip6_hdr *ip6hdr = (struct ip6_hdr *) pkt;

        // Skip extension headers
        uint16_t off = 0;
        protocol = ip6hdr->ip6_nxt;
        if (!is_upper_layer(protocol)) {
            log_print(PLATFORM_LOG_PRIORITY_WARN, "IP6 extension %d", protocol);
            off = sizeof(struct ip6_hdr);
            struct ip6_ext *ext = (struct ip6_ext *) (pkt + off);
            while (is_lower_layer(ext->ip6e_nxt) && !is_upper_layer(protocol)) {
                protocol = ext->ip6e_nxt;
                log_print(PLATFORM_LOG_PRIORITY_WARN, "IP6 extension %d", protocol);

                off += (8 + ext->ip6e_len);
                ext = (struct ip6_ext *) (pkt + off);
            }
            if (!is_upper_layer(protocol)) {
                off = 0;
                protocol = ip6hdr->ip6_nxt;
                log_print(PLATFORM_LOG_PRIORITY_WARN, "IP6 final extension %d", protocol);
            }
        }

        saddr = &ip6hdr->ip6_src;
        daddr = &ip6hdr->ip6_dst;

        payload = (uint8_t *) (pkt + sizeof(struct ip6_hdr) + off);

        // TODO checksum
    } else {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "Unknown version %d", version);
        return;
    }

    inet_ntop(version == 4 ? AF_INET : AF_INET6, saddr, source, sizeof(source));
    inet_ntop(version == 4 ? AF_INET : AF_INET6, daddr, dest, sizeof(dest));

    // Get ports & flags
    int syn = 0;
    uint16_t sport = 0;
    uint16_t dport = 0;
    *data = 0;
    if (protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6) {
        if (length - (payload - pkt) < ICMP_MINLEN) {
            log_print(PLATFORM_LOG_PRIORITY_WARN, "ICMP packet too short");
            return;
        }

        struct icmp *icmp = (struct icmp *) payload;

        sprintf(data, "type %d/%d", icmp->icmp_type, icmp->icmp_code);

        // http://lwn.net/Articles/443051/
        sport = ntohs(icmp->icmp_id);
        dport = ntohs(icmp->icmp_id);

    } else if (protocol == IPPROTO_UDP) {
        if (length - (payload - pkt) < sizeof(struct udphdr)) {
            log_print(PLATFORM_LOG_PRIORITY_WARN, "UDP packet too short");
            return;
        }

        struct udphdr *udp = (struct udphdr *) payload;

        sport = ntohs(udp->source);
        dport = ntohs(udp->dest);

        // TODO checksum (IPv6)
    } else if (protocol == IPPROTO_TCP) {
        if (length - (payload - pkt) < sizeof(struct tcphdr)) {
            log_print(PLATFORM_LOG_PRIORITY_WARN, "TCP packet too short");
            return;
        }

        struct tcphdr *tcp = (struct tcphdr *) payload;

        sport = ntohs(tcp->source);
        dport = ntohs(tcp->dest);

        if (tcp->syn) {
            syn = 1;
            flags[flen++] = 'S';
        }
        if (tcp->ack)
            flags[flen++] = 'A';
        if (tcp->psh)
            flags[flen++] = 'P';
        if (tcp->fin)
            flags[flen++] = 'F';
        if (tcp->rst)
            flags[flen++] = 'R';

        // intercept TLS
        tls_sni_inspection(args, pkt, length, daddr, version, payload);

        // TODO checksum
    } else if (protocol != IPPROTO_HOPOPTS && protocol != IPPROTO_IGMP && protocol != IPPROTO_ESP)
        log_print(PLATFORM_LOG_PRIORITY_WARN, "Unknown protocol %d", protocol);

    flags[flen] = 0;

    // Limit number of sessions
    if (sessions >= maxsessions) {
        if ((protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6) ||
            (protocol == IPPROTO_UDP && !has_udp_session(args, pkt, payload)) ||
            (protocol == IPPROTO_TCP && syn)) {
            log_print(PLATFORM_LOG_PRIORITY_ERROR,
                        "%d of max %d sessions, dropping version %d protocol %d",
                        sessions, maxsessions, protocol, version);
            return;
        }
    }

    // Get uid
    jint uid = -1;
    if (protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6 ||
        (protocol == IPPROTO_UDP && !has_udp_session(args, pkt, payload)) ||
        (protocol == IPPROTO_TCP && syn)) {
        if (args->ctx->sdk <= 28) // Android 9 Pie
            uid = get_uid(version, protocol, saddr, sport, daddr, dport);
        else
            uid = get_uid_q(args, version, protocol, source, sport, dest, dport);
    }

    log_print(PLATFORM_LOG_PRIORITY_DEBUG,
                "Packet v%d %s/%u > %s/%u proto %d flags %s uid %d",
                version, source, sport, dest, dport, protocol, flags, uid);

    // Check if allowed
    int allowed = 0;
    struct allowed *redirect = NULL;
    if (protocol == IPPROTO_UDP && has_udp_session(args, pkt, payload))
        allowed = 1; // could be a lingering/blocked session
    else if (protocol == IPPROTO_TCP && (!syn || (uid == 0 && dport == 53)))
        allowed = 1; // assume existing session
    else {
        packet_t packet;
        packet.version = version;
        packet.protocol = protocol;
        packet.flags = flags;
        packet.source = source;
        packet.sport = sport;
        packet.dest = dest;
        packet.dport = dport;
        packet.data = data;
        packet.uid = uid;
        packet.allowed = 0;

        redirect = is_address_allowed(args, &packet);
        allowed = (redirect != NULL);
        if (redirect != NULL && (*redirect->raddr == 0 || redirect->rport == 0))
            redirect = NULL;
    }

    // Handle allowed traffic
    if (allowed) {
        if (protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6)
            handle_icmp(args, pkt, length, payload, uid, epoll_fd);
        else if (protocol == IPPROTO_UDP)
            handle_udp(args, pkt, length, payload, uid, redirect, epoll_fd);
        else if (protocol == IPPROTO_TCP)
            handle_tcp(args, pkt, length, payload, uid, allowed, redirect, epoll_fd);
    } else {
        if (protocol == IPPROTO_UDP)
            block_udp(args, pkt, length, payload, uid);

        log_print(PLATFORM_LOG_PRIORITY_WARN, "Address v%d p%d %s/%u syn %d not allowed",
                    version, protocol, dest, dport, syn);
    }
}

jint get_uid(const int version, const int protocol,
             const void *saddr, const uint16_t sport,
             const void *daddr, const uint16_t dport) {
    jint uid = -1;

    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];
    inet_ntop(version == 4 ? AF_INET : AF_INET6, saddr, source, sizeof(source));
    inet_ntop(version == 4 ? AF_INET : AF_INET6, daddr, dest, sizeof(dest));

    struct timeval time;
    gettimeofday(&time, NULL);
    long now = (time.tv_sec * 1000) + (time.tv_usec / 1000);

    // Check IPv6 table first
    if (version == 4) {
        int8_t saddr128[16];
        memset(saddr128, 0, 10);
        saddr128[10] = (uint8_t) 0xFF;
        saddr128[11] = (uint8_t) 0xFF;
        memcpy(saddr128 + 12, saddr, 4);

        int8_t daddr128[16];
        memset(daddr128, 0, 10);
        daddr128[10] = (uint8_t) 0xFF;
        daddr128[11] = (uint8_t) 0xFF;
        memcpy(daddr128 + 12, daddr, 4);

        uid = get_uid_sub(6, protocol, saddr128, sport, daddr128, dport, source, dest, now);
        log_print(PLATFORM_LOG_PRIORITY_DEBUG, "uid v%d p%d %s/%u > %s/%u => %d as inet6",
                    version, protocol, source, sport, dest, dport, uid);
    }

    if (uid == -1) {
        uid = get_uid_sub(version, protocol, saddr, sport, daddr, dport, source, dest, now);
        log_print(PLATFORM_LOG_PRIORITY_DEBUG, "uid v%d p%d %s/%u > %s/%u => %d fallback",
                    version, protocol, source, sport, dest, dport, uid);
    }

    if (uid == -1)
        log_print(PLATFORM_LOG_PRIORITY_WARN, "uid v%d p%d %s/%u > %s/%u => not found",
                    version, protocol, source, sport, dest, dport);
    else if (uid >= 0)
        log_print(PLATFORM_LOG_PRIORITY_INFO, "uid v%d p%d %s/%u > %s/%u => %d",
                    version, protocol, source, sport, dest, dport, uid);

    return uid;
}

int uid_cache_size = 0;
struct uid_cache_entry *uid_cache = NULL;

jint get_uid_sub(const int version, const int protocol,
                 const void *saddr, const uint16_t sport,
                 const void *daddr, const uint16_t dport,
                 const char *source, const char *dest,
                 long now) {
    // NETLINK is not available on Android due to SELinux policies :-(
    // http://stackoverflow.com/questions/27148536/netlink-implementation-for-the-android-ndk
    // https://android.googlesource.com/platform/system/sepolicy/+/master/private/app.te (netlink_tcpdiag_socket)

    static uint8_t zero[16] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    int ws = (version == 4 ? 1 : 4);

    // Check cache
    for (int i = 0; i < uid_cache_size; i++)
        if (now - uid_cache[i].time <= UID_MAX_AGE &&
            uid_cache[i].version == version &&
            uid_cache[i].protocol == protocol &&
            uid_cache[i].sport == sport &&
            (uid_cache[i].dport == dport || uid_cache[i].dport == 0) &&
            (memcmp(uid_cache[i].saddr, saddr, (size_t) (ws * 4)) == 0 ||
             memcmp(uid_cache[i].saddr, zero, (size_t) (ws * 4)) == 0) &&
            (memcmp(uid_cache[i].daddr, daddr, (size_t) (ws * 4)) == 0 ||
             memcmp(uid_cache[i].daddr, zero, (size_t) (ws * 4)) == 0)) {

            log_print(PLATFORM_LOG_PRIORITY_INFO, "uid v%d p%d %s/%u > %s/%u => %d (from cache)",
                        version, protocol, source, sport, dest, dport, uid_cache[i].uid);

            return uid_cache[i].uid;
        }

    // Get proc file name
    char *fn = NULL;
    if (protocol == IPPROTO_ICMP && version == 4)
        fn = "/proc/net/icmp";
    else if (protocol == IPPROTO_ICMPV6 && version == 6)
        fn = "/proc/net/icmp6";
    else if (protocol == IPPROTO_TCP)
        fn = (version == 4 ? "/proc/net/tcp" : "/proc/net/tcp6");
    else if (protocol == IPPROTO_UDP)
        fn = (version == 4 ? "/proc/net/udp" : "/proc/net/udp6");
    else
        return -1;

    // Open proc file
    FILE *fd = fopen(fn, "r");
    if (fd == NULL) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "fopen %s error %d: %s", fn, errno, strerror(errno));
        return -2;
    }

    jint uid = -1;

    char line[250];
    int fields;

    char shex[16 * 2 + 1];
    uint8_t _saddr[16];
    int _sport;

    char dhex[16 * 2 + 1];
    uint8_t _daddr[16];
    int _dport;

    jint _uid;

    // Scan proc file
    int l = 0;
    *line = 0;
    int c = 0;
    const char *fmt = (version == 4
                       ? "%*d: %8s:%X %8s:%X %*X %*lX:%*lX %*X:%*X %*X %d %*d %*ld"
                       : "%*d: %32s:%X %32s:%X %*X %*lX:%*lX %*X:%*X %*X %d %*d %*ld");
    while (fgets(line, sizeof(line), fd) != NULL) {
        if (!l++)
            continue;

        fields = sscanf(line, fmt, shex, &_sport, dhex, &_dport, &_uid);
        if (fields == 5 && strlen(shex) == ws * 8 && strlen(dhex) == ws * 8) {
            hex2bytes(shex, _saddr);
            hex2bytes(dhex, _daddr);

            for (int w = 0; w < ws; w++)
                ((uint32_t *) _saddr)[w] = htonl(((uint32_t *) _saddr)[w]);

            for (int w = 0; w < ws; w++)
                ((uint32_t *) _daddr)[w] = htonl(((uint32_t *) _daddr)[w]);

            if (_sport == sport &&
                (_dport == dport || _dport == 0) &&
                (memcmp(_saddr, saddr, (size_t) (ws * 4)) == 0 ||
                 memcmp(_saddr, zero, (size_t) (ws * 4)) == 0) &&
                (memcmp(_daddr, daddr, (size_t) (ws * 4)) == 0 ||
                 memcmp(_daddr, zero, (size_t) (ws * 4)) == 0))
                uid = _uid;

            for (; c < uid_cache_size; c++)
                if (now - uid_cache[c].time > UID_MAX_AGE)
                    break;

            if (c >= uid_cache_size) {
                if (uid_cache_size == 0)
                    uid_cache = ng_malloc(sizeof(struct uid_cache_entry), "uid_cache init");
                else
                    uid_cache = ng_realloc(uid_cache,
                                           sizeof(struct uid_cache_entry) *
                                           (uid_cache_size + 1), "uid_cache extend");
                c = uid_cache_size;
                uid_cache_size++;
            }

            uid_cache[c].version = (uint8_t) version;
            uid_cache[c].protocol = (uint8_t) protocol;
            memcpy(uid_cache[c].saddr, _saddr, (size_t) (ws * 4));
            uid_cache[c].sport = (uint16_t) _sport;
            memcpy(uid_cache[c].daddr, _daddr, (size_t) (ws * 4));
            uid_cache[c].dport = (uint16_t) _dport;
            uid_cache[c].uid = _uid;
            uid_cache[c].time = now;
        } else {
            log_print(PLATFORM_LOG_PRIORITY_ERROR, "Invalid field #%d: %s", fields, line);
            return -2;
        }
    }

    if (fclose(fd))
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "fclose %s error %d: %s", fn, errno, strerror(errno));

    return uid;
}
