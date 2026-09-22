// Java
#ifndef NDK_CRASH_H
#define NDK_CRASH_H

// Call this method to register native crash handling
bool native_crash_handler_init();
// Call this method to de-register native crash handling
bool native_crash_handler_fini();

#endif // NDK_CRASH_H