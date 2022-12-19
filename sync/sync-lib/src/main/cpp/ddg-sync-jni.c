#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#include "DDGSyncCrypto.h"

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_lib_NativeLib_init(JNIEnv* env, jclass class,
    jbyteArray primaryKey,
    jbyteArray secretKey,
    jbyteArray protectedSecretKey,
    jbyteArray passwordHash,
    jstring userId,
    jstring password) {

    // Get the size of the arrays
    jsize primaryKeyLength = (*env)->GetArrayLength(env, primaryKey);
    jsize secretKeyLength = (*env)->GetArrayLength(env, secretKey);
    jsize protectedSecretKeyLength = (*env)->GetArrayLength(env, protectedSecretKey);
    jsize passwordHashLength = (*env)->GetArrayLength(env, passwordHash);

    // Get pointers to the arrays
    jbyte* primaryKeyElements = (*env)->GetByteArrayElements(env, primaryKey, NULL);
    jbyte* secretKeyElements = (*env)->GetByteArrayElements(env, secretKey, NULL);
    jbyte* protectedSecretKeyElements = (*env)->GetByteArrayElements(env, protectedSecretKey, NULL);
    jbyte* passwordHashElements = (*env)->GetByteArrayElements(env, passwordHash, NULL);

    // Convert the jstring arguments to const char*
    const char* userIdChars = (*env)->GetStringUTFChars(env, userId, NULL);
    const char* passwordChars = (*env)->GetStringUTFChars(env, password, NULL);

    ddgSyncGenerateAccountKeys(
        (unsigned char*) primaryKeyElements,
        (unsigned char*) secretKeyElements,
        (unsigned char*) protectedSecretKeyElements,
        (unsigned char*) passwordHashElements,
        userIdChars,
        passwordChars);

    // Release the arrays and jstring arguments
    (*env)->ReleaseByteArrayElements(env, primaryKey, primaryKeyElements, JNI_COMMIT);
    (*env)->ReleaseByteArrayElements(env, secretKey, secretKeyElements, JNI_COMMIT);
    (*env)->ReleaseByteArrayElements(env, protectedSecretKey, protectedSecretKeyElements, JNI_COMMIT);
    (*env)->ReleaseByteArrayElements(env, passwordHash, passwordHashElements, JNI_COMMIT);
    (*env)->ReleaseStringUTFChars(env, userId, userIdChars);
    (*env)->ReleaseStringUTFChars(env, password, passwordChars);
}