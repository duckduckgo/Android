#include <jni.h>
#include "ad-block/ad_block_client.h"

extern "C"
JNIEXPORT jlong
JNICALL
Java_com_duckduckgo_app_trackerdetection_AdBlockPlus_createClient(JNIEnv *env,
                                                                  jobject /* this */) {
    AdBlockClient *client = new AdBlockClient();
    return (long) client;
}

extern "C"
JNIEXPORT void
JNICALL
Java_com_duckduckgo_app_trackerdetection_AdBlockPlus_releaseClient(JNIEnv *env,
                                                                  jobject /* this */,
                                                                  jlong clientPointer) {
    AdBlockClient *client = (AdBlockClient *) clientPointer;
    delete client;

}


extern "C"
JNIEXPORT void
JNICALL
Java_com_duckduckgo_app_trackerdetection_AdBlockPlus_loadBasicData(JNIEnv *env,
                                                                   jobject /* this */,
                                                                   jlong clientPointer,
                                                                   jbyteArray data) {

    int dataLength = env->GetArrayLength(data);
    char *dataChars = new char[dataLength];
    env->GetByteArrayRegion(data, 0, dataLength, reinterpret_cast<jbyte *>(dataChars));

    AdBlockClient *client = (AdBlockClient *) clientPointer;
    client->parse(dataChars);

    delete[] dataChars;
}

extern "C"
JNIEXPORT void
JNICALL
Java_com_duckduckgo_app_trackerdetection_AdBlockPlus_loadProcessedData(JNIEnv *env,
                                                                       jobject /* this */,
                                                                       jlong clientPointer,
                                                                       jbyteArray data) {

    int dataLength = env->GetArrayLength(data);
    char *dataChars = new char[dataLength];
    env->GetByteArrayRegion(data, 0, dataLength, reinterpret_cast<jbyte *>(dataChars));

    AdBlockClient *client = (AdBlockClient *) clientPointer;
    client->deserialize(dataChars);

    delete[] dataChars;
}


extern "C"
JNIEXPORT jbyteArray
JNICALL
Java_com_duckduckgo_app_trackerdetection_AdBlockPlus_getProcessedData(JNIEnv *env,
                                                                      jobject /* this */,
                                                                      jlong clientPointer) {

    AdBlockClient *client = (AdBlockClient *) clientPointer;
    int size;
    char *data = client->serialize(&size, false, false);

    jbyteArray dataBytes = env->NewByteArray(size);
    env->SetByteArrayRegion(dataBytes, 0, size, reinterpret_cast<jbyte *>(data));
    delete[] data;

    // TODO check memory...

    return dataBytes;
}

extern "C"
JNIEXPORT jboolean
JNICALL
Java_com_duckduckgo_app_trackerdetection_AdBlockPlus_matches(JNIEnv *env,
                                                             jobject /* this */,
                                                             jlong clientPointer,
                                                             jstring url,
                                                             jstring documentUrl,
                                                             jint filterOption) {
    jboolean isUrlCopy;
    const char *urlChars = env->GetStringUTFChars(url, &isUrlCopy);

    jboolean isDocumentCopy;
    const char *documentUrlChars = env->GetStringUTFChars(documentUrl, &isDocumentCopy);

    AdBlockClient *client = (AdBlockClient *) clientPointer;
    bool matches = client->matches(urlChars, (FilterOption) filterOption, documentUrlChars);

    env->ReleaseStringUTFChars(url, urlChars);
    env->ReleaseStringUTFChars(documentUrl, documentUrlChars);

    return matches;
}