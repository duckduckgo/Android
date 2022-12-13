#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#include "sodium.h"
#include "sodium/crypto_pwhash_scryptsalsa208sha256.h"

JNIEXPORT jint JNICALL
Java_com_beemdevelopment_sodium_SodiumJNI_sodium_1init(JNIEnv* env, jclass class) {
    return sodium_init();
}

JNIEXPORT jint JNICALL
Java_com_beemdevelopment_sodium_SodiumJNI_crypto_1pwhash_1scryptsalsa208sha256_1ll(JNIEnv* env, jclass class,
                                                                                   jobject passwd, jsize passwdlen,
                                                                                   jobject salt, jsize saltlen,
                                                                                   jlong N, jint r, jint p,
                                                                                   jobject buf, jsize buflen) {
    jbyte *cpasswd = (jbyte *) (*env)->GetDirectBufferAddress(env, passwd);
    jbyte *csalt = (jbyte *) (*env)->GetDirectBufferAddress(env, salt);
    jbyte *cbuf = (jbyte *) (*env)->GetDirectBufferAddress(env, buf);

    return crypto_pwhash_scryptsalsa208sha256_ll((const uint8_t *) cpasswd, (size_t) passwdlen,
                                                 (const uint8_t *) csalt, (size_t) saltlen,
                                                 (uint64_t) N, (uint32_t) r, (uint32_t) p,
                                                 (uint8_t *) cbuf, (size_t) buflen);
}