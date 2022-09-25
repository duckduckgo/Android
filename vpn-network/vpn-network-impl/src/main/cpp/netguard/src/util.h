#ifndef UTIL_H
#define UTIL_H

#include <stdint.h>

uint16_t calc_checksum(uint16_t start, const uint8_t *buffer, size_t length);

int compare_u32(uint32_t seq1, uint32_t seq2);

int sdk_int(JNIEnv *env);

void hex2bytes(const char *hex, uint8_t *buffer);

char *hex(const uint8_t *data, const size_t len);

int32_t get_local_port(const int sock);

int is_readable(int fd);

int is_writable(int fd);

long long get_ms();

const char *strstate(const int state);

#endif // UTIL_H
