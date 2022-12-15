#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#include "DDGSyncCrypto.h"

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_lib_NativeLib_init(JNIEnv* env, jclass class) {
    return test_init();
}