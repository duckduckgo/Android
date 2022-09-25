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

#define LINKTYPE_RAW 101

// PCAP
// https://wiki.wireshark.org/Development/LibpcapFileFormat

typedef uint16_t guint16_t;
typedef uint32_t guint32_t;
typedef int32_t gint32_t;

typedef struct pcap_hdr_s {
    guint32_t magic_number;
    guint16_t version_major;
    guint16_t version_minor;
    gint32_t thiszone;
    guint32_t sigfigs;
    guint32_t snaplen;
    guint32_t network;
} __packed pcap_hdr_s;

typedef struct pcaprec_hdr_s {
    guint32_t ts_sec;
    guint32_t ts_usec;
    guint32_t incl_len;
    guint32_t orig_len;
} __packed pcaprec_hdr_s;

///////////////////////////////////////////////////////////////////////////////

FILE *pcap_file = NULL;
size_t pcap_record_size = 64;
long pcap_file_size = 2 * 1024 * 1024;

void write_pcap_hdr() {
    struct pcap_hdr_s pcap_hdr;
    pcap_hdr.magic_number = 0xa1b2c3d4;
    pcap_hdr.version_major = 2;
    pcap_hdr.version_minor = 4;
    pcap_hdr.thiszone = 0;
    pcap_hdr.sigfigs = 0;
    pcap_hdr.snaplen = pcap_record_size;
    pcap_hdr.network = LINKTYPE_RAW;
    write_pcap(&pcap_hdr, sizeof(struct pcap_hdr_s));
}

void write_pcap_rec(const uint8_t *buffer, size_t length) {
    struct timespec ts;
    if (clock_gettime(CLOCK_REALTIME, &ts))
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "clock_gettime error %d: %s", errno, strerror(errno));

    size_t plen = (length < pcap_record_size ? length : pcap_record_size);
    size_t rlen = sizeof(struct pcaprec_hdr_s) + plen;
    struct pcaprec_hdr_s *pcap_rec = ng_malloc(rlen, "pcap");

    pcap_rec->ts_sec = (guint32_t) ts.tv_sec;
    pcap_rec->ts_usec = (guint32_t) (ts.tv_nsec / 1000);
    pcap_rec->incl_len = (guint32_t) plen;
    pcap_rec->orig_len = (guint32_t) length;

    memcpy(((uint8_t *) pcap_rec) + sizeof(struct pcaprec_hdr_s), buffer, plen);

    write_pcap(pcap_rec, rlen);

    ng_free(pcap_rec, __FILE__, __LINE__);
}

void write_pcap(const void *ptr, size_t len) {
    if (fwrite(ptr, len, 1, pcap_file) < 1)
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "PCAP fwrite error %d: %s", errno, strerror(errno));
    else {
        long fsize = ftell(pcap_file);
        log_print(PLATFORM_LOG_PRIORITY_VERBOSE, "PCAP wrote %d @%ld", len, fsize);

        if (fsize > pcap_file_size) {
            log_print(PLATFORM_LOG_PRIORITY_WARN, "PCAP truncate @%ld", fsize);
            if (ftruncate(fileno(pcap_file), sizeof(struct pcap_hdr_s)))
                log_print(PLATFORM_LOG_PRIORITY_ERROR, "PCAP ftruncate error %d: %s",
                            errno, strerror(errno));
            else {
                if (!lseek(fileno(pcap_file), sizeof(struct pcap_hdr_s), SEEK_SET))
                    log_print(PLATFORM_LOG_PRIORITY_ERROR, "PCAP ftruncate error %d: %s",
                                errno, strerror(errno));
            }
        }
    }
}
