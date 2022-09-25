/*
    This file is part of NetGuard.

    NetGuard is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NetGuard is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NetGuard.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2015-2019 by Marcel Bokhorst (M66B)
*/

#include "netguard.h"

// It is assumed that no packets will get lost and that packets arrive in order
// https://android.googlesource.com/platform/frameworks/base.git/+/master/services/core/jni/com_android_server_connectivity_Vpn.cpp

///////////////////////////////////////////////////////////////////////////////
// Global variables
///////////////////////////////////////////////////////////////////////////////

int loglevel = PLATFORM_LOG_PRIORITY_WARN;

///////////////////////////////////////////////////////////////////////////////
// Private APIs
///////////////////////////////////////////////////////////////////////////////

jobject create_packet(const struct arguments *args,
                      jint version,
                      jint protocol,
                      const char *flags,
                      const char *source,
                      jint sport,
                      const char *dest,
                      jint dport,
                      const char *data,
                      jint uid,
                      jboolean allowed);


///////////////////////////////////////////////////////////////////////////////
// JNI
///////////////////////////////////////////////////////////////////////////////
jclass clsPacket;
jclass clsAllowed;
jclass clsRR;
jclass clsUsage;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    log_print(PLATFORM_LOG_PRIORITY_INFO, "JNI load");

    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        log_print(PLATFORM_LOG_PRIORITY_INFO, "JNI load GetEnv failed");
        return -1;
    }

    const char *packet = "com/duckduckgo/vpn/network/impl/models/Packet";
    clsPacket = jniGlobalRef(env, jniFindClass(env, packet));
    ng_add_alloc(clsPacket, "clsPacket");

    const char *allowed = "com/duckduckgo/vpn/network/impl/models/Allowed";
    clsAllowed = jniGlobalRef(env, jniFindClass(env, allowed));
    ng_add_alloc(clsAllowed, "clsAllowed");

    const char *rr = "com/duckduckgo/vpn/network/impl/models/ResourceRecord";
    clsRR = jniGlobalRef(env, jniFindClass(env, rr));
    ng_add_alloc(clsRR, "clsRR");

    const char *usage = "com/duckduckgo/vpn/network/impl/models/Usage";
    clsUsage = jniGlobalRef(env, jniFindClass(env, usage));
    ng_add_alloc(clsUsage, "clsUsage");

    // Raise file number limit to maximum
    struct rlimit rlim;
    if (getrlimit(RLIMIT_NOFILE, &rlim))
        log_print(PLATFORM_LOG_PRIORITY_WARN, "getrlimit error %d: %s", errno, strerror(errno));
    else {
        rlim_t soft = rlim.rlim_cur;
        rlim.rlim_cur = rlim.rlim_max;
        if (setrlimit(RLIMIT_NOFILE, &rlim))
            log_print(PLATFORM_LOG_PRIORITY_WARN, "setrlimit error %d: %s", errno, strerror(errno));
        else
            log_print(PLATFORM_LOG_PRIORITY_WARN, "raised file limit from %d to %d", soft, rlim.rlim_cur);
    }

    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *vm, void *reserved) {
    log_print(PLATFORM_LOG_PRIORITY_INFO, "JNI unload");

    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK)
        log_print(PLATFORM_LOG_PRIORITY_INFO, "JNI load GetEnv failed");
    else {
        (*env)->DeleteGlobalRef(env, clsPacket);
        (*env)->DeleteGlobalRef(env, clsAllowed);
        (*env)->DeleteGlobalRef(env, clsRR);
        (*env)->DeleteGlobalRef(env, clsUsage);
        ng_delete_alloc(clsPacket, __FILE__, __LINE__);
        ng_delete_alloc(clsAllowed, __FILE__, __LINE__);
        ng_delete_alloc(clsRR, __FILE__, __LINE__);
        ng_delete_alloc(clsUsage, __FILE__, __LINE__);
    }
}

void report_exit(const struct arguments *args, const char *fmt, ...) {
    jclass cls = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(cls, "cls");
    jmethodID mid = jniGetMethodID(args->env, cls, "nativeExit", "(Ljava/lang/String;)V");

    jstring jreason = NULL;
    if (fmt != NULL) {
        char line[1024];
        va_list argptr;
        va_start(argptr, fmt);
        vsprintf(line, fmt, argptr);
        jreason = (*args->env)->NewStringUTF(args->env, line);
        ng_add_alloc(jreason, "jreason");
        va_end(argptr);
    }

    (*args->env)->CallVoidMethod(args->env, args->instance, mid, jreason);
    jniCheckException(args->env);

    if (jreason != NULL) {
        (*args->env)->DeleteLocalRef(args->env, jreason);
        ng_delete_alloc(jreason, __FILE__, __LINE__);
    }
    (*args->env)->DeleteLocalRef(args->env, cls);
    ng_delete_alloc(cls, __FILE__, __LINE__);
}

void report_error(const struct arguments *args, jint error, const char *fmt, ...) {
    jclass cls = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(cls, "cls");
    jmethodID mid = jniGetMethodID(args->env, cls, "nativeError", "(ILjava/lang/String;)V");

    jstring jreason = NULL;
    if (fmt != NULL) {
        char line[1024];
        va_list argptr;
        va_start(argptr, fmt);
        vsprintf(line, fmt, argptr);
        jreason = (*args->env)->NewStringUTF(args->env, line);
        ng_add_alloc(jreason, "jreason");
        va_end(argptr);
    }

    (*args->env)->CallVoidMethod(args->env, args->instance, mid, error, jreason);
    jniCheckException(args->env);

    if (jreason != NULL) {
        (*args->env)->DeleteLocalRef(args->env, jreason);
        ng_delete_alloc(jreason, __FILE__, __LINE__);
    }
    (*args->env)->DeleteLocalRef(args->env, cls);
    ng_delete_alloc(cls, __FILE__, __LINE__);
}

static jmethodID midProtect = NULL;

int protect_socket(const struct arguments *args, int socket) {
    // we protect sockets for SDK 31 and above to overcome DDG app downloads bug
    if (args->ctx->sdk >= 21)
        return 0;

    jclass cls = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(cls, "cls");
    if (cls == NULL) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "protect socket failed to get class");
        return -1;
    }

    if (midProtect == NULL)
        midProtect = jniGetMethodID(args->env, cls, "protect", "(I)Z");
    if (midProtect == NULL) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "protect socket failed to get method");
        return -1;
    }

    jboolean isProtected = (*args->env)->CallBooleanMethod(
            args->env, args->instance, midProtect, socket);
    jniCheckException(args->env);

    if (!isProtected) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "protect socket failed");
        return -1;
    }

    (*args->env)->DeleteLocalRef(args->env, cls);
    ng_delete_alloc(cls, __FILE__, __LINE__);

    return 0;
}

// http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/functions.html
// http://journals.ecs.soton.ac.uk/java/tutorial/native1.1/implementing/index.html

jobject jniGlobalRef(JNIEnv *env, jobject cls) {
    jobject gcls = (*env)->NewGlobalRef(env, cls);
    if (gcls == NULL)
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "Global ref failed (out of memory?)");
    return gcls;
}

jclass jniFindClass(JNIEnv *env, const char *name) {
    jclass cls = (*env)->FindClass(env, name);
    if (cls == NULL)
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "Class %s not found", name);
    else
        jniCheckException(env);
    return cls;
}

jmethodID jniGetMethodID(JNIEnv *env, jclass cls, const char *name, const char *signature) {
    jmethodID method = (*env)->GetMethodID(env, cls, name, signature);
    if (method == NULL) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "Method %s %s not found", name, signature);
        jniCheckException(env);
    }
    return method;
}

jfieldID jniGetFieldID(JNIEnv *env, jclass cls, const char *name, const char *type) {
    jfieldID field = (*env)->GetFieldID(env, cls, name, type);
    if (field == NULL)
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "Field %s type %s not found", name, type);
    return field;
}

jobject jniNewObject(JNIEnv *env, jclass cls, jmethodID constructor, const char *name) {
    jobject object = (*env)->NewObject(env, cls, constructor);
    if (object == NULL)
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "Create object %s failed", name);
    else
        jniCheckException(env);
    return object;
}

int jniCheckException(JNIEnv *env) {
    jthrowable ex = (*env)->ExceptionOccurred(env);
    if (ex) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, ex);
        ng_delete_alloc(ex, __FILE__, __LINE__);
        return 1;
    }
    return 0;
}

static jmethodID midLogPacket = NULL;

void log_packet(const struct arguments *args, const packet_t *packet) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jobject jpacket = create_packet(
        args,
        packet->version,
        packet->protocol,
        packet->flags,
        packet->source,
        packet->sport,
        packet->dest,
        packet->dport,
        packet->data,
        packet->uid,
        packet->allowed
    );

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(Lcom/duckduckgo/vpn/network/impl/models/Packet;)V";
    if (midLogPacket == NULL)
        midLogPacket = jniGetMethodID(args->env, clsService, "logPacket", signature);

    (*args->env)->CallVoidMethod(args->env, args->instance, midLogPacket, jpacket);
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, clsService);
    (*args->env)->DeleteLocalRef(args->env, jpacket);
    ng_delete_alloc(clsService, __FILE__, __LINE__);
    ng_delete_alloc(jpacket, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_print(PLATFORM_LOG_PRIORITY_WARN, "log_packet %f", mselapsed);
#endif
}


static jmethodID midSniResolved = NULL;

void sni_resolved(const struct arguments *args, const char *name, const char *daddr) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(Ljava/lang/String;Ljava/lang/String;)V";
    if (midSniResolved == NULL) {
        midSniResolved = jniGetMethodID(args->env, clsService, "sniResolved", signature);
    }

    jstring jname = (*args->env)->NewStringUTF(args->env, name);
    jstring jresource = (*args->env)->NewStringUTF(args->env, daddr);
    ng_add_alloc(jname, "jname");
    ng_add_alloc(jresource, "jresource");

    (*args->env)->CallVoidMethod(args->env, args->instance, midSniResolved, jname, jresource);
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, jname);
    (*args->env)->DeleteLocalRef(args->env, jresource);
    (*args->env)->DeleteLocalRef(args->env, clsService);
    ng_delete_alloc(jname, __FILE__, __LINE__);
    ng_delete_alloc(jresource, __FILE__, __LINE__);
    ng_delete_alloc(clsService, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_print(PLATFORM_LOG_PRIORITY_WARN, "sni_resolved %f", mselapsed);
#endif
}

static jmethodID midDnsResolved = NULL;
static jmethodID midInitRR = NULL;
jfieldID fidQTime = NULL;
jfieldID fidQName = NULL;
jfieldID fidAName = NULL;
jfieldID fidResource = NULL;
jfieldID fidTTL = NULL;

void dns_resolved(const struct arguments *args,
                  const char *qname, const char *aname, const char *resource, int ttl) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(Lcom/duckduckgo/vpn/network/impl/models/ResourceRecord;)V";
    if (midDnsResolved == NULL)
        midDnsResolved = jniGetMethodID(args->env, clsService, "dnsResolved", signature);

    const char *rr = "com/duckduckgo/vpn/network/impl/models/ResourceRecord";
    if (midInitRR == NULL)
        midInitRR = jniGetMethodID(args->env, clsRR, "<init>", "()V");

    jobject jrr = jniNewObject(args->env, clsRR, midInitRR, rr);
    ng_add_alloc(jrr, "jrr");

    if (fidQTime == NULL) {
        const char *string = "Ljava/lang/String;";
        fidQTime = jniGetFieldID(args->env, clsRR, "Time", "J");
        fidQName = jniGetFieldID(args->env, clsRR, "QName", string);
        fidAName = jniGetFieldID(args->env, clsRR, "AName", string);
        fidResource = jniGetFieldID(args->env, clsRR, "Resource", string);
        fidTTL = jniGetFieldID(args->env, clsRR, "TTL", "I");
    }

    jlong jtime = time(NULL) * 1000LL;
    jstring jqname = (*args->env)->NewStringUTF(args->env, qname);
    jstring janame = (*args->env)->NewStringUTF(args->env, aname);
    jstring jresource = (*args->env)->NewStringUTF(args->env, resource);
    ng_add_alloc(jqname, "jqname");
    ng_add_alloc(janame, "janame");
    ng_add_alloc(jresource, "jresource");

    (*args->env)->SetLongField(args->env, jrr, fidQTime, jtime);
    (*args->env)->SetObjectField(args->env, jrr, fidQName, jqname);
    (*args->env)->SetObjectField(args->env, jrr, fidAName, janame);
    (*args->env)->SetObjectField(args->env, jrr, fidResource, jresource);
    (*args->env)->SetIntField(args->env, jrr, fidTTL, ttl);

    (*args->env)->CallVoidMethod(args->env, args->instance, midDnsResolved, jrr);
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, jresource);
    (*args->env)->DeleteLocalRef(args->env, janame);
    (*args->env)->DeleteLocalRef(args->env, jqname);
    (*args->env)->DeleteLocalRef(args->env, jrr);
    (*args->env)->DeleteLocalRef(args->env, clsService);
    ng_delete_alloc(jresource, __FILE__, __LINE__);
    ng_delete_alloc(janame, __FILE__, __LINE__);
    ng_delete_alloc(jqname, __FILE__, __LINE__);
    ng_delete_alloc(jrr, __FILE__, __LINE__);
    ng_delete_alloc(clsService, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_print(PLATFORM_LOG_PRIORITY_WARN, "log_packet %f", mselapsed);
#endif
}

static jmethodID midIsDomainBlocked = NULL;

jboolean is_domain_blocked(const struct arguments *args, const char *name, jint uid) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(Ljava/lang/String;I)Z";
    if (midIsDomainBlocked == NULL)
        midIsDomainBlocked = jniGetMethodID(args->env, clsService, "isDomainBlocked", signature);

    jstring jname = (*args->env)->NewStringUTF(args->env, name);
    ng_add_alloc(jname, "jname");

    jboolean jallowed = (*args->env)->CallBooleanMethod(
            args->env, args->instance, midIsDomainBlocked, jname, uid);
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, jname);
    (*args->env)->DeleteLocalRef(args->env, clsService);
    ng_delete_alloc(jname, __FILE__, __LINE__);
    ng_delete_alloc(clsService, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_print(PLATFORM_LOG_PRIORITY_WARN, "is_domain_blocked %f", mselapsed);
#endif

    return jallowed;
}

static jmethodID midGetUidQ = NULL;

jint get_uid_q(const struct arguments *args,
               jint version, jint protocol,
               const char *source, jint sport,
               const char *dest, jint dport) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(IILjava/lang/String;ILjava/lang/String;I)I";
    if (midGetUidQ == NULL)
        midGetUidQ = jniGetMethodID(args->env, clsService, "getUidQ", signature);

    jstring jsource = (*args->env)->NewStringUTF(args->env, source);
    jstring jdest = (*args->env)->NewStringUTF(args->env, dest);
    ng_add_alloc(jsource, "jsource");
    ng_add_alloc(jdest, "jdest");

    jint juid = (*args->env)->CallIntMethod(
            args->env, args->instance, midGetUidQ,
            version, protocol, jsource, sport, jdest, dport);
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, jdest);
    (*args->env)->DeleteLocalRef(args->env, jsource);
    (*args->env)->DeleteLocalRef(args->env, clsService);
    ng_delete_alloc(jdest, __FILE__, __LINE__);
    ng_delete_alloc(jsource, __FILE__, __LINE__);
    ng_delete_alloc(clsService, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_print(PLATFORM_LOG_PRIORITY_WARN, "get_uid_q %f", mselapsed);
#endif

    return juid;
}

static jmethodID midIsAddressAllowed = NULL;
jfieldID fidRaddr = NULL;
jfieldID fidRport = NULL;
struct allowed allowed;

struct allowed *is_address_allowed(const struct arguments *args, const packet_t *packet) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jobject jpacket = create_packet(
        args,
        packet->version,
        packet->protocol,
        packet->flags,
        packet->source,
        packet->sport,
        packet->dest,
        packet->dport,
        packet->data,
        packet->uid,
        packet->allowed
    );

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(Lcom/duckduckgo/vpn/network/impl/models/Packet;)Lcom/duckduckgo/vpn/network/impl/models/Allowed;";
    if (midIsAddressAllowed == NULL)
        midIsAddressAllowed = jniGetMethodID(args->env, clsService, "isAddressAllowed", signature);

    jobject jallowed = (*args->env)->CallObjectMethod(
            args->env, args->instance, midIsAddressAllowed, jpacket);
    ng_add_alloc(jallowed, "jallowed");
    jniCheckException(args->env);

    if (jallowed != NULL) {
        if (fidRaddr == NULL) {
            const char *string = "Ljava/lang/String;";
            fidRaddr = jniGetFieldID(args->env, clsAllowed, "raddr", string);
            fidRport = jniGetFieldID(args->env, clsAllowed, "rport", "I");
        }

        jstring jraddr = (*args->env)->GetObjectField(args->env, jallowed, fidRaddr);
        ng_add_alloc(jraddr, "jraddr");
        if (jraddr == NULL)
            *allowed.raddr = 0;
        else {
            const char *raddr = (*args->env)->GetStringUTFChars(args->env, jraddr, NULL);
            ng_add_alloc(raddr, "raddr");
            strcpy(allowed.raddr, raddr);
            (*args->env)->ReleaseStringUTFChars(args->env, jraddr, raddr);
            ng_delete_alloc(raddr, __FILE__, __LINE__);
        }
        allowed.rport = (uint16_t) (*args->env)->GetIntField(args->env, jallowed, fidRport);

        (*args->env)->DeleteLocalRef(args->env, jraddr);
        ng_delete_alloc(jraddr, __FILE__, __LINE__);
    }


    (*args->env)->DeleteLocalRef(args->env, jpacket);
    (*args->env)->DeleteLocalRef(args->env, clsService);
    (*args->env)->DeleteLocalRef(args->env, jallowed);
    ng_delete_alloc(jpacket, __FILE__, __LINE__);
    ng_delete_alloc(clsService, __FILE__, __LINE__);
    ng_delete_alloc(jallowed, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_print(PLATFORM_LOG_PRIORITY_WARN, "is_address_allowed %f", mselapsed);
#endif

    return (jallowed == NULL ? NULL : &allowed);
}

jmethodID midInitPacket = NULL;

jfieldID fidTime = NULL;
jfieldID fidVersion = NULL;
jfieldID fidProtocol = NULL;
jfieldID fidFlags = NULL;
jfieldID fidSaddr = NULL;
jfieldID fidSport = NULL;
jfieldID fidDaddr = NULL;
jfieldID fidDport = NULL;
jfieldID fidData = NULL;
jfieldID fidUid = NULL;
jfieldID fidAllowed = NULL;

jobject create_packet(const struct arguments *args,
                      jint version,
                      jint protocol,
                      const char *flags,
                      const char *source,
                      jint sport,
                      const char *dest,
                      jint dport,
                      const char *data,
                      jint uid,
                      jboolean allowed) {
    JNIEnv *env = args->env;

#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    /*
        jbyte b[] = {1,2,3};
        jbyteArray ret = env->NewByteArray(3);
        env->SetByteArrayRegion (ret, 0, 3, b);
     */

    const char *packet = "com/duckduckgo/vpn/network/impl/models/Packet";
    if (midInitPacket == NULL)
        midInitPacket = jniGetMethodID(env, clsPacket, "<init>", "()V");
    jobject jpacket = jniNewObject(env, clsPacket, midInitPacket, packet);
    ng_add_alloc(jpacket, "jpacket");

    if (fidTime == NULL) {
        const char *string = "Ljava/lang/String;";
        fidTime = jniGetFieldID(env, clsPacket, "time", "J");
        fidVersion = jniGetFieldID(env, clsPacket, "version", "I");
        fidProtocol = jniGetFieldID(env, clsPacket, "protocol", "I");
        fidFlags = jniGetFieldID(env, clsPacket, "flags", string);
        fidSaddr = jniGetFieldID(env, clsPacket, "saddr", string);
        fidSport = jniGetFieldID(env, clsPacket, "sport", "I");
        fidDaddr = jniGetFieldID(env, clsPacket, "daddr", string);
        fidDport = jniGetFieldID(env, clsPacket, "dport", "I");
        fidData = jniGetFieldID(env, clsPacket, "data", string);
        fidUid = jniGetFieldID(env, clsPacket, "uid", "I");
        fidAllowed = jniGetFieldID(env, clsPacket, "allowed", "Z");
    }

    struct timeval tv;
    gettimeofday(&tv, NULL);
    jlong t = tv.tv_sec * 1000LL + tv.tv_usec / 1000;
    jstring jflags = (*env)->NewStringUTF(env, flags);
    jstring jsource = (*env)->NewStringUTF(env, source);
    jstring jdest = (*env)->NewStringUTF(env, dest);
    jstring jdata = (*env)->NewStringUTF(env, data);
    ng_add_alloc(jflags, "jflags");
    ng_add_alloc(jsource, "jsource");
    ng_add_alloc(jdest, "jdest");
    ng_add_alloc(jdata, "jdata");

    (*env)->SetLongField(env, jpacket, fidTime, t);
    (*env)->SetIntField(env, jpacket, fidVersion, version);
    (*env)->SetIntField(env, jpacket, fidProtocol, protocol);
    (*env)->SetObjectField(env, jpacket, fidFlags, jflags);
    (*env)->SetObjectField(env, jpacket, fidSaddr, jsource);
    (*env)->SetIntField(env, jpacket, fidSport, sport);
    (*env)->SetObjectField(env, jpacket, fidDaddr, jdest);
    (*env)->SetIntField(env, jpacket, fidDport, dport);
    (*env)->SetObjectField(env, jpacket, fidData, jdata);
    (*env)->SetIntField(env, jpacket, fidUid, uid);
    (*env)->SetBooleanField(env, jpacket, fidAllowed, allowed);

    (*env)->DeleteLocalRef(env, jdata);
    (*env)->DeleteLocalRef(env, jdest);
    (*env)->DeleteLocalRef(env, jsource);
    (*env)->DeleteLocalRef(env, jflags);
    ng_delete_alloc(jdata, __FILE__, __LINE__);
    ng_delete_alloc(jdest, __FILE__, __LINE__);
    ng_delete_alloc(jsource, __FILE__, __LINE__);
    ng_delete_alloc(jflags, __FILE__, __LINE__);
    // Caller needs to delete reference to packet

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_print(PLATFORM_LOG_PRIORITY_WARN, "create_packet %f", mselapsed);
#endif

    return jpacket;
}

jmethodID midAccountUsage = NULL;
jmethodID midInitUsage = NULL;
jfieldID fidUsageTime = NULL;
jfieldID fidUsageVersion = NULL;
jfieldID fidUsageProtocol = NULL;
jfieldID fidUsageDAddr = NULL;
jfieldID fidUsageDPort = NULL;
jfieldID fidUsageUid = NULL;
jfieldID fidUsageSent = NULL;
jfieldID fidUsageReceived = NULL;

void account_usage(const struct arguments *args, uint32_t version, uint32_t protocol,
                   const char *daddr, uint32_t dport, uint32_t uid, uint64_t sent, uint64_t received) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(Lcom/duckduckgo/vpn/network/impl/models/Usage;)V";
    if (midAccountUsage == NULL)
        midAccountUsage = jniGetMethodID(args->env, clsService, "accountUsage", signature);

    const char *usage = "com/duckduckgo/vpn/network/impl/models/Usage";
    if (midInitUsage == NULL)
        midInitUsage = jniGetMethodID(args->env, clsUsage, "<init>", "()V");

    jobject jusage = jniNewObject(args->env, clsUsage, midInitUsage, usage);
    ng_add_alloc(jusage, "jusage");

    if (fidUsageTime == NULL) {
        const char *string = "Ljava/lang/String;";
        fidUsageTime = jniGetFieldID(args->env, clsUsage, "Time", "J");
        fidUsageVersion = jniGetFieldID(args->env, clsUsage, "Version", "I");
        fidUsageProtocol = jniGetFieldID(args->env, clsUsage, "Protocol", "I");
        fidUsageDAddr = jniGetFieldID(args->env, clsUsage, "DAddr", string);
        fidUsageDPort = jniGetFieldID(args->env, clsUsage, "DPort", "I");
        fidUsageUid = jniGetFieldID(args->env, clsUsage, "Uid", "I");
        fidUsageSent = jniGetFieldID(args->env, clsUsage, "Sent", "J");
        fidUsageReceived = jniGetFieldID(args->env, clsUsage, "Received", "J");
    }

    jlong jtime = time(NULL) * 1000LL;
    jstring jdaddr = (*args->env)->NewStringUTF(args->env, daddr);
    ng_add_alloc(jdaddr, "jdaddr");

    (*args->env)->SetLongField(args->env, jusage, fidUsageTime, jtime);
    (*args->env)->SetIntField(args->env, jusage, fidUsageVersion, version);
    (*args->env)->SetIntField(args->env, jusage, fidUsageProtocol, protocol);
    (*args->env)->SetObjectField(args->env, jusage, fidUsageDAddr, jdaddr);
    (*args->env)->SetIntField(args->env, jusage, fidUsageDPort, dport);
    (*args->env)->SetIntField(args->env, jusage, fidUsageUid, uid);
    (*args->env)->SetLongField(args->env, jusage, fidUsageSent, sent);
    (*args->env)->SetLongField(args->env, jusage, fidUsageReceived, received);

    (*args->env)->CallVoidMethod(args->env, args->instance, midAccountUsage, jusage);
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, jdaddr);
    (*args->env)->DeleteLocalRef(args->env, jusage);
    (*args->env)->DeleteLocalRef(args->env, clsService);
    ng_delete_alloc(jdaddr, __FILE__, __LINE__);
    ng_delete_alloc(jusage, __FILE__, __LINE__);
    ng_delete_alloc(clsService, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_print(PLATFORM_LOG_PRIORITY_WARN, "log_packet %f", mselapsed);
#endif
}
