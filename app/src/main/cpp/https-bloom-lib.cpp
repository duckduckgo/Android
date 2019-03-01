#include <jni.h>
#include "third-party/bloom_cpp/src/BloomFilter.hpp"

extern "C"
JNIEXPORT long
JNICALL
Java_com_duckduckgo_app_httpsupgrade_BloomFilter_createBloomFilter(JNIEnv *env,
                                                                   jobject,
                                                                   jint maxItems,
                                                                   jdouble targetProbability) {
    BloomFilter *filter = new BloomFilter(maxItems, targetProbability);
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

    BloomFilter *filter = new BloomFilter(pathChars, maxItems);
    env->ReleaseStringUTFChars(path, pathChars);
    return (long) filter;
}


extern "C"
JNIEXPORT void
JNICALL
Java_com_duckduckgo_app_httpsupgrade_BloomFilter_releaseBloomFilter(JNIEnv *env,
                                                                    jobject,
                                                                    jlong pointer) {
    auto *filter = (BloomFilter *) pointer;
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

    env->ReleaseStringUTFChars(element, elementChars);
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
    bool containsElement = filter->contains(elementChars);
    env->ReleaseStringUTFChars(element, elementChars);
    return containsElement;
}