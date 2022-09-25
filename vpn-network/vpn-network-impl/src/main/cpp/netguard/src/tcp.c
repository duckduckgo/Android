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

#define SOCKS5_NONE 1
#define SOCKS5_HELLO 2
#define SOCKS5_AUTH 3
#define SOCKS5_CONNECT 4
#define SOCKS5_CONNECTED 5

#define TCP_INIT_TIMEOUT 20 // seconds ~net.inet.tcp.keepinit
#define TCP_IDLE_TIMEOUT 3600 // seconds ~net.inet.tcp.keepidle
#define TCP_CLOSE_TIMEOUT 20 // seconds
#define TCP_KEEP_TIMEOUT 300 // seconds
// https://en.wikipedia.org/wiki/Maximum_segment_lifetime

#define SEND_BUF_DEFAULT 163840 // bytes

struct segment {
    uint32_t seq;
    uint16_t len;
    uint16_t sent;
    int psh;
    uint8_t *data;
    struct segment *next;
};


static uint32_t get_send_window(const struct tcp_session *cur);

static uint32_t get_receive_buffer(const struct ng_session *cur);

static uint32_t get_receive_window(const struct ng_session *cur);

static void queue_tcp(const struct arguments *args,
               const struct tcphdr *tcphdr,
               const char *session, struct tcp_session *cur,
               const uint8_t *data, uint16_t datalen);

static int open_tcp_socket(const struct arguments *args,
                    const struct tcp_session *cur, const struct allowed *redirect);

static int write_syn_ack(const struct arguments *args, struct tcp_session *cur);

static int write_ack(const struct arguments *args, struct tcp_session *cur);

static int write_data(const struct arguments *args, struct tcp_session *cur,
               const uint8_t *buffer, size_t length);

static int write_fin_ack(const struct arguments *args, struct tcp_session *cur);

static void write_rst_ack(const struct arguments *args, struct tcp_session *cur);

static ssize_t write_tcp(const struct arguments *args, const struct tcp_session *cur,
                  const uint8_t *data, size_t datalen,
                  int syn, int ack, int fin, int rst);

///////////////////////////////////////////////////////////////////////////////

void clear_tcp_data(struct tcp_session *cur) {
    struct segment *s = cur->forward;
    while (s != NULL) {
        struct segment *p = s;
        s = s->next;
        ng_free(p->data, __FILE__, __LINE__);
        ng_free(p, __FILE__, __LINE__);
    }
}

int get_tcp_timeout(const struct tcp_session *t, int sessions, int maxsessions) {
    int timeout;
    if (t->state == TCP_LISTEN || t->state == TCP_SYN_RECV)
        timeout = TCP_INIT_TIMEOUT;
    else if (t->state == TCP_ESTABLISHED)
        timeout = TCP_IDLE_TIMEOUT;
    else
        timeout = TCP_CLOSE_TIMEOUT;

    int scale = 100 - sessions * 100 / maxsessions;
    timeout = timeout * scale / 100;

    return timeout;
}

int check_tcp_session(const struct arguments *args, struct ng_session *s,
                      int sessions, int maxsessions) {
    time_t now = time(NULL);

    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];
    if (s->tcp.version == 4) {
        inet_ntop(AF_INET, &s->tcp.saddr.ip4, source, sizeof(source));
        inet_ntop(AF_INET, &s->tcp.daddr.ip4, dest, sizeof(dest));
    } else {
        inet_ntop(AF_INET6, &s->tcp.saddr.ip6, source, sizeof(source));
        inet_ntop(AF_INET6, &s->tcp.daddr.ip6, dest, sizeof(dest));
    }

    char session[250];
    sprintf(session, "TCP socket from %s/%u to %s/%u %s socket %d",
            source, ntohs(s->tcp.source), dest, ntohs(s->tcp.dest),
            strstate(s->tcp.state), s->socket);

    int timeout = get_tcp_timeout(&s->tcp, sessions, maxsessions);

    // Check session timeout
    if (s->tcp.state != TCP_CLOSING && s->tcp.state != TCP_CLOSE &&
        s->tcp.time + timeout < now) {
        log_print(PLATFORM_LOG_PRIORITY_WARN, "%s idle %d/%d sec ", session, now - s->tcp.time,
                    timeout);
        if (s->tcp.state == TCP_LISTEN)
            s->tcp.state = TCP_CLOSING;
        else
            write_rst(args, &s->tcp);
    }

    // Check closing sessions
    if (s->tcp.state == TCP_CLOSING) {
        // eof closes socket
        if (s->socket >= 0) {
            if (close(s->socket))
                log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s close error %d: %s",
                            session, errno, strerror(errno));
            else
                log_print(PLATFORM_LOG_PRIORITY_WARN, "%s close", session);
            s->socket = -1;
        }

        s->tcp.time = time(NULL);
        s->tcp.state = TCP_CLOSE;
    }

    if ((s->tcp.state == TCP_CLOSING || s->tcp.state == TCP_CLOSE) &&
        (s->tcp.sent || s->tcp.received)) {
        account_usage(args, s->tcp.version, IPPROTO_TCP,
                      dest, ntohs(s->tcp.dest), s->tcp.uid, s->tcp.sent, s->tcp.received);
        s->tcp.sent = 0;
        s->tcp.received = 0;
    }

    // Cleanup lingering sessions
    if (s->tcp.state == TCP_CLOSE && s->tcp.time + TCP_KEEP_TIMEOUT < now)
        return 1;

    return 0;
}

int monitor_tcp_session(const struct arguments *args, struct ng_session *s, int epoll_fd) {
    int recheck = 0;
    unsigned int events = EPOLLERR;

    if (s->tcp.state == TCP_LISTEN) {
        // Check for connected = writable
        if (s->tcp.socks5 == SOCKS5_NONE)
            events = events | EPOLLOUT;
        else
            events = events | EPOLLIN;
    } else if (s->tcp.state == TCP_ESTABLISHED || s->tcp.state == TCP_CLOSE_WAIT) {

        // Check for incoming data
        if (get_send_window(&s->tcp) > 0)
            events = events | EPOLLIN;
        else {
            recheck = 1;

            long long ms = get_ms();
            if (ms - s->tcp.last_keep_alive > EPOLL_MIN_CHECK) {
                s->tcp.last_keep_alive = ms;
                log_print(PLATFORM_LOG_PRIORITY_WARN, "Sending keep alive to update send window");
                s->tcp.remote_seq--;
                write_ack(args, &s->tcp);
                s->tcp.remote_seq++;
            }
        }

        // Check for outgoing data
        if (s->tcp.forward != NULL) {
            uint32_t buffer_size = get_receive_buffer(s);
            if (s->tcp.forward->seq == s->tcp.remote_seq &&
                s->tcp.forward->len - s->tcp.forward->sent < buffer_size)
                events = events | EPOLLOUT;
            else
                recheck = 1;
        }
    }

    if (events != s->ev.events) {
        s->ev.events = events;
        if (epoll_ctl(epoll_fd, EPOLL_CTL_MOD, s->socket, &s->ev)) {
            s->tcp.state = TCP_CLOSING;
            log_print(PLATFORM_LOG_PRIORITY_ERROR, "epoll mod tcp error %d: %s", errno, strerror(errno));
        } else
            log_print(PLATFORM_LOG_PRIORITY_DEBUG, "epoll mod tcp socket %d in %d out %d",
                        s->socket, (events & EPOLLIN) != 0, (events & EPOLLOUT) != 0);
    }

    return recheck;
}

static uint32_t get_send_window(const struct tcp_session *cur) {
    uint32_t behind;
    if (cur->acked <= cur->local_seq)
        behind = (cur->local_seq - cur->acked);
    else
        behind = (0x10000 + cur->local_seq - cur->acked);
    behind += (cur->unconfirmed + 1) * 40; // Maximum header size

    uint32_t total = (behind < cur->send_window ? cur->send_window - behind : 0);

    log_print(PLATFORM_LOG_PRIORITY_DEBUG, "Send window behind %u window %u total %u",
                behind, cur->send_window, total);

    return total;
}

static uint32_t get_receive_buffer(const struct ng_session *cur) {
    if (cur->socket < 0)
        return 0;

    // Get send buffer size
    // /proc/sys/net/core/wmem_default
    int sendbuf = 0;
    int sendbufsize = sizeof(sendbuf);
    if (getsockopt(cur->socket, SOL_SOCKET, SO_SNDBUF, &sendbuf, (socklen_t *) &sendbufsize) < 0)
        log_print(PLATFORM_LOG_PRIORITY_WARN, "getsockopt SO_RCVBUF %d: %s", errno, strerror(errno));

    if (sendbuf == 0)
        sendbuf = SEND_BUF_DEFAULT;

    // Get unsent data size
    int unsent = 0;
    if (ioctl(cur->socket, SIOCOUTQ, &unsent))
        log_print(PLATFORM_LOG_PRIORITY_WARN, "ioctl SIOCOUTQ %d: %s", errno, strerror(errno));

    uint32_t total = (uint32_t) (unsent < sendbuf ? sendbuf - unsent : 0);

    log_print(PLATFORM_LOG_PRIORITY_DEBUG, "Send buffer %u unsent %u total %u",
                sendbuf, unsent, total);

    return total;
}

static uint32_t get_receive_window(const struct ng_session *cur) {
    // Get data to forward size
    uint32_t toforward = 0;
    struct segment *q = cur->tcp.forward;
    while (q != NULL) {
        toforward += (q->len - q->sent);
        q = q->next;
    }

    uint32_t window = get_receive_buffer(cur);

    uint32_t max = ((uint32_t) 0xFFFF) << cur->tcp.recv_scale;
    if (window > max) {
        log_print(PLATFORM_LOG_PRIORITY_DEBUG, "Receive window %u > max %u", window, max);
        window = max;
    }

    uint32_t total = (toforward < window ? window - toforward : 0);

    log_print(PLATFORM_LOG_PRIORITY_DEBUG, "Receive window toforward %u window %u total %u",
                toforward, window, total);

    return total;
}

void check_tcp_socket(const struct arguments *args,
                      const struct epoll_event *ev,
                      const int epoll_fd) {
    struct ng_session *s = (struct ng_session *) ev->data.ptr;

    int oldstate = s->tcp.state;
    uint32_t oldlocal = s->tcp.local_seq;
    uint32_t oldremote = s->tcp.remote_seq;

    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];
    if (s->tcp.version == 4) {
        inet_ntop(AF_INET, &s->tcp.saddr.ip4, source, sizeof(source));
        inet_ntop(AF_INET, &s->tcp.daddr.ip4, dest, sizeof(dest));
    } else {
        inet_ntop(AF_INET6, &s->tcp.saddr.ip6, source, sizeof(source));
        inet_ntop(AF_INET6, &s->tcp.daddr.ip6, dest, sizeof(dest));
    }
    char session[250];
    sprintf(session, "TCP socket from %s/%u to %s/%u %s loc %u rem %u",
            source, ntohs(s->tcp.source), dest, ntohs(s->tcp.dest),
            strstate(s->tcp.state),
            s->tcp.local_seq - s->tcp.local_start,
            s->tcp.remote_seq - s->tcp.remote_start);

    // Check socket error
    if (ev->events & EPOLLERR) {
        s->tcp.time = time(NULL);

        int serr = 0;
        socklen_t optlen = sizeof(int);
        int err = getsockopt(s->socket, SOL_SOCKET, SO_ERROR, &serr, &optlen);
        if (err < 0)
            log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s getsockopt error %d: %s",
                        session, errno, strerror(errno));
        else if (serr)
            log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s SO_ERROR %d: %s",
                        session, serr, strerror(serr));

        write_rst(args, &s->tcp);

        // Connection refused
        if (0)
            if (err >= 0 && (serr == ECONNREFUSED || serr == EHOSTUNREACH)) {
                struct icmp icmp;
                memset(&icmp, 0, sizeof(struct icmp));
                icmp.icmp_type = ICMP_UNREACH;
                if (serr == ECONNREFUSED)
                    icmp.icmp_code = ICMP_UNREACH_PORT;
                else
                    icmp.icmp_code = ICMP_UNREACH_HOST;
                icmp.icmp_cksum = 0;
                icmp.icmp_cksum = ~calc_checksum(0, (const uint8_t *) &icmp, 4);

                struct icmp_session sicmp;
                memset(&sicmp, 0, sizeof(struct icmp_session));
                sicmp.version = s->tcp.version;
                if (s->tcp.version == 4) {
                    sicmp.saddr.ip4 = (__be32) s->tcp.saddr.ip4;
                    sicmp.daddr.ip4 = (__be32) s->tcp.daddr.ip4;
                } else {
                    memcpy(&sicmp.saddr.ip6, &s->tcp.saddr.ip6, 16);
                    memcpy(&sicmp.daddr.ip6, &s->tcp.daddr.ip6, 16);
                }

                write_icmp(args, &sicmp, (uint8_t *) &icmp, 8);
            }
    } else {
        // Assume socket okay
        if (s->tcp.state == TCP_LISTEN) {
            // Check socket connect
            if (s->tcp.socks5 == SOCKS5_NONE) {
                if (ev->events & EPOLLOUT) {
                    log_print(PLATFORM_LOG_PRIORITY_INFO, "%s connected", session);

                    // https://tools.ietf.org/html/rfc1928
                    // https://tools.ietf.org/html/rfc1929
                    // https://en.wikipedia.org/wiki/SOCKS#SOCKS5
                    if (*socks5_addr && socks5_port)
                        s->tcp.socks5 = SOCKS5_HELLO;
                    else
                        s->tcp.socks5 = SOCKS5_CONNECTED;
                }
            } else {
                if (ev->events & EPOLLIN) {
                    uint8_t buffer[32];
                    ssize_t bytes = recv(s->socket, buffer, sizeof(buffer), 0);
                    if (bytes < 0) {
                        log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s recv SOCKS5 error %d: %s",
                                    session, errno, strerror(errno));
                        write_rst(args, &s->tcp);
                    } else {
                        char *h = hex(buffer, (const size_t) bytes);
                        log_print(PLATFORM_LOG_PRIORITY_INFO, "%s recv SOCKS5 %s", session, h);
                        ng_free(h, __FILE__, __LINE__);

                        if (s->tcp.socks5 == SOCKS5_HELLO &&
                            bytes == 2 && buffer[0] == 5) {
                            if (buffer[1] == 0)
                                s->tcp.socks5 = SOCKS5_CONNECT;
                            else if (buffer[1] == 2)
                                s->tcp.socks5 = SOCKS5_AUTH;
                            else {
                                s->tcp.socks5 = 0;
                                log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s SOCKS5 auth %d not supported",
                                            session, buffer[1]);
                                write_rst(args, &s->tcp);
                            }

                        } else if (s->tcp.socks5 == SOCKS5_AUTH &&
                                   bytes == 2 &&
                                   (buffer[0] == 1 || buffer[0] == 5)) {
                            if (buffer[1] == 0) {
                                s->tcp.socks5 = SOCKS5_CONNECT;
                                log_print(PLATFORM_LOG_PRIORITY_WARN, "%s SOCKS5 auth OK", session);
                            } else {
                                s->tcp.socks5 = 0;
                                log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s SOCKS5 auth error %d",
                                            session, buffer[1]);
                                write_rst(args, &s->tcp);
                            }

                        } else if (s->tcp.socks5 == SOCKS5_CONNECT &&
                                   bytes == 6 + (s->tcp.version == 4 ? 4 : 16) &&
                                   buffer[0] == 5) {
                            if (buffer[1] == 0) {
                                s->tcp.socks5 = SOCKS5_CONNECTED;
                                log_print(PLATFORM_LOG_PRIORITY_WARN, "%s SOCKS5 connected", session);
                            } else {
                                s->tcp.socks5 = 0;
                                log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s SOCKS5 connect error %d",
                                            session, buffer[1]);
                                write_rst(args, &s->tcp);
                                /*
                                    0x00 = request granted
                                    0x01 = general failure
                                    0x02 = connection not allowed by ruleset
                                    0x03 = network unreachable
                                    0x04 = host unreachable
                                    0x05 = connection refused by destination host
                                    0x06 = TTL expired
                                    0x07 = command not supported / protocol error
                                    0x08 = address type not supported
                                 */
                            }

                        } else {
                            s->tcp.socks5 = 0;
                            log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s recv SOCKS5 state %d",
                                        session, s->tcp.socks5);
                            write_rst(args, &s->tcp);
                        }
                    }
                }
            }

            if (s->tcp.socks5 == SOCKS5_HELLO) {
                uint8_t buffer[4] = {5, 2, 0, 2};
                char *h = hex(buffer, sizeof(buffer));
                log_print(PLATFORM_LOG_PRIORITY_INFO, "%s sending SOCKS5 hello: %s",
                            session, h);
                ng_free(h, __FILE__, __LINE__);
                ssize_t sent = send(s->socket, buffer, sizeof(buffer), MSG_NOSIGNAL);
                if (sent < 0) {
                    log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s send SOCKS5 hello error %d: %s",
                                session, errno, strerror(errno));
                    write_rst(args, &s->tcp);
                }

            } else if (s->tcp.socks5 == SOCKS5_AUTH) {
                uint8_t ulen = strlen(socks5_username);
                uint8_t plen = strlen(socks5_password);
                uint8_t buffer[512];
                *(buffer + 0) = 1; // Version
                *(buffer + 1) = ulen;
                memcpy(buffer + 2, socks5_username, ulen);
                *(buffer + 2 + ulen) = plen;
                memcpy(buffer + 2 + ulen + 1, socks5_password, plen);

                size_t len = 2 + ulen + 1 + plen;

                char *h = hex(buffer, len);
                log_print(PLATFORM_LOG_PRIORITY_INFO, "%s sending SOCKS5 auth: %s",
                            session, h);
                ng_free(h, __FILE__, __LINE__);
                ssize_t sent = send(s->socket, buffer, len, MSG_NOSIGNAL);
                if (sent < 0) {
                    log_print(PLATFORM_LOG_PRIORITY_ERROR,
                                "%s send SOCKS5 connect error %d: %s",
                                session, errno, strerror(errno));
                    write_rst(args, &s->tcp);
                }

            } else if (s->tcp.socks5 == SOCKS5_CONNECT) {
                uint8_t buffer[22];
                *(buffer + 0) = 5; // version
                *(buffer + 1) = 1; // TCP/IP stream connection
                *(buffer + 2) = 0; // reserved
                *(buffer + 3) = (uint8_t) (s->tcp.version == 4 ? 1 : 4);
                if (s->tcp.version == 4) {
                    memcpy(buffer + 4, &s->tcp.daddr.ip4, 4);
                    *((__be16 *) (buffer + 4 + 4)) = s->tcp.dest;
                } else {
                    memcpy(buffer + 4, &s->tcp.daddr.ip6, 16);
                    *((__be16 *) (buffer + 4 + 16)) = s->tcp.dest;
                }

                size_t len = (s->tcp.version == 4 ? 10 : 22);

                char *h = hex(buffer, len);
                log_print(PLATFORM_LOG_PRIORITY_INFO, "%s sending SOCKS5 connect: %s",
                            session, h);
                ng_free(h, __FILE__, __LINE__);
                ssize_t sent = send(s->socket, buffer, len, MSG_NOSIGNAL);
                if (sent < 0) {
                    log_print(PLATFORM_LOG_PRIORITY_ERROR,
                                "%s send SOCKS5 connect error %d: %s",
                                session, errno, strerror(errno));
                    write_rst(args, &s->tcp);
                }

            } else if (s->tcp.socks5 == SOCKS5_CONNECTED) {
                s->tcp.remote_seq++; // remote SYN
                if (write_syn_ack(args, &s->tcp) >= 0) {
                    s->tcp.time = time(NULL);
                    s->tcp.local_seq++; // local SYN
                    s->tcp.state = TCP_SYN_RECV;
                }
            }
        } else {

            // Always forward data
            int fwd = 0;
            if (ev->events & EPOLLOUT) {
                // Forward data
                uint32_t buffer_size = get_receive_buffer(s);
                while (s->tcp.forward != NULL &&
                       s->tcp.forward->seq == s->tcp.remote_seq &&
                       s->tcp.forward->len - s->tcp.forward->sent < buffer_size) {
                    log_print(PLATFORM_LOG_PRIORITY_DEBUG, "%s fwd %u...%u sent %u",
                                session,
                                s->tcp.forward->seq - s->tcp.remote_start,
                                s->tcp.forward->seq + s->tcp.forward->len - s->tcp.remote_start,
                                s->tcp.forward->sent);

                    ssize_t sent = send(s->socket,
                                        s->tcp.forward->data + s->tcp.forward->sent,
                                        s->tcp.forward->len - s->tcp.forward->sent,
                                        (unsigned int) (MSG_NOSIGNAL | (s->tcp.forward->psh
                                                                        ? 0
                                                                        : MSG_MORE)));
                    if (sent < 0) {
                        log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s send error %d: %s",
                                    session, errno, strerror(errno));
                        if (errno == EINTR || errno == EAGAIN) {
                            // Retry later
                            break;
                        } else {
                            write_rst(args, &s->tcp);
                            break;
                        }
                    } else {
                        fwd = 1;
                        buffer_size -= sent;
                        s->tcp.sent += sent;
                        s->tcp.forward->sent += sent;

                        if (s->tcp.forward->len == s->tcp.forward->sent) {
                            s->tcp.remote_seq = s->tcp.forward->seq + s->tcp.forward->sent;

                            struct segment *p = s->tcp.forward;
                            s->tcp.forward = s->tcp.forward->next;
                            ng_free(p->data, __FILE__, __LINE__);
                            ng_free(p, __FILE__, __LINE__);
                        } else {
                            log_print(PLATFORM_LOG_PRIORITY_WARN,
                                        "%s partial send %u/%u",
                                        session, s->tcp.forward->sent, s->tcp.forward->len);
                            break;
                        }
                    }
                }

                // Log data buffered
                struct segment *seg = s->tcp.forward;
                while (seg != NULL) {
                    log_print(PLATFORM_LOG_PRIORITY_WARN, "%s queued %u...%u sent %u",
                                session,
                                seg->seq - s->tcp.remote_start,
                                seg->seq + seg->len - s->tcp.remote_start,
                                seg->sent);
                    seg = seg->next;
                }
            }

            // Get receive window
            uint32_t window = get_receive_window(s);
            uint32_t prev = s->tcp.recv_window;
            s->tcp.recv_window = window;
            if ((prev == 0 && window > 0) || (prev > 0 && window == 0))
                log_print(PLATFORM_LOG_PRIORITY_WARN, "%s recv window %u > %u",
                            session, prev, window);

            // Acknowledge forwarded data
            if (fwd || (prev == 0 && window > 0)) {
                if (fwd && s->tcp.forward == NULL && s->tcp.state == TCP_CLOSE_WAIT) {
                    log_print(PLATFORM_LOG_PRIORITY_WARN, "%s confirm FIN", session);
                    s->tcp.remote_seq++; // remote FIN
                }
                if (write_ack(args, &s->tcp) >= 0)
                    s->tcp.time = time(NULL);
            }

            if (s->tcp.state == TCP_ESTABLISHED || s->tcp.state == TCP_CLOSE_WAIT) {
                // Check socket read
                // Send window can be changed in the mean time

                uint32_t send_window = get_send_window(&s->tcp);
                if ((ev->events & EPOLLIN) && send_window > 0) {
                    s->tcp.time = time(NULL);

                    uint32_t buffer_size = (send_window > s->tcp.mss
                                            ? s->tcp.mss : send_window);
                    uint8_t *buffer = ng_malloc(buffer_size, "tcp socket");
                    ssize_t bytes = recv(s->socket, buffer, (size_t) buffer_size, 0);
                    if (bytes < 0) {
                        // Socket error
                        log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s recv error %d: %s",
                                    session, errno, strerror(errno));

                        if (errno != EINTR && errno != EAGAIN)
                            write_rst(args, &s->tcp);
                    } else if (bytes == 0) {
                        log_print(PLATFORM_LOG_PRIORITY_WARN, "%s recv eof", session);

                        if (s->tcp.forward == NULL) {
                            if (write_fin_ack(args, &s->tcp) >= 0) {
                                log_print(PLATFORM_LOG_PRIORITY_WARN, "%s FIN sent", session);
                                s->tcp.local_seq++; // local FIN
                            }

                            if (s->tcp.state == TCP_ESTABLISHED)
                                s->tcp.state = TCP_FIN_WAIT1;
                            else if (s->tcp.state == TCP_CLOSE_WAIT)
                                s->tcp.state = TCP_LAST_ACK;
                            else
                                log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s invalid close", session);
                        } else {
                            // There was still data to send
                            log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s close with queue", session);
                            write_rst(args, &s->tcp);
                        }

                        if (close(s->socket))
                            log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s close error %d: %s",
                                        session, errno, strerror(errno));
                        s->socket = -1;

                    } else {
                        // Socket read data
                        log_print(PLATFORM_LOG_PRIORITY_DEBUG, "%s recv bytes %d", session, bytes);
                        s->tcp.received += bytes;

                        // Process DNS response
                        int block_dns = 0;
                        if (ntohs(s->tcp.dest) == 53 && bytes > 2) {
                            ssize_t dlen = bytes - 2;
                            block_dns = parse_dns_response(args, s, buffer + 2, (size_t *) &dlen);
                        }

                        // Forward to tun
                        if (block_dns == 0 && write_data(args, &s->tcp, buffer, (size_t) bytes) >= 0) {
                            s->tcp.local_seq += bytes;
                            s->tcp.unconfirmed++;
                        }
                    }
                    ng_free(buffer, __FILE__, __LINE__);
                }
            }
        }
    }

    if (s->tcp.state != oldstate || s->tcp.local_seq != oldlocal ||
        s->tcp.remote_seq != oldremote)
        log_print(PLATFORM_LOG_PRIORITY_DEBUG, "%s new state", session);
}

jboolean handle_tcp(const struct arguments *args,
                    const uint8_t *pkt, size_t length,
                    const uint8_t *payload,
                    int uid, int allowed, struct allowed *redirect,
                    const int epoll_fd) {
    // Get headers
    const uint8_t version = (*pkt) >> 4;
    const struct iphdr *ip4 = (struct iphdr *) pkt;
    const struct ip6_hdr *ip6 = (struct ip6_hdr *) pkt;
    const struct tcphdr *tcphdr = (struct tcphdr *) payload;
    const uint8_t tcpoptlen = (uint8_t) ((tcphdr->doff - 5) * 4);
    const uint8_t *tcpoptions = payload + sizeof(struct tcphdr);
    const uint8_t *data = payload + sizeof(struct tcphdr) + tcpoptlen;
    const uint16_t datalen = (const uint16_t) (length - (data - pkt));

    // Search session
    struct ng_session *cur = args->ctx->ng_session;
    while (cur != NULL &&
           !(cur->protocol == IPPROTO_TCP &&
             cur->tcp.version == version &&
             cur->tcp.source == tcphdr->source && cur->tcp.dest == tcphdr->dest &&
             (version == 4 ? cur->tcp.saddr.ip4 == ip4->saddr &&
                             cur->tcp.daddr.ip4 == ip4->daddr
                           : memcmp(&cur->tcp.saddr.ip6, &ip6->ip6_src, 16) == 0 &&
                             memcmp(&cur->tcp.daddr.ip6, &ip6->ip6_dst, 16) == 0)))
        cur = cur->next;

    // Prepare logging
    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];
    if (version == 4) {
        inet_ntop(AF_INET, &ip4->saddr, source, sizeof(source));
        inet_ntop(AF_INET, &ip4->daddr, dest, sizeof(dest));
    } else {
        inet_ntop(AF_INET6, &ip6->ip6_src, source, sizeof(source));
        inet_ntop(AF_INET6, &ip6->ip6_dst, dest, sizeof(dest));
    }

    char flags[10];
    int flen = 0;
    if (tcphdr->syn)
        flags[flen++] = 'S';
    if (tcphdr->ack)
        flags[flen++] = 'A';
    if (tcphdr->psh)
        flags[flen++] = 'P';
    if (tcphdr->fin)
        flags[flen++] = 'F';
    if (tcphdr->rst)
        flags[flen++] = 'R';
    if (tcphdr->urg)
        flags[flen++] = 'U';
    flags[flen] = 0;

    char packet[250];
    sprintf(packet,
            "TCP %s %s/%u > %s/%u seq %u ack %u data %u win %u uid %d",
            flags,
            source, ntohs(tcphdr->source),
            dest, ntohs(tcphdr->dest),
            ntohl(tcphdr->seq) - (cur == NULL ? 0 : cur->tcp.remote_start),
            tcphdr->ack ? ntohl(tcphdr->ack_seq) - (cur == NULL ? 0 : cur->tcp.local_start) : 0,
            datalen, ntohs(tcphdr->window), uid);
    log_print(tcphdr->urg ? PLATFORM_LOG_PRIORITY_WARN : PLATFORM_LOG_PRIORITY_DEBUG, packet);

    // Drop URG data
    if (tcphdr->urg)
        return 1;

    // Check session
    if (cur == NULL) {
        if (tcphdr->syn) {
            // Decode options
            // http://www.iana.org/assignments/tcp-parameters/tcp-parameters.xhtml#tcp-parameters-1
            uint16_t mss = get_default_mss(version);
            uint8_t ws = 0;
            int optlen = tcpoptlen;
            uint8_t *options = (uint8_t *) tcpoptions;
            while (optlen > 0) {
                uint8_t kind = *options;
                uint8_t len = *(options + 1);
                if (kind == 0) // End of options list
                    break;

                if (kind == 2 && len == 4)
                    mss = ntohs(*((uint16_t *) (options + 2)));

                else if (kind == 3 && len == 3)
                    ws = *(options + 2);

                if (kind == 1) {
                    optlen--;
                    options++;
                } else {
                    optlen -= len;
                    options += len;
                }
            }

            log_print(PLATFORM_LOG_PRIORITY_WARN, "%s new session mss %u ws %u window %u",
                        packet, mss, ws, ntohs(tcphdr->window) << ws);

            // Register session
            struct ng_session *s = ng_malloc(sizeof(struct ng_session), "tcp session");
            s->protocol = IPPROTO_TCP;

            s->tcp.time = time(NULL);
            s->tcp.uid = uid;
            s->tcp.version = version;
            s->tcp.mss = mss;
            s->tcp.recv_scale = ws;
            s->tcp.send_scale = ws;
            s->tcp.send_window = ((uint32_t) ntohs(tcphdr->window)) << s->tcp.send_scale;
            s->tcp.unconfirmed = 0;
            s->tcp.remote_seq = ntohl(tcphdr->seq); // ISN remote
            s->tcp.local_seq = (uint32_t) rand(); // ISN local
            s->tcp.remote_start = s->tcp.remote_seq;
            s->tcp.local_start = s->tcp.local_seq;
            s->tcp.acked = 0;
            s->tcp.last_keep_alive = 0;
            s->tcp.sent = 0;
            s->tcp.received = 0;

            if (version == 4) {
                s->tcp.saddr.ip4 = (__be32) ip4->saddr;
                s->tcp.daddr.ip4 = (__be32) ip4->daddr;
            } else {
                memcpy(&s->tcp.saddr.ip6, &ip6->ip6_src, 16);
                memcpy(&s->tcp.daddr.ip6, &ip6->ip6_dst, 16);
            }

            s->tcp.source = tcphdr->source;
            s->tcp.dest = tcphdr->dest;
            s->tcp.state = TCP_LISTEN;
            s->tcp.socks5 = SOCKS5_NONE;
            s->tcp.forward = NULL;
            s->next = NULL;

            if (datalen) {
                log_print(PLATFORM_LOG_PRIORITY_WARN, "%s SYN data", packet);
                s->tcp.forward = ng_malloc(sizeof(struct segment), "syn segment");
                s->tcp.forward->seq = s->tcp.remote_seq;
                s->tcp.forward->len = datalen;
                s->tcp.forward->sent = 0;
                s->tcp.forward->psh = tcphdr->psh;
                s->tcp.forward->data = ng_malloc(datalen, "syn segment data");
                memcpy(s->tcp.forward->data, data, datalen);
                s->tcp.forward->next = NULL;
            }

            // Open socket
            s->socket = open_tcp_socket(args, &s->tcp, redirect);
            if (s->socket < 0) {
                // Remote might retry
                ng_free(s, __FILE__, __LINE__);
                return 0;
            }

            s->tcp.recv_window = get_receive_window(s);

            log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TCP socket %d lport %d",
                        s->socket, get_local_port(s->socket));

            // Monitor events
            memset(&s->ev, 0, sizeof(struct epoll_event));
            s->ev.events = EPOLLOUT | EPOLLERR;
            s->ev.data.ptr = s;
            if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, s->socket, &s->ev))
                log_print(PLATFORM_LOG_PRIORITY_ERROR, "epoll add tcp error %d: %s",
                            errno, strerror(errno));

            s->next = args->ctx->ng_session;
            args->ctx->ng_session = s;

            if (!allowed) {
                log_print(PLATFORM_LOG_PRIORITY_WARN, "%s resetting blocked session", packet);
                write_rst(args, &s->tcp);
            }
        } else {
            log_print(PLATFORM_LOG_PRIORITY_WARN, "%s unknown session", packet);

            struct tcp_session rst;
            memset(&rst, 0, sizeof(struct tcp_session));
            rst.version = version;
            rst.local_seq = ntohl(tcphdr->ack_seq);
            rst.remote_seq = ntohl(tcphdr->seq) + datalen + (tcphdr->syn || tcphdr->fin ? 1 : 0);

            if (version == 4) {
                rst.saddr.ip4 = (__be32) ip4->saddr;
                rst.daddr.ip4 = (__be32) ip4->daddr;
            } else {
                memcpy(&rst.saddr.ip6, &ip6->ip6_src, 16);
                memcpy(&rst.daddr.ip6, &ip6->ip6_dst, 16);
            }

            rst.source = tcphdr->source;
            rst.dest = tcphdr->dest;

            write_rst(args, &rst);
            return 0;
        }
    } else {
        char session[250];
        sprintf(session,
                "%s %s loc %u rem %u acked %u",
                packet,
                strstate(cur->tcp.state),
                cur->tcp.local_seq - cur->tcp.local_start,
                cur->tcp.remote_seq - cur->tcp.remote_start,
                cur->tcp.acked - cur->tcp.local_start);

        // Session found
        if (cur->tcp.state == TCP_CLOSING || cur->tcp.state == TCP_CLOSE) {
            log_print(PLATFORM_LOG_PRIORITY_WARN, "%s was closed", session);
            write_rst(args, &cur->tcp);
            return 0;
        } else {
            int oldstate = cur->tcp.state;
            uint32_t oldlocal = cur->tcp.local_seq;
            uint32_t oldremote = cur->tcp.remote_seq;

            log_print(PLATFORM_LOG_PRIORITY_DEBUG, "%s handling", session);

            if (!tcphdr->syn)
                cur->tcp.time = time(NULL);
            cur->tcp.send_window = ((uint32_t) ntohs(tcphdr->window)) << cur->tcp.send_scale;
            cur->tcp.unconfirmed = 0;

            // Do not change the order of the conditions

            // Queue data to forward
            if (datalen) {
                if (cur->socket < 0) {
                    log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s data while local closed", session);
                    write_rst(args, &cur->tcp);
                    return 0;
                }
                if (cur->tcp.state == TCP_CLOSE_WAIT) {
                    log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s data while remote closed", session);
                    write_rst(args, &cur->tcp);
                    return 0;
                }
                queue_tcp(args, tcphdr, session, &cur->tcp, data, datalen);
            }

            if (tcphdr->rst /* +ACK */) {
                // No sequence check
                // http://tools.ietf.org/html/rfc1122#page-87
                log_print(PLATFORM_LOG_PRIORITY_WARN, "%s received reset", session);
                cur->tcp.state = TCP_CLOSING;
                return 0;
            } else {
                if (!tcphdr->ack || ntohl(tcphdr->ack_seq) == cur->tcp.local_seq) {
                    if (tcphdr->syn) {
                        log_print(PLATFORM_LOG_PRIORITY_WARN, "%s repeated SYN", session);
                        // The socket is probably not opened yet

                    } else if (tcphdr->fin /* +ACK */) {
                        if (cur->tcp.state == TCP_ESTABLISHED) {
                            log_print(PLATFORM_LOG_PRIORITY_WARN, "%s FIN received", session);
                            if (cur->tcp.forward == NULL) {
                                cur->tcp.remote_seq++; // remote FIN
                                if (write_ack(args, &cur->tcp) >= 0)
                                    cur->tcp.state = TCP_CLOSE_WAIT;
                            } else
                                cur->tcp.state = TCP_CLOSE_WAIT;
                        } else if (cur->tcp.state == TCP_CLOSE_WAIT) {
                            log_print(PLATFORM_LOG_PRIORITY_WARN, "%s repeated FIN", session);
                            // The socket is probably not closed yet
                        } else if (cur->tcp.state == TCP_FIN_WAIT1) {
                            log_print(PLATFORM_LOG_PRIORITY_WARN, "%s last ACK", session);
                            cur->tcp.remote_seq++; // remote FIN
                            if (write_ack(args, &cur->tcp) >= 0)
                                cur->tcp.state = TCP_CLOSE;
                        } else {
                            log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s invalid FIN", session);
                            return 0;
                        }

                    } else if (tcphdr->ack) {
                        cur->tcp.acked = ntohl(tcphdr->ack_seq);

                        if (cur->tcp.state == TCP_SYN_RECV)
                            cur->tcp.state = TCP_ESTABLISHED;

                        else if (cur->tcp.state == TCP_ESTABLISHED) {
                            // Do nothing
                        } else if (cur->tcp.state == TCP_LAST_ACK)
                            cur->tcp.state = TCP_CLOSING;

                        else if (cur->tcp.state == TCP_CLOSE_WAIT) {
                            // ACK after FIN/ACK
                        } else if (cur->tcp.state == TCP_FIN_WAIT1) {
                            // Do nothing
                        } else {
                            log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s invalid state", session);
                            return 0;
                        }
                    } else {
                        log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s unknown packet", session);
                        return 0;
                    }
                } else {
                    uint32_t ack = ntohl(tcphdr->ack_seq);
                    if ((uint32_t) (ack + 1) == cur->tcp.local_seq) {
                        // Keep alive
                        if (cur->tcp.state == TCP_ESTABLISHED) {
                            int on = 1;
                            if (setsockopt(cur->socket, SOL_SOCKET, SO_KEEPALIVE, &on, sizeof(on)))
                                log_print(PLATFORM_LOG_PRIORITY_ERROR,
                                            "%s setsockopt SO_KEEPALIVE error %d: %s",
                                            session, errno, strerror(errno));
                            else
                                log_print(PLATFORM_LOG_PRIORITY_WARN, "%s enabled keep alive", session);
                        } else
                            log_print(PLATFORM_LOG_PRIORITY_WARN, "%s keep alive", session);

                    } else if (compare_u32(ack, cur->tcp.local_seq) < 0) {
                        if (compare_u32(ack, cur->tcp.acked) <= 0)
                            log_print(
                                    ack == cur->tcp.acked ? PLATFORM_LOG_PRIORITY_WARN : PLATFORM_LOG_PRIORITY_ERROR,
                                    "%s repeated ACK %u/%u",
                                    session,
                                    ack - cur->tcp.local_start,
                                    cur->tcp.acked - cur->tcp.local_start);
                        else {
                            log_print(PLATFORM_LOG_PRIORITY_WARN, "%s previous ACK %u",
                                        session, ack - cur->tcp.local_seq);
                            cur->tcp.acked = ack;
                        }

                        return 1;
                    } else {
                        log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s future ACK", session);
                        write_rst(args, &cur->tcp);
                        return 0;
                    }
                }
            }

            if (cur->tcp.state != oldstate ||
                cur->tcp.local_seq != oldlocal ||
                cur->tcp.remote_seq != oldremote)
                log_print(PLATFORM_LOG_PRIORITY_INFO, "%s > %s loc %u rem %u",
                            session,
                            strstate(cur->tcp.state),
                            cur->tcp.local_seq - cur->tcp.local_start,
                            cur->tcp.remote_seq - cur->tcp.remote_start);
        }
    }

    return 1;
}

static void queue_tcp(const struct arguments *args,
               const struct tcphdr *tcphdr,
               const char *session, struct tcp_session *cur,
               const uint8_t *data, uint16_t datalen) {
    uint32_t seq = ntohl(tcphdr->seq);
    if (compare_u32(seq, cur->remote_seq) < 0)
        log_print(PLATFORM_LOG_PRIORITY_WARN, "%s already forwarded %u..%u",
                    session,
                    seq - cur->remote_start, seq + datalen - cur->remote_start);
    else {
        struct segment *p = NULL;
        struct segment *s = cur->forward;
        while (s != NULL && compare_u32(s->seq, seq) < 0) {
            p = s;
            s = s->next;
        }

        if (s == NULL || compare_u32(s->seq, seq) > 0) {
            log_print(PLATFORM_LOG_PRIORITY_DEBUG, "%s queuing %u...%u",
                        session,
                        seq - cur->remote_start, seq + datalen - cur->remote_start);
            struct segment *n = ng_malloc(sizeof(struct segment), "tcp segment");
            n->seq = seq;
            n->len = datalen;
            n->sent = 0;
            n->psh = tcphdr->psh;
            n->data = ng_malloc(datalen, "tcp segment");
            memcpy(n->data, data, datalen);
            n->next = s;
            if (p == NULL)
                cur->forward = n;
            else
                p->next = n;
        } else if (s != NULL && s->seq == seq) {
            if (s->len == datalen)
                log_print(PLATFORM_LOG_PRIORITY_WARN, "%s segment already queued %u..%u",
                            session,
                            s->seq - cur->remote_start, s->seq + s->len - cur->remote_start);
            else if (s->len < datalen) {
                log_print(PLATFORM_LOG_PRIORITY_WARN, "%s segment smaller %u..%u > %u",
                            session,
                            s->seq - cur->remote_start, s->seq + s->len - cur->remote_start,
                            s->seq + datalen - cur->remote_start);
                ng_free(s->data, __FILE__, __LINE__);
                s->len = datalen;
                s->data = ng_malloc(datalen, "tcp segment smaller");
                memcpy(s->data, data, datalen);
            } else {
                log_print(PLATFORM_LOG_PRIORITY_ERROR, "%s segment larger %u..%u < %u",
                            session,
                            s->seq - cur->remote_start, s->seq + s->len - cur->remote_start,
                            s->seq + datalen - cur->remote_start);
                ng_free(s->data, __FILE__, __LINE__);
                s->len = datalen;
                s->data = ng_malloc(datalen, "tcp segment larger");
                memcpy(s->data, data, datalen);
            }
        }
    }
}

static int open_tcp_socket(const struct arguments *args,
                    const struct tcp_session *cur, const struct allowed *redirect) {
    int sock;
    int version;
    if (redirect == NULL) {
        if (*socks5_addr && socks5_port)
            version = (strstr(socks5_addr, ":") == NULL ? 4 : 6);
        else
            version = cur->version;
    } else
        version = (strstr(redirect->raddr, ":") == NULL ? 4 : 6);

    // Get TCP socket
    if ((sock = socket(version == 4 ? PF_INET : PF_INET6, SOCK_STREAM, 0)) < 0) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "socket error %d: %s", errno, strerror(errno));
        return -1;
    }

    // Protect
    if (protect_socket(args, sock) < 0)
        return -1;

    int on = 1;
    if (setsockopt(sock, SOL_TCP, TCP_NODELAY, &on, sizeof(on)) < 0)
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "setsockopt TCP_NODELAY error %d: %s",
                    errno, strerror(errno));

    // Set non blocking
    int flags = fcntl(sock, F_GETFL, 0);
    if (flags < 0 || fcntl(sock, F_SETFL, flags | O_NONBLOCK) < 0) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "fcntl socket O_NONBLOCK error %d: %s",
                    errno, strerror(errno));
        return -1;
    }

    // Build target address
    struct sockaddr_in addr4;
    struct sockaddr_in6 addr6;
    if (redirect == NULL) {
        if (*socks5_addr && socks5_port) {
            log_print(PLATFORM_LOG_PRIORITY_WARN, "TCP%d SOCKS5 to %s/%u",
                        version, socks5_addr, socks5_port);

            if (version == 4) {
                addr4.sin_family = AF_INET;
                inet_pton(AF_INET, socks5_addr, &addr4.sin_addr);
                addr4.sin_port = htons(socks5_port);
            } else {
                addr6.sin6_family = AF_INET6;
                inet_pton(AF_INET6, socks5_addr, &addr6.sin6_addr);
                addr6.sin6_port = htons(socks5_port);
            }
        } else {
            if (version == 4) {
                addr4.sin_family = AF_INET;
                addr4.sin_addr.s_addr = (__be32) cur->daddr.ip4;
                addr4.sin_port = cur->dest;
            } else {
                addr6.sin6_family = AF_INET6;
                memcpy(&addr6.sin6_addr, &cur->daddr.ip6, 16);
                addr6.sin6_port = cur->dest;
            }
        }
    } else {
        log_print(PLATFORM_LOG_PRIORITY_WARN, "TCP%d redirect to %s/%u",
                    version, redirect->raddr, redirect->rport);

        if (version == 4) {
            addr4.sin_family = AF_INET;
            inet_pton(AF_INET, redirect->raddr, &addr4.sin_addr);
            addr4.sin_port = htons(redirect->rport);
        } else {
            addr6.sin6_family = AF_INET6;
            inet_pton(AF_INET6, redirect->raddr, &addr6.sin6_addr);
            addr6.sin6_port = htons(redirect->rport);
        }
    }

    // Initiate connect
    int err = connect(sock,
                      (version == 4 ? (const struct sockaddr *) &addr4
                                    : (const struct sockaddr *) &addr6),
                      (socklen_t) (version == 4
                                   ? sizeof(struct sockaddr_in)
                                   : sizeof(struct sockaddr_in6)));
    if (err < 0 && errno != EINPROGRESS) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "connect error %d: %s", errno, strerror(errno));
        return -1;
    }

    return sock;
}

static int write_syn_ack(const struct arguments *args, struct tcp_session *cur) {
    if (write_tcp(args, cur, NULL, 0, 1, 1, 0, 0) < 0) {
        cur->state = TCP_CLOSING;
        return -1;
    }
    return 0;
}

static int write_ack(const struct arguments *args, struct tcp_session *cur) {
    if (write_tcp(args, cur, NULL, 0, 0, 1, 0, 0) < 0) {
        cur->state = TCP_CLOSING;
        return -1;
    }
    return 0;
}

static int write_data(const struct arguments *args, struct tcp_session *cur,
               const uint8_t *buffer, size_t length) {
    if (write_tcp(args, cur, buffer, length, 0, 1, 0, 0) < 0) {
        cur->state = TCP_CLOSING;
        return -1;
    }
    return 0;
}

static int write_fin_ack(const struct arguments *args, struct tcp_session *cur) {
    if (write_tcp(args, cur, NULL, 0, 0, 1, 1, 0) < 0) {
        cur->state = TCP_CLOSING;
        return -1;
    }
    return 0;
}

void write_rst(const struct arguments *args, struct tcp_session *cur) {
    // https://www.snellman.net/blog/archive/2016-02-01-tcp-rst/
    int ack = 0;
    if (cur->state == TCP_LISTEN) {
        ack = 1;
        cur->remote_seq++; // SYN
    }
    write_tcp(args, cur, NULL, 0, 0, ack, 0, 1);
    if (cur->state != TCP_CLOSE)
        cur->state = TCP_CLOSING;
}

static ssize_t write_tcp(const struct arguments *args, const struct tcp_session *cur,
                  const uint8_t *data, size_t datalen,
                  int syn, int ack, int fin, int rst) {
    size_t len;
    uint8_t *buffer;
    struct tcphdr *tcp;
    uint16_t csum;
    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];

    // Build packet
    int optlen = (syn ? 4 + 3 + 1 : 0);
    uint8_t *options;
    if (cur->version == 4) {
        len = sizeof(struct iphdr) + sizeof(struct tcphdr) + optlen + datalen;
        buffer = ng_malloc(len, "tcp write4");
        struct iphdr *ip4 = (struct iphdr *) buffer;
        tcp = (struct tcphdr *) (buffer + sizeof(struct iphdr));
        options = buffer + sizeof(struct iphdr) + sizeof(struct tcphdr);
        if (datalen)
            memcpy(buffer + sizeof(struct iphdr) + sizeof(struct tcphdr) + optlen, data, datalen);

        // Build IP4 header
        memset(ip4, 0, sizeof(struct iphdr));
        ip4->version = 4;
        ip4->ihl = sizeof(struct iphdr) >> 2;
        ip4->tot_len = htons(len);
        ip4->ttl = IPDEFTTL;
        ip4->protocol = IPPROTO_TCP;
        ip4->saddr = cur->daddr.ip4;
        ip4->daddr = cur->saddr.ip4;

        // Calculate IP4 checksum
        ip4->check = ~calc_checksum(0, (uint8_t *) ip4, sizeof(struct iphdr));

        // Calculate TCP4 checksum
        struct ippseudo pseudo;
        memset(&pseudo, 0, sizeof(struct ippseudo));
        pseudo.ippseudo_src.s_addr = (__be32) ip4->saddr;
        pseudo.ippseudo_dst.s_addr = (__be32) ip4->daddr;
        pseudo.ippseudo_p = ip4->protocol;
        pseudo.ippseudo_len = htons(sizeof(struct tcphdr) + optlen + datalen);

        csum = calc_checksum(0, (uint8_t *) &pseudo, sizeof(struct ippseudo));
    } else {
        len = sizeof(struct ip6_hdr) + sizeof(struct tcphdr) + optlen + datalen;
        buffer = ng_malloc(len, "tcp write 6");
        struct ip6_hdr *ip6 = (struct ip6_hdr *) buffer;
        tcp = (struct tcphdr *) (buffer + sizeof(struct ip6_hdr));
        options = buffer + sizeof(struct ip6_hdr) + sizeof(struct tcphdr);
        if (datalen)
            memcpy(buffer + sizeof(struct ip6_hdr) + sizeof(struct tcphdr) + optlen, data, datalen);

        // Build IP6 header
        memset(ip6, 0, sizeof(struct ip6_hdr));
        ip6->ip6_ctlun.ip6_un1.ip6_un1_plen = htons(len - sizeof(struct ip6_hdr));
        ip6->ip6_ctlun.ip6_un1.ip6_un1_nxt = IPPROTO_TCP;
        ip6->ip6_ctlun.ip6_un1.ip6_un1_hlim = IPDEFTTL;
        ip6->ip6_ctlun.ip6_un2_vfc = 0x60;
        memcpy(&(ip6->ip6_src), &cur->daddr.ip6, 16);
        memcpy(&(ip6->ip6_dst), &cur->saddr.ip6, 16);

        // Calculate TCP6 checksum
        struct ip6_hdr_pseudo pseudo;
        memset(&pseudo, 0, sizeof(struct ip6_hdr_pseudo));
        memcpy(&pseudo.ip6ph_src, &ip6->ip6_dst, 16);
        memcpy(&pseudo.ip6ph_dst, &ip6->ip6_src, 16);
        pseudo.ip6ph_len = ip6->ip6_ctlun.ip6_un1.ip6_un1_plen;
        pseudo.ip6ph_nxt = ip6->ip6_ctlun.ip6_un1.ip6_un1_nxt;

        csum = calc_checksum(0, (uint8_t *) &pseudo, sizeof(struct ip6_hdr_pseudo));
    }


    // Build TCP header
    memset(tcp, 0, sizeof(struct tcphdr));
    tcp->source = cur->dest;
    tcp->dest = cur->source;
    tcp->seq = htonl(cur->local_seq);
    tcp->ack_seq = htonl((uint32_t) (cur->remote_seq));
    tcp->doff = (__u16) ((sizeof(struct tcphdr) + optlen) >> 2);
    tcp->syn = (__u16) syn;
    tcp->ack = (__u16) ack;
    tcp->fin = (__u16) fin;
    tcp->rst = (__u16) rst;
    tcp->window = htons(cur->recv_window >> cur->recv_scale);

    if (!tcp->ack)
        tcp->ack_seq = 0;

    // TCP options
    if (syn) {
        *(options) = 2; // MSS
        *(options + 1) = 4; // total option length
        *((uint16_t *) (options + 2)) = get_default_mss(cur->version);

        *(options + 4) = 3; // window scale
        *(options + 5) = 3; // total option length
        *(options + 6) = cur->recv_scale;

        *(options + 7) = 0; // End, padding
    }

    // Continue checksum
    csum = calc_checksum(csum, (uint8_t *) tcp, sizeof(struct tcphdr));
    csum = calc_checksum(csum, options, (size_t) optlen);
    csum = calc_checksum(csum, data, datalen);
    tcp->check = ~csum;

    inet_ntop(cur->version == 4 ? AF_INET : AF_INET6,
              cur->version == 4 ? (const void *) &cur->saddr.ip4 : (const void *) &cur->saddr.ip6,
              source, sizeof(source));
    inet_ntop(cur->version == 4 ? AF_INET : AF_INET6,
              cur->version == 4 ? (const void *) &cur->daddr.ip4 : (const void *) &cur->daddr.ip6,
              dest, sizeof(dest));

    // Send packet
    log_print(PLATFORM_LOG_PRIORITY_DEBUG,
                "TCP sending%s%s%s%s to tun %s/%u seq %u ack %u data %u",
                (tcp->syn ? " SYN" : ""),
                (tcp->ack ? " ACK" : ""),
                (tcp->fin ? " FIN" : ""),
                (tcp->rst ? " RST" : ""),
                dest, ntohs(tcp->dest),
                ntohl(tcp->seq) - cur->local_start,
                ntohl(tcp->ack_seq) - cur->remote_start,
                datalen);

    ssize_t res = write(args->tun, buffer, len);

    // Write pcap record
    if (res >= 0) {
        if (pcap_file != NULL)
            write_pcap_rec(buffer, (size_t) res);
    } else
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "TCP write%s%s%s%s data %d error %d: %s",
                    (tcp->syn ? " SYN" : ""),
                    (tcp->ack ? " ACK" : ""),
                    (tcp->fin ? " FIN" : ""),
                    (tcp->rst ? " RST" : ""),
                    datalen,
                    errno, strerror((errno)));

    ng_free(buffer, __FILE__, __LINE__);

    if (res != len) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "TCP write %d/%d", res, len);
        return -1;
    }

    return res;
}
