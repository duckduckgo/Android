#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#include "DDGSyncCrypto.h"

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_crypto_SyncNativeLib_generateAccountKeys(JNIEnv* env, jclass class,
    jbyteArray primaryKey,
    jbyteArray secretKey,
    jbyteArray protectedSecretKey,
    jbyteArray passwordHash,
    jstring userId,
    jstring password) {

    // Get pointers to the arrays
    jbyte* primaryKeyElements = (*env)->GetByteArrayElements(env, primaryKey, NULL);
    jbyte* secretKeyElements = (*env)->GetByteArrayElements(env, secretKey, NULL);
    jbyte* protectedSecretKeyElements = (*env)->GetByteArrayElements(env, protectedSecretKey, NULL);
    jbyte* passwordHashElements = (*env)->GetByteArrayElements(env, passwordHash, NULL);

    // Convert the jstring arguments to const char*
    const char* userIdChars = (*env)->GetStringUTFChars(env, userId, NULL);
    const char* passwordChars = (*env)->GetStringUTFChars(env, password, NULL);

    jint result = ddgSyncGenerateAccountKeys(
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
Java_com_duckduckgo_sync_crypto_SyncNativeLib_prepareForConnect(JNIEnv* env, jclass class,
    jbyteArray publicKey,
    jbyteArray secretKey) {

    // Get pointers to the arrays
    jbyte* publicKeyElements = (*env)->GetByteArrayElements(env, publicKey, NULL);
    jbyte* secretKeyElements = (*env)->GetByteArrayElements(env, secretKey, NULL);

    jint result = ddgSyncPrepareForConnect(
        (unsigned char*) publicKeyElements,
        (unsigned char*) secretKeyElements);

    // Release the arrays
    (*env)->ReleaseByteArrayElements(env, publicKey, publicKeyElements, JNI_COMMIT);
    (*env)->ReleaseByteArrayElements(env, secretKey, secretKeyElements, JNI_COMMIT);

    return result;
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_crypto_SyncNativeLib_prepareForLogin(
    JNIEnv *env,
    jclass clazz,
    jbyteArray passwordHash,
    jbyteArray stretchedPrimaryKey,
    jbyteArray primaryKey) {

    // Get the input arrays as C arrays
    jbyte *passwordHashCArray = (*env)->GetByteArrayElements(env, passwordHash, NULL);
    jbyte *stretchedPrimaryKeyCArray = (*env)->GetByteArrayElements(env, stretchedPrimaryKey, NULL);
    jbyte *primaryKeyCArray = (*env)->GetByteArrayElements(env, primaryKey, NULL);

    jint result = ddgSyncPrepareForLogin(
        (unsigned char *)passwordHashCArray,
        (unsigned char *)stretchedPrimaryKeyCArray,
        (unsigned char *)primaryKeyCArray
    );

    // Release the input arrays
    (*env)->ReleaseByteArrayElements(env, passwordHash, passwordHashCArray, JNI_COMMIT);
    (*env)->ReleaseByteArrayElements(env, stretchedPrimaryKey, stretchedPrimaryKeyCArray, JNI_COMMIT);
    (*env)->ReleaseByteArrayElements(env, primaryKey, primaryKeyCArray, JNI_ABORT);

    return result;
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_crypto_SyncNativeLib_encrypt(
    JNIEnv *env,
    jclass clazz,
    jbyteArray encryptedBytes,
    jbyteArray rawBytes,
    jbyteArray secretKey
) {
    jsize rawBytesLength = (*env)->GetArrayLength(env, rawBytes);

    // Get pointers to the arrays
    jbyte* encryptedBytesElements = (*env)->GetByteArrayElements(env, encryptedBytes, NULL);
    jbyte* rawBytesElements = (*env)->GetByteArrayElements(env, rawBytes, NULL);
    jbyte* secretKeyElements = (*env)->GetByteArrayElements(env, secretKey, NULL);

    // Call the C function
    jint result = ddgSyncEncrypt(
      (unsigned char *)encryptedBytesElements,
      (unsigned char *)rawBytesElements,
      (unsigned long long)rawBytesLength,
      (unsigned char *)secretKeyElements
    );

    // Release the input arrays
    (*env)->ReleaseByteArrayElements(env, encryptedBytes, encryptedBytesElements, JNI_COMMIT);
    (*env)->ReleaseByteArrayElements(env, rawBytes, rawBytesElements, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, secretKey, secretKeyElements, JNI_ABORT);

    return result;
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_crypto_SyncNativeLib_decrypt(
    JNIEnv *env,
    jclass clazz,
    jbyteArray rawBytes,
    jbyteArray encryptedBytes,
    jbyteArray secretKey
) {
    jsize encryptedBytesLength = (*env)->GetArrayLength(env, encryptedBytes);

    // Get pointers to the arrays
    jbyte* rawBytesElements = (*env)->GetByteArrayElements(env, rawBytes, NULL);
    jbyte* encryptedBytesElements = (*env)->GetByteArrayElements(env, encryptedBytes, NULL);
    jbyte* secretKeyElements = (*env)->GetByteArrayElements(env, secretKey, NULL);

    // Call the C function
    jint result = ddgSyncDecrypt(
      (unsigned char *)rawBytesElements,
      (unsigned char *)encryptedBytesElements,
      (unsigned long long)encryptedBytesLength,
      (unsigned char *)secretKeyElements
    );

    // Release the input arrays
    (*env)->ReleaseByteArrayElements(env, rawBytes, rawBytesElements, JNI_COMMIT);
    (*env)->ReleaseByteArrayElements(env, encryptedBytes, encryptedBytesElements, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, secretKey, secretKeyElements, JNI_ABORT);

    return result;
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_crypto_SyncNativeLib_getPrimaryKeySize(
    JNIEnv *env,
    jclass clazz
) {
    return DDGSYNCCRYPTO_PRIMARY_KEY_SIZE;
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_crypto_SyncNativeLib_getSecretKeySize(
    JNIEnv *env,
    jclass clazz
) {
    return DDGSYNCCRYPTO_SECRET_KEY_SIZE;
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_crypto_SyncNativeLib_getProtectedSecretKeySize(
    JNIEnv *env,
    jclass clazz
) {
    return DDGSYNCCRYPTO_PROTECTED_SECRET_KEY_SIZE;
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_crypto_SyncNativeLib_getPasswordHashSize(
    JNIEnv *env,
    jclass clazz
) {
    return DDGSYNCCRYPTO_HASH_SIZE;
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_crypto_SyncNativeLib_getStretchedPrimaryKeySize(
    JNIEnv *env,
    jclass clazz
) {
    return DDGSYNCCRYPTO_STRETCHED_PRIMARY_KEY_SIZE;
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_crypto_SyncNativeLib_getEncryptedExtraBytes(
    JNIEnv *env,
    jclass clazz
) {
    return DDGSYNCCRYPTO_ENCRYPTED_EXTRA_BYTES_SIZE;
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_crypto_SyncNativeLib_getPublicKeyBytes(
    JNIEnv *env,
    jclass clazz
) {
    return DDGSYNCCRYPTO_PUBLIC_KEY;
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_sync_crypto_SyncNativeLib_getPrivateKeyBytes(
    JNIEnv *env,
    jclass clazz
) {
    return DDGSYNCCRYPTO_PRIVATE_KEY;
}

