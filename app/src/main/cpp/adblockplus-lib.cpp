#include <jni.h>
#include <string>
#include "adblockplus/ad_block_client.h"


AdBlockClient client = AdBlockClient();

char* to_char_array(JNIEnv *env, jbyteArray array) {
    int len = env->GetArrayLength (array);
    char* buf = new char[len];
    env->GetByteArrayRegion (array, 0, len, reinterpret_cast<jbyte*>(buf));
    return buf;
}

extern "C"
JNIEXPORT void
JNICALL Java_com_duckduckgo_app_trackerdetection_AdBlockPlus_loadData(JNIEnv *env, jobject /* this */, jbyteArray bytes) {
    char* data = to_char_array(env, bytes);
    client.deserialize(data);
}


extern "C"
JNIEXPORT jboolean
JNICALL Java_com_duckduckgo_app_trackerdetection_AdBlockPlus_matches(JNIEnv *env, jobject /* this */, jstring url, jstring documentUrl, jint filterOption) {

    jboolean isUrlCopy;
    const char *urlChars = env->GetStringUTFChars(url, &isUrlCopy);

    jboolean  isDocumentCopy;
    const char *documentUrlChars = env->GetStringUTFChars(documentUrl, &isDocumentCopy);
    bool matches = client.matches(urlChars, (FilterOption) filterOption, documentUrlChars);

    env->ReleaseStringUTFChars(url, urlChars);
    env->ReleaseStringUTFChars(documentUrl, documentUrlChars);

    return matches;
}
