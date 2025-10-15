#include <jni.h>
#include <android/log.h>
#include <exception>
#include <string.h> // strncpy

#include "android.h"
#include "ndk-crash.h"
#include "pixel.h"

///////////////////////////////////////////////////////////////////////////

int loglevel = 0;
char appVersion[256];
char pname[256];
bool isCustomTab = false;
char wvPackage[256];
char wvVersion[256];

///////////////////////////////////////////////////////////////////////////


void __platform_log_print(int prio, const char *tag, const char *fmt, ...) {
    char line[1024];
    va_list argptr;
    va_start(argptr, fmt);
    vsprintf(line, fmt, argptr);
    __android_log_print(prio, tag, "%s", line);
    va_end(argptr);
}

///////////////////////////////////////////////////////////////////////////
// native<>JVM interface
///////////////////////////////////////////////////////////////////////////

extern "C" JNIEXPORT void JNICALL
Java_com_duckduckgo_app_anr_ndk_NativeCrashInit_jni_1register_1sighandler(
        JNIEnv* env,
        jobject instance,
        jint loglevel_,
        jstring version_,
        jstring pname_,
        jboolean customtab_,
        jstring wvpackage_,
        jstring wvversion_
) {

    if (!native_crash_handler_init()) {
        log_print(ANDROID_LOG_ERROR, "Error initialising crash handler.");
        return;
    }

    // get and set loglevel
    loglevel = loglevel_;

    // get and set app vesrion
    const char *versionChars = env->GetStringUTFChars(version_, nullptr);
    strncpy(appVersion, versionChars, sizeof(appVersion) - 1);
    appVersion[sizeof(appVersion) - 1] = '\0'; // Ensure null-termination
    env->ReleaseStringUTFChars(version_, versionChars);

    // get and set process name
    const char *pnameChars = env->GetStringUTFChars(pname_, nullptr);
    strncpy(pname, pnameChars, sizeof(pname) - 1);
    pname[sizeof(pname) - 1] = '\0'; // Ensure null-termination
    env->ReleaseStringUTFChars(pname_, pnameChars);

    // get and set webview package name
    const char *wvpackageChars = env->GetStringUTFChars(wvpackage_, nullptr);
    strncpy(wvPackage, wvpackageChars, sizeof(wvPackage) - 1);
    wvPackage[sizeof(wvPackage) - 1] = '\0'; // Ensure null-termination
    env->ReleaseStringUTFChars(wvpackage_, wvpackageChars);

    // get and set webview version
    const char *wvversionChars = env->GetStringUTFChars(wvversion_, nullptr);
    strncpy(wvVersion, wvversionChars, sizeof(wvVersion) - 1);
    wvVersion[sizeof(wvVersion) - 1] = '\0'; // Ensure null-termination
    env->ReleaseStringUTFChars(wvversion_, wvversionChars);

    // get and set isCustomTabs
    isCustomTab = customtab_;

    send_crash_handle_init_pixel();

    log_print(ANDROID_LOG_ERROR, "Native crash handler successfully initialized on %s.", pname);
}

extern "C" JNIEXPORT void JNICALL
Java_com_duckduckgo_app_anr_ndk_NativeCrashInit_jni_1unregister_sighandler(
        JNIEnv* env,
        jobject /* this */) {
    native_crash_handler_fini();
}
