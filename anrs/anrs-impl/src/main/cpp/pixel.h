// Java
#ifndef NDK_PIXEL_H
#define NDK_PIXEL_H

// Call this method to send the native crash pixel
void send_crash_pixel();
// Call this method to send the native crash handler init pixel
void send_crash_handle_init_pixel();

#endif // NDK_PIXEL_H