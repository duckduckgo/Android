#include <jni.h>
#include "ad-block/ad_block_client.h"


AdBlockClient easylistClient = AdBlockClient();
AdBlockClient easyprivacyClient = AdBlockClient();

char *to_char_array(JNIEnv *env, jbyteArray array) {
    int len = env->GetArrayLength(array);
    char *buf = new char[len];
    env->GetByteArrayRegion(array, 0, len, reinterpret_cast<jbyte *>(buf));
    //TODO clear that memory when done, otherwise this will be leakalicious ;-)
    return buf;
}

extern "C"
JNIEXPORT void
JNICALL
Java_com_duckduckgo_app_trackerdetection_AdBlockPlus_loadData(JNIEnv *env, jobject /* this */,
                                                              jbyteArray easylistData,
                                                              jbyteArray easyprivacyData) {
    easylistClient.parse(to_char_array(env, easylistData));
    easyprivacyClient.parse(to_char_array(env, easyprivacyData));
}


extern "C"
JNIEXPORT jboolean
JNICALL
Java_com_duckduckgo_app_trackerdetection_AdBlockPlus_matches(JNIEnv *env, jobject /* this */,
                                                             jstring url, jstring documentUrl,
                                                             jint filterOption) {

    jboolean isUrlCopy;
    const char *urlChars = env->GetStringUTFChars(url, &isUrlCopy);

    jboolean isDocumentCopy;
    const char *documentUrlChars = env->GetStringUTFChars(documentUrl, &isDocumentCopy);
    bool matches = easyprivacyClient.matches(urlChars, FONoFilterOption, documentUrlChars) ||
                   easylistClient.matches(urlChars, FONoFilterOption, documentUrlChars);

    env->ReleaseStringUTFChars(url, urlChars);
    env->ReleaseStringUTFChars(documentUrl, documentUrlChars);

    return matches;
}
