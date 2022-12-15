#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#include "sodium.h"
#include "sodium/crypto_pwhash_scryptsalsa208sha256.h"

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_lib_NativeLib_init(JNIEnv* env, jclass class) {
    return sodium_init();
}