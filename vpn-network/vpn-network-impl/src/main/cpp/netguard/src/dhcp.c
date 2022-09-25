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

#define DHCP_OPTION_MAGIC_NUMBER (0x63825363)

typedef struct dhcp_packet {
    uint8_t opcode;
    uint8_t htype;
    uint8_t hlen;
    uint8_t hops;
    uint32_t xid;
    uint16_t secs;
    uint16_t flags;
    uint32_t ciaddr;
    uint32_t yiaddr;
    uint32_t siaddr;
    uint32_t giaddr;
    uint8_t chaddr[16];
    uint8_t sname[64];
    uint8_t file[128];
    uint32_t option_format;
} __packed dhcp_packet;

typedef struct dhcp_option {
    uint8_t code;
    uint8_t length;
} __packed dhcp_option;

///////////////////////////////////////////////////////////////////////////////

int check_dhcp(const struct arguments *args, const struct udp_session *u,
               const uint8_t *data, const size_t datalen) {

    // This is untested
    // Android routing of DHCP is erroneous

    log_print(PLATFORM_LOG_PRIORITY_WARN, "DHCP check");

    if (datalen < sizeof(struct dhcp_packet)) {
        log_print(PLATFORM_LOG_PRIORITY_WARN, "DHCP packet size %d", datalen);
        return -1;
    }

    const struct dhcp_packet *request = (struct dhcp_packet *) data;

    if (ntohl(request->option_format) != DHCP_OPTION_MAGIC_NUMBER) {
        log_print(PLATFORM_LOG_PRIORITY_WARN, "DHCP invalid magic %x", request->option_format);
        return -1;
    }

    if (request->htype != 1 || request->hlen != 6) {
        log_print(PLATFORM_LOG_PRIORITY_WARN, "DHCP unknown hardware htype %d hlen %d",
                    request->htype, request->hlen);
        return -1;
    }

    log_print(PLATFORM_LOG_PRIORITY_WARN, "DHCP opcode", request->opcode);

    // Discover: source 0.0.0.0:68 destination 255.255.255.255:67
    // Offer: source 10.1.10.1:67 destination 255.255.255.255:68
    // Request: source 0.0.0.0:68 destination 255.255.255.255:67
    // Ack: source: 10.1.10.1 destination: 255.255.255.255

    if (request->opcode == 1) { // Discover/request
        struct dhcp_packet *response = ng_calloc(500, 1, "dhcp");

        // Hack
        inet_pton(AF_INET, "10.1.10.1", (void *) &u->saddr);

        /*
        Discover:
            DHCP option 53: DHCP Discover
            DHCP option 50: 192.168.1.100 requested
            DHCP option 55: Parameter Request List:
            Request Subnet Mask (1), Router (3), Domain Name (15), Domain Name Server (6)

        Request
            DHCP option 53: DHCP Request
            DHCP option 50: 192.168.1.100 requested
            DHCP option 54: 192.168.1.1 DHCP server.
        */

        memcpy(response, request, sizeof(struct dhcp_packet));
        response->opcode = (uint8_t) (request->siaddr == 0 ? 2 /* Offer */ : /* Ack */ 4);
        response->secs = 0;
        response->flags = 0;
        memset(&response->ciaddr, 0, sizeof(response->ciaddr));
        inet_pton(AF_INET, "10.1.10.2", &response->yiaddr);
        inet_pton(AF_INET, "10.1.10.1", &response->siaddr);
        memset(&response->giaddr, 0, sizeof(response->giaddr));

        // https://tools.ietf.org/html/rfc2132
        uint8_t *options = (uint8_t *) (response + sizeof(struct dhcp_packet));

        int idx = 0;
        *(options + idx++) = 53; // Message type
        *(options + idx++) = 1;
        *(options + idx++) = (uint8_t) (request->siaddr == 0 ? 2 : 5);
        /*
             1     DHCPDISCOVER
             2     DHCPOFFER
             3     DHCPREQUEST
             4     DHCPDECLINE
             5     DHCPACK
             6     DHCPNAK
             7     DHCPRELEASE
             8     DHCPINFORM
         */

        *(options + idx++) = 1; // subnet mask
        *(options + idx++) = 4; // IP4 length
        inet_pton(AF_INET, "255.255.255.0", options + idx);
        idx += 4;

        *(options + idx++) = 3; // gateway
        *(options + idx++) = 4; // IP4 length
        inet_pton(AF_INET, "10.1.10.1", options + idx);
        idx += 4;

        *(options + idx++) = 51; // lease time
        *(options + idx++) = 4; // quad
        *((uint32_t *) (options + idx)) = 3600;
        idx += 4;

        *(options + idx++) = 54; // DHCP
        *(options + idx++) = 4; // IP4 length
        inet_pton(AF_INET, "10.1.10.1", options + idx);
        idx += 4;

        *(options + idx++) = 6; // DNS
        *(options + idx++) = 4; // IP4 length
        inet_pton(AF_INET, "8.8.8.8", options + idx);
        idx += 4;

        *(options + idx++) = 255; // End

        /*
            DHCP option 53: DHCP Offer
            DHCP option 1: 255.255.255.0 subnet mask
            DHCP option 3: 192.168.1.1 router
            DHCP option 51: 86400s (1 day) IP address lease time
            DHCP option 54: 192.168.1.1 DHCP server
            DHCP option 6: DNS servers 9.7.10.15
         */

        write_udp(args, u, (uint8_t *) response, 500);

        ng_free(response, __FILE__, __LINE__);
    }

    return 0;
}
