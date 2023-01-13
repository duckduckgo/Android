#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#include "DDGSyncCrypto.h"

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_lib_SyncNativeLib_generateAccountKeys(JNIEnv* env, jclass class,
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

    jint result = 111;
    result = ddgSyncGenerateAccountKeys(
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

    return result;
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_lib_SyncNativeLib_prepareForLogin(
    JNIEnv *env,
    jclass clazz,
    jbyteArray passwordHash,
    jbyteArray stretchedPrimaryKey,
    jbyteArray primaryKey) {

    // Get the input arrays as C arrays
    jbyte *passwordHashCArray = (*env)->GetByteArrayElements(env, passwordHash, NULL);
    jbyte *stretchedPrimaryKeyCArray = (*env)->GetByteArrayElements(env, stretchedPrimaryKey, NULL);
    jbyte *primaryKeyCArray = (*env)->GetByteArrayElements(env, primaryKey, NULL);

    jint result = 111;
    result = ddgSyncPrepareForLogin(
        (unsigned char *)passwordHashCArray,
        (unsigned char *)stretchedPrimaryKeyCArray,
        (unsigned char *)primaryKeyCArray
    );

    // Release the input arrays
    (*env)->ReleaseByteArrayElements(env, passwordHash, passwordHashCArray, JNI_COMMIT);
    (*env)->ReleaseByteArrayElements(env, stretchedPrimaryKey, stretchedPrimaryKeyCArray, JNI_COMMIT);

    return result;
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_lib_SyncNativeLib_decrypt(
    JNIEnv *env,
    jclass clazz,
    jbyteArray rawBytes, //out
    jbyteArray encryptedBytes, //in
    jlong encryptedBytesLength, //in
    jbyteArray secretKey // in
) {
    // Get pointers to the arrays
    jbyte* rawBytesElements = (*env)->GetByteArrayElements(env, rawBytes, NULL);
    jbyte* encryptedBytesElements = (*env)->GetByteArrayElements(env, encryptedBytes, NULL);
    jbyte* secretKeyElements = (*env)->GetByteArrayElements(env, secretKey, NULL);

    // Call the C function
    jint result = 111;

    result = ddgSyncDecrypt(
      (unsigned char *)rawBytesElements,
      (unsigned char *)encryptedBytesElements,
      (unsigned long long)encryptedBytesLength,
      (unsigned char *)secretKeyElements
    );

    // Release the input arrays
    (*env)->ReleaseByteArrayElements(env, encryptedBytes, encryptedBytesElements, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, secretKey, secretKeyElements, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, rawBytes, rawBytesElements, JNI_COMMIT);

    return result;
}
