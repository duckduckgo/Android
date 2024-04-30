#include <jni.h>
#include <android/log.h>
#include <exception>
#include <string.h> // strncpy

#include "android.h"
#include "ndk-crash.h"
#include "pixel.h"

///////////////////////////////////////////////////////////////////////////

static JavaVM *JVM = NULL;
jclass clsCrash;
jobject CLASS_JVM_CRASH = NULL;


static jobject jniGlobalRef(JNIEnv *env, jobject cls);
static jclass jniFindClass(JNIEnv *env, const char *name);
static jmethodID jniGetMethodID(JNIEnv *env, jclass cls, const char *name, const char *signature);

int loglevel = 0;
char appVersion[256];
char pname[256];
bool isCustomTab = false;

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
// JNI utils
///////////////////////////////////////////////////////////////////////////

static jobject jniGlobalRef(JNIEnv *env, jobject cls) {
    jobject gcls = env->NewGlobalRef(cls);
    if (gcls == NULL)
        log_print(ANDROID_LOG_ERROR, "Global ref failed (out of memory?)");
    return gcls;
}

static jclass jniFindClass(JNIEnv *env, const char *name) {
    jclass cls = env->FindClass(name);
    if (cls == NULL)
        log_print(ANDROID_LOG_ERROR, "Class %s not found", name);
    return cls;
}

static jmethodID jniGetMethodID(JNIEnv *env, jclass cls, const char *name, const char *signature) {
    jmethodID method = env->GetMethodID(cls, name, signature);
    if (method == NULL) {
        log_print(ANDROID_LOG_ERROR, "Method %s %s not found", name, signature);
    }
    return method;
}

///////////////////////////////////////////////////////////////////////////
// JNI lifecycle
///////////////////////////////////////////////////////////////////////////

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if ((vm)->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        log_print(ANDROID_LOG_INFO, "JNI load GetEnv failed");
        return -1;
    }

    jint rs = env->GetJavaVM(&JVM);
    if (rs != JNI_OK) {
        log_print(ANDROID_LOG_ERROR, "Could not get JVM");
        return -1;
    }

    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *vm, void *reserved) {
    log_print(ANDROID_LOG_INFO, "JNI unload");

    JNIEnv *env;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK)
        log_print(ANDROID_LOG_INFO, "JNI load GetEnv failed");
    else {
        env->DeleteGlobalRef(clsCrash);
    }
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
        jboolean customtab_
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

    // get and set isCustomTabs
    isCustomTab = customtab_;

    clsCrash = env->GetObjectClass(instance);
    const char *emptyParamVoidSig = "()V";
    CLASS_JVM_CRASH = env->NewGlobalRef(instance);

    send_crash_handle_init_pixel();

    log_print(ANDROID_LOG_ERROR, "Native crash handler successfully initialized.");
}

extern "C" JNIEXPORT void JNICALL
Java_com_duckduckgo_app_anr_ndk_NativeCrashInit_jni_1unregister_sighandler(
        JNIEnv* env,
        jobject /* this */) {
    native_crash_handler_fini();
}
