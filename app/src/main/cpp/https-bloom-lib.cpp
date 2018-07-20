#include <jni.h>
#include "https/BloomFilter.hpp"

extern "C"
JNIEXPORT long
JNICALL
Java_com_duckduckgo_app_httpsupgrade_BloomFilter_createBloomFilter(JNIEnv *env,
                                                                   jobject,
                                                                   jint maxItems,
                                                                   jdouble targetProbability) {
    BloomFilter* filter = new BloomFilter(maxItems, targetProbability);
    return (long) filter;
}


extern "C"
JNIEXPORT long
JNICALL
Java_com_duckduckgo_app_httpsupgrade_BloomFilter_createBloomFilterFromFile(JNIEnv *env,
                                                                   jobject,
                                                                   jstring path,
                                                                   jint maxItems) {
    jboolean isElementCopy;
    const char *pathChars = env->GetStringUTFChars(path, &isElementCopy);

    BloomFilter* filter = new BloomFilter(pathChars, maxItems);
    return (long) filter;
}


extern "C"
JNIEXPORT void
JNICALL
Java_com_duckduckgo_app_httpsupgrade_BloomFilter_releaseBloomFilter(JNIEnv *env,
                                                                     jobject,
                                                                     jlong pointer) {
    BloomFilter *filter = (BloomFilter *) pointer;
    delete filter;
}

extern "C"
JNIEXPORT void
JNICALL
Java_com_duckduckgo_app_httpsupgrade_BloomFilter_add(JNIEnv *env,
                                                      jobject,
                                                      jlong pointer,
                                                      jstring element) {
    jboolean isElementCopy;
    const char *elementChars = env->GetStringUTFChars(element, &isElementCopy);

    BloomFilter *filter = (BloomFilter *) pointer;
    filter->add(elementChars);
}

extern "C"
JNIEXPORT jboolean
JNICALL
Java_com_duckduckgo_app_httpsupgrade_BloomFilter_contains(JNIEnv *env,
                                                           jobject,
                                                           jlong pointer,
                                                           jstring element) {
    jboolean isElementCopy;
    const char *elementChars = env->GetStringUTFChars(element, &isElementCopy);

    BloomFilter *filter = (BloomFilter *) pointer;
    return filter->contains(elementChars);
}