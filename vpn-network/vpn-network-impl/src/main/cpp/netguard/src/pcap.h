#ifndef PCAP_H
#define PCAP_H

#include <stdint.h>

void write_pcap_hdr();
void write_pcap_rec(const uint8_t *buffer, size_t len);
void write_pcap(const void *ptr, size_t len);

#endif // PCAP_H
