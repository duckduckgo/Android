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

#define DNS_QCLASS_IN 1
#define DNS_QTYPE_A 1 // IPv4
#define DNS_QTYPE_AAAA 28 // IPv6

#define DNS_SVCB 64
#define DNS_HTTPS 65

#define DNS_QNAME_MAX 255
#define DNS_TTL (10 * 60) // seconds

typedef struct dns_rr {
    __be16 qname_ptr;
    __be16 qtype;
    __be16 qclass;
    __be32 ttl;
    __be16 rdlength;
} __packed dns_rr;

struct dns_header {
    uint16_t id; // identification number
# if __BYTE_ORDER == __LITTLE_ENDIAN
    uint16_t rd :1; // recursion desired
    uint16_t tc :1; // truncated message
    uint16_t aa :1; // authoritive answer
    uint16_t opcode :4; // purpose of message
    uint16_t qr :1; // query/response flag
    uint16_t rcode :4; // response code
    uint16_t cd :1; // checking disabled
    uint16_t ad :1; // authenticated data
    uint16_t z :1; // its z! reserved
    uint16_t ra :1; // recursion available
#elif __BYTE_ORDER == __BIG_ENDIAN
    uint16_t qr :1; // query/response flag
    uint16_t opcode :4; // purpose of message
    uint16_t aa :1; // authoritive answer
    uint16_t tc :1; // truncated message
    uint16_t rd :1; // recursion desired
    uint16_t ra :1; // recursion available
    uint16_t z :1; // its z! reserved
    uint16_t ad :1; // authenticated data
    uint16_t cd :1; // checking disabled
    uint16_t rcode :4; // response code
# else
# error "Adjust your <bits/endian.h> defines"
#endif
    uint16_t q_count; // number of question entries
    uint16_t ans_count; // number of answer entries
    uint16_t auth_count; // number of authority entries
    uint16_t add_count; // number of resource entries
} __packed;

static int32_t get_qname(const uint8_t *data, const size_t datalen, uint16_t off, char *qname);

///////////////////////////////////////////////////////////////////////////////

static int32_t get_qname(const uint8_t *data, const size_t datalen, uint16_t off, char *qname) {
    *qname = 0;

    if (off >= datalen)
        return -1;

    uint16_t c = 0;
    uint8_t noff = 0;
    uint16_t ptr = off;
    uint8_t len = *(data + ptr);
    uint8_t count = 0;
    while (len) {
        if (count++ > 25)
            break;

        if (ptr + 1 < datalen && (len & 0xC0)) {
            uint16_t jump = (uint16_t) ((len & 0x3F) * 256 + *(data + ptr + 1));
            if (jump >= datalen) {
                log_print(PLATFORM_LOG_PRIORITY_DEBUG, "DNS invalid jump");
                break;
            }
            ptr = jump;
            len = *(data + ptr);
            log_print(PLATFORM_LOG_PRIORITY_DEBUG, "DNS qname compression ptr %d len %d", ptr, len);
            if (!c) {
                c = 1;
                off += 2;
            }
        } else if (ptr + 1 + len < datalen && noff + len <= DNS_QNAME_MAX) {
            memcpy(qname + noff, data + ptr + 1, len);
            *(qname + noff + len) = '.';
            noff += (len + 1);

            uint16_t jump = (uint16_t) (ptr + 1 + len);
            if (jump >= datalen) {
                log_print(PLATFORM_LOG_PRIORITY_DEBUG, "DNS invalid jump");
                break;
            }
            ptr = jump;
            len = *(data + ptr);
        } else
            break;
    }
    ptr++;

    if (len > 0 || noff == 0) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "DNS qname invalid len %d noff %d", len, noff);
        return -1;
    }

    *(qname + noff - 1) = 0;
    log_print(PLATFORM_LOG_PRIORITY_DEBUG, "qname %s", qname);

    return (c ? off : ptr);
}

int parse_dns_response(const struct arguments *args, const struct ng_session *s,
                        const uint8_t *data, size_t *datalen) {
    if (*datalen < sizeof(struct dns_header) + 1) {
        log_print(PLATFORM_LOG_PRIORITY_WARN, "DNS response length %d", *datalen);
        return 0;
    }

    // Check if standard DNS query
    // TODO multiple qnames
    struct dns_header *dns = (struct dns_header *) data;
    int qcount = ntohs(dns->q_count);
    int acount = ntohs(dns->ans_count);
    if (dns->qr == 1 && dns->opcode == 0 && qcount > 0 && acount > 0) {
        log_print(PLATFORM_LOG_PRIORITY_DEBUG, "DNS response qcount %d acount %d", qcount, acount);
        if (qcount > 1)
            log_print(PLATFORM_LOG_PRIORITY_WARN, "DNS response qcount %d acount %d", qcount, acount);

        // http://tools.ietf.org/html/rfc1035
        char name[DNS_QNAME_MAX + 1];
        int32_t off = sizeof(struct dns_header);

        uint16_t qtype;
        uint16_t qclass;
        char qname[DNS_QNAME_MAX + 1];

        for (int q = 0; q < 1; q++) {
            off = get_qname(data, *datalen, (uint16_t) off, name);
            if (off > 0 && off + 4 <= *datalen) {
                // TODO multiple qnames?
                if (q == 0) {
                    strcpy(qname, name);
                    qtype = ntohs(*((uint16_t *) (data + off)));
                    qclass = ntohs(*((uint16_t *) (data + off + 2)));
                    log_print(PLATFORM_LOG_PRIORITY_DEBUG,
                                "DNS question %d qtype %d qclass %d qname %s",
                                q, qtype, qclass, qname);
                }
                off += 4;
            } else {
                log_print(PLATFORM_LOG_PRIORITY_WARN,
                            "DNS response Q invalid off %d datalen %d", off, *datalen);
                return 0;
            }
        }

        short svcb = 0;
        int32_t aoff = off;
        for (int a = 0; a < acount; a++) {
            off = get_qname(data, *datalen, (uint16_t) off, name);
            if (off > 0 && off + 10 <= *datalen) {
                uint16_t qtype = ntohs(*((uint16_t *) (data + off)));
                uint16_t qclass = ntohs(*((uint16_t *) (data + off + 2)));
                uint32_t ttl = ntohl(*((uint32_t *) (data + off + 4)));
                uint16_t rdlength = ntohs(*((uint16_t *) (data + off + 8)));
                off += 10;

                if (off + rdlength <= *datalen) {
                    if (qclass == DNS_QCLASS_IN &&
                        (qtype == DNS_QTYPE_A || qtype == DNS_QTYPE_AAAA)) {

                        char rd[INET6_ADDRSTRLEN + 1];
                        if (qtype == DNS_QTYPE_A) {
                            if (off + sizeof(__be32) <= *datalen)
                                inet_ntop(AF_INET, data + off, rd, sizeof(rd));
                            else
                                return 0;
                        } else if (qclass == DNS_QCLASS_IN && qtype == DNS_QTYPE_AAAA) {
                            if (off + sizeof(struct in6_addr) <= *datalen)
                                inet_ntop(AF_INET6, data + off, rd, sizeof(rd));
                            else
                                return 0;
                        }

                        dns_resolved(args, qname, name, rd, ttl);
                        log_print(PLATFORM_LOG_PRIORITY_DEBUG,
                                    "DNS answer %d qname %s qtype %d ttl %d data %s",
                                    a, name, qtype, ttl, rd);
                    } else if (qclass == DNS_QCLASS_IN &&
                               (qtype == DNS_SVCB || qtype == DNS_HTTPS)) {
                        // https://tools.ietf.org/id/draft-ietf-dnsop-svcb-https-01.html
                        svcb = 1;
                        log_print(PLATFORM_LOG_PRIORITY_WARN,
                                    "SVCB answer %d qname %s qtype %d", a, name, qtype);
                    } else
                        log_print(PLATFORM_LOG_PRIORITY_DEBUG,
                                    "DNS answer %d qname %s qclass %d qtype %d ttl %d length %d",
                                    a, name, qclass, qtype, ttl, rdlength);

                    off += rdlength;
                } else {
                    log_print(PLATFORM_LOG_PRIORITY_WARN,
                                "DNS response A invalid off %d rdlength %d datalen %d",
                                off, rdlength, *datalen);
                    return 0;
                }
            } else {
                log_print(PLATFORM_LOG_PRIORITY_WARN,
                            "DNS response A invalid off %d datalen %d", off, *datalen);
                return 0;
            }
        }

        if (qcount > 0 &&
            (svcb || is_domain_blocked(args, qname, s->udp.uid))) {
            dns->qr = 1;
            dns->aa = 0;
            dns->tc = 0;
            dns->rd = 0;
            dns->ra = 0;
            dns->z = 0;
            dns->ad = 0;
            dns->cd = 0;
            dns->rcode = (uint16_t) args->rcode;
            dns->ans_count = 0;
            dns->auth_count = 0;
            dns->add_count = 0;
            *datalen = aoff;

            int version;
            char source[INET6_ADDRSTRLEN + 1];
            char dest[INET6_ADDRSTRLEN + 1];
            uint16_t sport;
            uint16_t dport;

            if (s->protocol == IPPROTO_UDP) {
                version = s->udp.version;
                sport = ntohs(s->udp.source);
                dport = ntohs(s->udp.dest);
                if (s->udp.version == 4) {
                    inet_ntop(AF_INET, &s->udp.saddr.ip4, source, sizeof(source));
                    inet_ntop(AF_INET, &s->udp.daddr.ip4, dest, sizeof(dest));
                } else {
                    inet_ntop(AF_INET6, &s->udp.saddr.ip6, source, sizeof(source));
                    inet_ntop(AF_INET6, &s->udp.daddr.ip6, dest, sizeof(dest));
                }
            } else {
                version = s->tcp.version;
                sport = ntohs(s->tcp.source);
                dport = ntohs(s->tcp.dest);
                if (s->tcp.version == 4) {
                    inet_ntop(AF_INET, &s->tcp.saddr.ip4, source, sizeof(source));
                    inet_ntop(AF_INET, &s->tcp.daddr.ip4, dest, sizeof(dest));
                } else {
                    inet_ntop(AF_INET6, &s->tcp.saddr.ip6, source, sizeof(source));
                    inet_ntop(AF_INET6, &s->tcp.daddr.ip6, dest, sizeof(dest));
                }
            }

            // Log qname
            char name[DNS_QNAME_MAX + 40 + 1];
            sprintf(name, "qtype %d qname %s rcode %d", qtype, qname, dns->rcode);
            packet_t packet;
            packet.version = version;
            packet.protocol = s->protocol;
            packet.flags = "";
            packet.source = source;
            packet.sport = sport;
            packet.dest = dest;
            packet.dport = dport;
            packet.data = name;
            packet.uid = 0;
            packet.allowed = 0;
            log_packet(args, &packet);
            // TODO this is a temporary hack to minimise heavy retries. We'll refactor DNS parsing later on
            return 1; // signal DNS request should be blocked
        }
    } else if (acount > 0)
        log_print(PLATFORM_LOG_PRIORITY_WARN,
                    "DNS response qr %d opcode %d qcount %d acount %d",
                    dns->qr, dns->opcode, qcount, acount);

    return 0;
}
