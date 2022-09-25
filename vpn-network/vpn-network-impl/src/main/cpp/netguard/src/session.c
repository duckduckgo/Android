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

#define EPOLL_TIMEOUT 3600 // seconds
#define EPOLL_EVENTS 20

#define TUN_YIELD 10 // packets
#define UDP_YIELD 10 // packets

#define SESSION_LIMIT 40 // percent
#define SESSION_MAX (1024 * SESSION_LIMIT / 100) // number

void check_allowed(const struct arguments *args);

///////////////////////////////////////////////////////////////////////////////

void clear(struct context *ctx) {
    struct ng_session *s = ctx->ng_session;
    while (s != NULL) {
        if (s->socket >= 0 && close(s->socket))
            log_print(PLATFORM_LOG_PRIORITY_ERROR, "close %d error %d: %s",
                        s->socket, errno, strerror(errno));
        if (s->protocol == IPPROTO_TCP)
            clear_tcp_data(&s->tcp);
        struct ng_session *p = s;
        s = s->next;
        ng_free(p, __FILE__, __LINE__);
    }
    ctx->ng_session = NULL;
}

void *handle_events(void *a) {
    struct arguments *args = (struct arguments *) a;
    log_print(PLATFORM_LOG_PRIORITY_WARN, "Start events tun=%d", args->tun);

    // Get max number of sessions
    int maxsessions = SESSION_MAX;
    struct rlimit rlim;
    if (getrlimit(RLIMIT_NOFILE, &rlim))
        log_print(PLATFORM_LOG_PRIORITY_WARN, "getrlimit error %d: %s", errno, strerror(errno));
    else {
        maxsessions = (int) (rlim.rlim_cur * SESSION_LIMIT / 100);
        if (maxsessions > SESSION_MAX)
            maxsessions = SESSION_MAX;
        log_print(PLATFORM_LOG_PRIORITY_WARN, "getrlimit soft %d hard %d max sessions %d",
                    rlim.rlim_cur, rlim.rlim_max, maxsessions);
    }

    // Terminate existing sessions not allowed anymore
    check_allowed(args);

    // Open epoll file
    int epoll_fd = epoll_create(1);
    if (epoll_fd < 0) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "epoll create error %d: %s", errno, strerror(errno));
        report_exit(args, "epoll create error %d: %s", errno, strerror(errno));
        args->ctx->stopping = 1;
    }

    // Monitor stop events
    struct epoll_event ev_pipe;
    memset(&ev_pipe, 0, sizeof(struct epoll_event));
    ev_pipe.events = EPOLLIN | EPOLLERR;
    ev_pipe.data.ptr = &ev_pipe;
    if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, args->ctx->pipefds[0], &ev_pipe)) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "epoll add pipe error %d: %s", errno, strerror(errno));
        report_exit(args, "epoll add pipe error %d: %s", errno, strerror(errno));
        args->ctx->stopping = 1;
    }

    // Monitor tun events
    struct epoll_event ev_tun;
    memset(&ev_tun, 0, sizeof(struct epoll_event));
    ev_tun.events = EPOLLIN | EPOLLERR;
    ev_tun.data.ptr = NULL;
    if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, args->tun, &ev_tun)) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "epoll add tun error %d: %s", errno, strerror(errno));
        report_exit(args, "epoll add tun error %d: %s", errno, strerror(errno));
        args->ctx->stopping = 1;
    }

    // Loop
    long long last_check = 0;
    while (!args->ctx->stopping) {
        log_print(PLATFORM_LOG_PRIORITY_DEBUG, "Loop");

        int recheck = 0;
        int timeout = EPOLL_TIMEOUT;

        // Count sessions
        int isessions = 0;
        int usessions = 0;
        int tsessions = 0;
        struct ng_session *s = args->ctx->ng_session;
        while (s != NULL) {
            if (s->protocol == IPPROTO_ICMP || s->protocol == IPPROTO_ICMPV6) {
                if (!s->icmp.stop)
                    isessions++;
            } else if (s->protocol == IPPROTO_UDP) {
                if (s->udp.state == UDP_ACTIVE)
                    usessions++;
            } else if (s->protocol == IPPROTO_TCP) {
                if (s->tcp.state != TCP_CLOSING && s->tcp.state != TCP_CLOSE)
                    tsessions++;
                if (s->socket >= 0)
                    recheck = recheck | monitor_tcp_session(args, s, epoll_fd);
            }
            s = s->next;
        }
        int sessions = isessions + usessions + tsessions;

        // Check sessions
        long long ms = get_ms();
        if (ms - last_check > EPOLL_MIN_CHECK) {
            last_check = ms;

            time_t now = time(NULL);
            struct ng_session *sl = NULL;
            s = args->ctx->ng_session;
            while (s != NULL) {
                int del = 0;
                if (s->protocol == IPPROTO_ICMP || s->protocol == IPPROTO_ICMPV6) {
                    del = check_icmp_session(args, s, sessions, maxsessions);
                    if (!s->icmp.stop && !del) {
                        int stimeout = s->icmp.time +
                                       get_icmp_timeout(&s->icmp, sessions, maxsessions) - now + 1;
                        if (stimeout > 0 && stimeout < timeout)
                            timeout = stimeout;
                    }
                } else if (s->protocol == IPPROTO_UDP) {
                    del = check_udp_session(args, s, sessions, maxsessions);
                    if (s->udp.state == UDP_ACTIVE && !del) {
                        int stimeout = s->udp.time +
                                       get_udp_timeout(&s->udp, sessions, maxsessions) - now + 1;
                        if (stimeout > 0 && stimeout < timeout)
                            timeout = stimeout;
                    }
                } else if (s->protocol == IPPROTO_TCP) {
                    del = check_tcp_session(args, s, sessions, maxsessions);
                    if (s->tcp.state != TCP_CLOSING && s->tcp.state != TCP_CLOSE && !del) {
                        int stimeout = s->tcp.time +
                                       get_tcp_timeout(&s->tcp, sessions, maxsessions) - now + 1;
                        if (stimeout > 0 && stimeout < timeout)
                            timeout = stimeout;
                    }
                }

                if (del) {
                    if (sl == NULL)
                        args->ctx->ng_session = s->next;
                    else
                        sl->next = s->next;

                    struct ng_session *c = s;
                    s = s->next;
                    if (c->protocol == IPPROTO_TCP)
                        clear_tcp_data(&c->tcp);
                    ng_free(c, __FILE__, __LINE__);
                } else {
                    sl = s;
                    s = s->next;
                }
            }
        } else {
            recheck = 1;
            log_print(PLATFORM_LOG_PRIORITY_DEBUG, "Skipped session checks");
        }

        log_print(PLATFORM_LOG_PRIORITY_DEBUG,
                    "sessions ICMP %d UDP %d TCP %d max %d/%d timeout %d recheck %d",
                    isessions, usessions, tsessions, sessions, maxsessions, timeout, recheck);

        // Poll
        struct epoll_event ev[EPOLL_EVENTS];
        int ready = epoll_wait(epoll_fd, ev, EPOLL_EVENTS,
                               recheck ? EPOLL_MIN_CHECK : timeout * 1000);

        if (ready < 0) {
            if (errno == EINTR) {
                log_print(PLATFORM_LOG_PRIORITY_DEBUG, "epoll interrupted tun %d", args->tun);
                continue;
            } else {
                log_print(PLATFORM_LOG_PRIORITY_ERROR,
                            "epoll tun %d error %d: %s",
                            args->tun, errno, strerror(errno));
                report_exit(args, "epoll tun %d error %d: %s",
                            args->tun, errno, strerror(errno));
                break;
            }
        }

        if (ready == 0)
            log_print(PLATFORM_LOG_PRIORITY_DEBUG, "epoll timeout");
        else {

            if (pthread_mutex_lock(&args->ctx->lock))
                log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_lock failed");

            int error = 0;

            for (int i = 0; i < ready; i++) {
                if (ev[i].data.ptr == &ev_pipe) {
                    // Check pipe
                    uint8_t buffer[1];
                    if (read(args->ctx->pipefds[0], buffer, 1) < 0)
                        log_print(PLATFORM_LOG_PRIORITY_WARN, "Read pipe error %d: %s",
                                    errno, strerror(errno));
                    else
                        log_print(PLATFORM_LOG_PRIORITY_WARN, "Read pipe");

                } else if (ev[i].data.ptr == NULL) {
                    // Check upstream
                    log_print(PLATFORM_LOG_PRIORITY_DEBUG, "epoll ready %d/%d in %d out %d err %d hup %d",
                                i, ready,
                                (ev[i].events & EPOLLIN) != 0,
                                (ev[i].events & EPOLLOUT) != 0,
                                (ev[i].events & EPOLLERR) != 0,
                                (ev[i].events & EPOLLHUP) != 0);

                    int count = 0;
                    while (count < TUN_YIELD && !error && !args->ctx->stopping &&
                           is_readable(args->tun)) {
                        count++;
                        if (check_tun(args, &ev[i], epoll_fd, sessions, maxsessions) < 0)
                            error = 1;
                    }

                } else {
                    // Check downstream
                    log_print(PLATFORM_LOG_PRIORITY_DEBUG,
                                "epoll ready %d/%d in %d out %d err %d hup %d prot %d sock %d",
                                i, ready,
                                (ev[i].events & EPOLLIN) != 0,
                                (ev[i].events & EPOLLOUT) != 0,
                                (ev[i].events & EPOLLERR) != 0,
                                (ev[i].events & EPOLLHUP) != 0,
                                ((struct ng_session *) ev[i].data.ptr)->protocol,
                                ((struct ng_session *) ev[i].data.ptr)->socket);

                    struct ng_session *session = (struct ng_session *) ev[i].data.ptr;
                    if (session->protocol == IPPROTO_ICMP ||
                        session->protocol == IPPROTO_ICMPV6)
                        check_icmp_socket(args, &ev[i]);
                    else if (session->protocol == IPPROTO_UDP) {
                        int count = 0;
                        while (count < UDP_YIELD && !args->ctx->stopping &&
                               !(ev[i].events & EPOLLERR) && (ev[i].events & EPOLLIN) &&
                               is_readable(session->socket)) {
                            count++;
                            check_udp_socket(args, &ev[i]);
                        }
                    } else if (session->protocol == IPPROTO_TCP)
                        check_tcp_socket(args, &ev[i], epoll_fd);
                }

                if (error)
                    break;
            }

            if (pthread_mutex_unlock(&args->ctx->lock))
                log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_unlock failed");

            if (error)
                break;
        }
    }

    // Close epoll file
    if (epoll_fd >= 0 && close(epoll_fd))
        log_print(PLATFORM_LOG_PRIORITY_ERROR,
                    "epoll close error %d: %s", errno, strerror(errno));

    // Cleanup
    ng_free(args, __FILE__, __LINE__);

    log_print(PLATFORM_LOG_PRIORITY_WARN, "Stopped events tun=%d", args->tun);
    return NULL;
}

void check_allowed(const struct arguments *args) {
    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];

    struct ng_session *l = NULL;
    struct ng_session *s = args->ctx->ng_session;
    while (s != NULL) {
        if (s->protocol == IPPROTO_ICMP || s->protocol == IPPROTO_ICMPV6) {
            if (!s->icmp.stop) {
                if (s->icmp.version == 4) {
                    inet_ntop(AF_INET, &s->icmp.saddr.ip4, source, sizeof(source));
                    inet_ntop(AF_INET, &s->icmp.daddr.ip4, dest, sizeof(dest));
                } else {
                    inet_ntop(AF_INET6, &s->icmp.saddr.ip6, source, sizeof(source));
                    inet_ntop(AF_INET6, &s->icmp.daddr.ip6, dest, sizeof(dest));
                }

                packet_t packet;
                packet.version = s->icmp.version;
                packet.protocol = IPPROTO_ICMP;
                packet.flags = "";
                packet.source = source;
                packet.sport = 0;
                packet.dest = dest;
                packet.dport = 0;
                packet.data = "";
                packet.uid = s->icmp.uid;
                packet.allowed = 0;
                if (is_address_allowed(args, &packet) == NULL) {
                    s->icmp.stop = 1;
                    log_print(PLATFORM_LOG_PRIORITY_WARN, "ICMP terminate %d uid %d",
                                s->socket, s->icmp.uid);
                }
            }

        } else if (s->protocol == IPPROTO_UDP) {
            if (s->udp.state == UDP_ACTIVE) {
                if (s->udp.version == 4) {
                    inet_ntop(AF_INET, &s->udp.saddr.ip4, source, sizeof(source));
                    inet_ntop(AF_INET, &s->udp.daddr.ip4, dest, sizeof(dest));
                } else {
                    inet_ntop(AF_INET6, &s->udp.saddr.ip6, source, sizeof(source));
                    inet_ntop(AF_INET6, &s->udp.daddr.ip6, dest, sizeof(dest));
                }

                packet_t packet;
                packet.version = s->udp.version;
                packet.protocol = IPPROTO_UDP;
                packet.flags = "";
                packet.source = source;
                packet.sport = ntohs(s->udp.source);
                packet.dest = dest;
                packet.dport = ntohs(s->udp.dest);
                packet.data = "";
                packet.uid = s->udp.uid;
                packet.allowed = 0;
                if (is_address_allowed(args, &packet) == NULL) {
                    s->udp.state = UDP_FINISHING;
                    log_print(PLATFORM_LOG_PRIORITY_WARN, "UDP terminate session socket %d uid %d",
                                s->socket, s->udp.uid);
                }
            } else if (s->udp.state == UDP_BLOCKED) {
                log_print(PLATFORM_LOG_PRIORITY_WARN, "UDP remove blocked session uid %d", s->udp.uid);

                if (l == NULL)
                    args->ctx->ng_session = s->next;
                else
                    l->next = s->next;

                struct ng_session *c = s;
                s = s->next;
                ng_free(c, __FILE__, __LINE__);
                continue;
            }

        } else if (s->protocol == IPPROTO_TCP) {
            if (s->tcp.state != TCP_CLOSING && s->tcp.state != TCP_CLOSE) {
                if (s->tcp.version == 4) {
                    inet_ntop(AF_INET, &s->tcp.saddr.ip4, source, sizeof(source));
                    inet_ntop(AF_INET, &s->tcp.daddr.ip4, dest, sizeof(dest));
                } else {
                    inet_ntop(AF_INET6, &s->tcp.saddr.ip6, source, sizeof(source));
                    inet_ntop(AF_INET6, &s->tcp.daddr.ip6, dest, sizeof(dest));
                }

                packet_t packet;
                packet.version = s->tcp.version;
                packet.protocol = IPPROTO_TCP;
                packet.flags = "";
                packet.source = source;
                packet.sport = ntohs(s->tcp.source);
                packet.dest = dest;
                packet.dport = ntohs(s->tcp.dest);
                packet.data = "";
                packet.uid = s->tcp.uid;
                packet.allowed = 0;
                if (is_address_allowed(args, &packet) == NULL) {
                    write_rst(args, &s->tcp);
                    log_print(PLATFORM_LOG_PRIORITY_WARN, "TCP terminate socket %d uid %d",
                                s->socket, s->tcp.uid);
                }
            }

        }

        l = s;
        s = s->next;
    }
}

