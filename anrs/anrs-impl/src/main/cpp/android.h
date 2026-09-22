#ifndef ANDROID_JNI_H
#define ANDROID_JNI_H

#include <cstdio>
#include <android/log.h>

void __platform_log_print(int prio, const char *tag, const char *fmt, ...);

// loglevel will be assigned during library initialisation, it always has a default value
extern int loglevel;
extern char pname[256];
extern char appVersion[256];
extern bool isCustomTab;
extern char wvPackage[256];
extern char wvVersion[256];

// Use this method to print log messages into the console
#define log_print(prio, format, ...) do { if (prio >= loglevel) __platform_log_print(prio, "ndk-crash", format, ##__VA_ARGS__); } while (0)

#endif // ANDROID_JNI_H