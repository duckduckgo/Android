// Java
#ifndef NDK_PIXEL_H
#define NDK_PIXEL_H

#include <cstdint>

// Call this method to send the native crash pixel
void send_crash_pixel();
// Call this method to send the native crash pixel with crash location
// signo: the signal number that caused the crash
// lib_name: the library path/name where the crash location was resolved
// offset: the offset within the resolved library
void send_crash_pixel_with_location(int signo, const char* lib_name, uintptr_t offset);
// Call this method to send the native crash handler init pixel
void send_crash_handle_init_pixel();

#endif // NDK_PIXEL_H