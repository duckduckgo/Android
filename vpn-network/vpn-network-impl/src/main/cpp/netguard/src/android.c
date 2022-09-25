#include <android/log.h>
#include <jni.h>

#include "netguard.h"

void __platform_log_print(int prio, const char *tag, const char *fmt, ...) {
    char line[1024];
    va_list argptr;
    va_start(argptr, fmt);
    vsprintf(line, fmt, argptr);
    __android_log_print(prio, tag, "%s", line);
    va_end(argptr);
}


int __sdk_int(JNIEnv *env) {
    jclass clsVersion = jniFindClass(env, "android/os/Build$VERSION");
    jfieldID fid = (*env)->GetStaticFieldID(env, clsVersion, "SDK_INT", "I");
    return (*env)->GetStaticIntField(env, clsVersion, fid);
}

////////////////////////////////////////////////////////////////////////////////
// JNI RealVpnNetwork
////////////////////////////////////////////////////////////////////////////////

JNIEXPORT jlong JNICALL
Java_com_duckduckgo_vpn_network_impl_RealVpnNetwork_jni_1init(
        JNIEnv *env, jobject instance, jint sdk) {
    struct context *ctx = ng_calloc(1, sizeof(struct context), "init");
    ctx->sdk = sdk;

    loglevel = PLATFORM_LOG_PRIORITY_WARN;

    *socks5_addr = 0;
    socks5_port = 0;
    *socks5_username = 0;
    *socks5_password = 0;
    pcap_file = NULL;

    if (pthread_mutex_init(&ctx->lock, NULL))
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_init failed");

    // Create signal pipe
    if (pipe(ctx->pipefds))
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "Create pipe error %d: %s", errno, strerror(errno));
    else
        for (int i = 0; i < 2; i++) {
            int flags = fcntl(ctx->pipefds[i], F_GETFL, 0);
            if (flags < 0 || fcntl(ctx->pipefds[i], F_SETFL, flags | O_NONBLOCK) < 0)
                log_print(PLATFORM_LOG_PRIORITY_ERROR, "fcntl pipefds[%d] O_NONBLOCK error %d: %s",
                            i, errno, strerror(errno));
        }

    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_com_duckduckgo_vpn_network_impl_RealVpnNetwork_jni_1start(
        JNIEnv *env, jobject instance, jlong context, jint loglevel_) {
    struct context *ctx = (struct context *) context;

    loglevel = loglevel_;
    max_tun_msg = 0;
    ctx->stopping = 0;

    log_print(PLATFORM_LOG_PRIORITY_WARN, "Starting level %d", loglevel);

}

JNIEXPORT void JNICALL
Java_com_duckduckgo_vpn_network_impl_RealVpnNetwork_jni_1run(
        JNIEnv *env, jobject instance, jlong context, jint tun, jboolean fwd53, jint rcode) {
    struct context *ctx = (struct context *) context;

    log_print(PLATFORM_LOG_PRIORITY_WARN, "Running tun %d fwd53 %d level %d", tun, fwd53, loglevel);

    // Set blocking
    int flags = fcntl(tun, F_GETFL, 0);
    if (flags < 0 || fcntl(tun, F_SETFL, flags & ~O_NONBLOCK) < 0)
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "fcntl tun ~O_NONBLOCK error %d: %s",
                    errno, strerror(errno));

    // Get arguments
    struct arguments *args = ng_malloc(sizeof(struct arguments), "arguments");
    args->env = env;
    args->instance = instance;
    args->tun = tun;
    args->fwd53 = fwd53;
    args->rcode = rcode;
    args->ctx = ctx;
    handle_events(args);
}

JNIEXPORT void JNICALL
Java_com_duckduckgo_vpn_network_impl_RealVpnNetwork_jni_1stop(
        JNIEnv *env, jobject instance, jlong context) {
    struct context *ctx = (struct context *) context;
    ctx->stopping = 1;

    log_print(PLATFORM_LOG_PRIORITY_WARN, "Write pipe wakeup");
    if (write(ctx->pipefds[1], "w", 1) < 0)
        log_print(PLATFORM_LOG_PRIORITY_WARN, "Write pipe error %d: %s", errno, strerror(errno));
}

JNIEXPORT void JNICALL
Java_com_duckduckgo_vpn_network_impl_RealVpnNetwork_jni_1clear(
        JNIEnv *env, jobject instance, jlong context) {
    struct context *ctx = (struct context *) context;
    clear(ctx);
}

JNIEXPORT jint JNICALL
Java_com_duckduckgo_vpn_network_impl_RealVpnNetwork_jni_1get_1mtu(JNIEnv *env, jobject instance) {
    return get_mtu();
}

JNIEXPORT jintArray JNICALL
Java_com_duckduckgo_vpn_network_impl_RealVpnNetwork_jni_1get_1stats(
        JNIEnv *env, jobject instance, jlong context) {
    struct context *ctx = (struct context *) context;

    if (pthread_mutex_lock(&ctx->lock))
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_lock failed");

    jintArray jarray = (*env)->NewIntArray(env, 5);
    jint *jcount = (*env)->GetIntArrayElements(env, jarray, NULL);

    struct ng_session *s = ctx->ng_session;
    while (s != NULL) {
        if (s->protocol == IPPROTO_ICMP || s->protocol == IPPROTO_ICMPV6) {
            if (!s->icmp.stop)
                jcount[0]++;
        } else if (s->protocol == IPPROTO_UDP) {
            if (s->udp.state == UDP_ACTIVE)
                jcount[1]++;
        } else if (s->protocol == IPPROTO_TCP) {
            if (s->tcp.state != TCP_CLOSING && s->tcp.state != TCP_CLOSE)
                jcount[2]++;
        }
        s = s->next;
    }

    if (pthread_mutex_unlock(&ctx->lock))
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_unlock failed");

    jcount[3] = 0;
    DIR *d = opendir("/proc/self/fd");
    if (d) {
        struct dirent *dir;
        while ((dir = readdir(d)) != NULL)
            if (dir->d_type != DT_DIR)
                jcount[3]++;
        closedir(d);
    }

    struct rlimit rlim;
    memset(&rlim, 0, sizeof(struct rlimit));
    getrlimit(RLIMIT_NOFILE, &rlim);
    jcount[4] = (jint) rlim.rlim_cur;

    (*env)->ReleaseIntArrayElements(env, jarray, jcount, 0);
    return jarray;
}

JNIEXPORT void JNICALL
Java_com_duckduckgo_vpn_network_impl_RealVpnNetwork_jni_1pcap(
        JNIEnv *env, jclass type,
        jstring name_, jint record_size, jint file_size) {

    pcap_record_size = (size_t) record_size;
    pcap_file_size = file_size;

    //if (pthread_mutex_lock(&lock))
    //    log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_lock failed");

    if (name_ == NULL) {
        if (pcap_file != NULL) {
            int flags = fcntl(fileno(pcap_file), F_GETFL, 0);
            if (flags < 0 || fcntl(fileno(pcap_file), F_SETFL, flags & ~O_NONBLOCK) < 0)
                log_print(PLATFORM_LOG_PRIORITY_ERROR, "PCAP fcntl ~O_NONBLOCK error %d: %s",
                            errno, strerror(errno));

            if (fsync(fileno(pcap_file)))
                log_print(PLATFORM_LOG_PRIORITY_ERROR, "PCAP fsync error %d: %s", errno, strerror(errno));

            if (fclose(pcap_file))
                log_print(PLATFORM_LOG_PRIORITY_ERROR, "PCAP fclose error %d: %s", errno, strerror(errno));

            pcap_file = NULL;
        }
        log_print(PLATFORM_LOG_PRIORITY_WARN, "PCAP disabled");
    } else {
        const char *name = (*env)->GetStringUTFChars(env, name_, 0);
        ng_add_alloc(name, "name");
        log_print(PLATFORM_LOG_PRIORITY_WARN, "PCAP file %s record size %d truncate @%ld",
                    name, pcap_record_size, pcap_file_size);

        pcap_file = fopen(name, "ab+");
        if (pcap_file == NULL)
            log_print(PLATFORM_LOG_PRIORITY_ERROR, "PCAP fopen error %d: %s", errno, strerror(errno));
        else {
            int flags = fcntl(fileno(pcap_file), F_GETFL, 0);
            if (flags < 0 || fcntl(fileno(pcap_file), F_SETFL, flags | O_NONBLOCK) < 0)
                log_print(PLATFORM_LOG_PRIORITY_ERROR, "PCAP fcntl O_NONBLOCK error %d: %s",
                            errno, strerror(errno));

            long size = ftell(pcap_file);
            if (size == 0) {
                log_print(PLATFORM_LOG_PRIORITY_WARN, "PCAP initialize");
                write_pcap_hdr();
            } else
                log_print(PLATFORM_LOG_PRIORITY_WARN, "PCAP current size %ld", size);
        }

        (*env)->ReleaseStringUTFChars(env, name_, name);
        ng_delete_alloc(name, __FILE__, __LINE__);
    }

    //if (pthread_mutex_unlock(&lock))
    //    log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_unlock failed");
}

JNIEXPORT void JNICALL
Java_com_duckduckgo_vpn_network_impl_RealVpnNetwork_jni_1socks5(JNIEnv *env, jobject instance, jstring addr_,
                                                      jint port, jstring username_,
                                                      jstring password_) {
    const char *addr = (*env)->GetStringUTFChars(env, addr_, 0);
    const char *username = (*env)->GetStringUTFChars(env, username_, 0);
    const char *password = (*env)->GetStringUTFChars(env, password_, 0);
    ng_add_alloc(addr, "addr");
    ng_add_alloc(username, "username");
    ng_add_alloc(password, "password");

    strcpy(socks5_addr, addr);
    socks5_port = port;
    strcpy(socks5_username, username);
    strcpy(socks5_password, password);

    log_print(PLATFORM_LOG_PRIORITY_WARN, "SOCKS5 %s:%d user=%s",
                socks5_addr, socks5_port, socks5_username);

    (*env)->ReleaseStringUTFChars(env, addr_, addr);
    (*env)->ReleaseStringUTFChars(env, username_, username);
    (*env)->ReleaseStringUTFChars(env, password_, password);
    ng_delete_alloc(addr, __FILE__, __LINE__);
    ng_delete_alloc(username, __FILE__, __LINE__);
    ng_delete_alloc(password, __FILE__, __LINE__);
}

JNIEXPORT void JNICALL
Java_com_duckduckgo_vpn_network_impl_RealVpnNetwork_jni_1done(
        JNIEnv *env, jobject instance, jlong context) {
    struct context *ctx = (struct context *) context;
    log_print(PLATFORM_LOG_PRIORITY_INFO, "Done");

    clear(ctx);

    if (pthread_mutex_destroy(&ctx->lock))
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_destroy failed");

    for (int i = 0; i < 2; i++)
        if (close(ctx->pipefds[i]))
            log_print(PLATFORM_LOG_PRIORITY_ERROR, "Close pipe error %d: %s", errno, strerror(errno));

    if (uid_cache != NULL)
        ng_free(uid_cache, __FILE__, __LINE__);
    uid_cache_size = 0;
    uid_cache = NULL;

    ng_free(ctx, __FILE__, __LINE__);
}

////////////////////////////////////////////////////////////////////////////////
// JNI Util
////////////////////////////////////////////////////////////////////////////////

JNIEXPORT jstring JNICALL
Java_eu_faircode_netguard_Util_jni_1getprop(JNIEnv *env, jclass type, jstring name_) {
    const char *name = (*env)->GetStringUTFChars(env, name_, 0);
    ng_add_alloc(name, "name");

    char value[PROP_VALUE_MAX + 1] = "";
    __system_property_get(name, value);

    (*env)->ReleaseStringUTFChars(env, name_, name);
    ng_delete_alloc(name, __FILE__, __LINE__);

    return (*env)->NewStringUTF(env, value); // Freed by Java
}

JNIEXPORT jboolean JNICALL
Java_eu_faircode_netguard_Util_is_1numeric_1address(JNIEnv *env, jclass type, jstring ip_) {
    jboolean numeric = 0;
    const char *ip = (*env)->GetStringUTFChars(env, ip_, 0);
    ng_add_alloc(ip, "ip");

    struct addrinfo hints;
    memset(&hints, 0, sizeof(struct addrinfo));
    hints.ai_family = AF_UNSPEC;
    hints.ai_flags = AI_NUMERICHOST;
    struct addrinfo *result;
    int err = getaddrinfo(ip, NULL, &hints, &result);
    if (err)
        log_print(PLATFORM_LOG_PRIORITY_DEBUG, "getaddrinfo(%s) error %d: %s", ip, err, gai_strerror(err));
    else
        numeric = (jboolean) (result != NULL);

    if (result != NULL)
        freeaddrinfo(result);

    (*env)->ReleaseStringUTFChars(env, ip_, ip);
    ng_delete_alloc(ip, __FILE__, __LINE__);
    return numeric;
}


JNIEXPORT void JNICALL
Java_eu_faircode_netguard_Util_dump_1memory_1profile(JNIEnv *env, jclass type) {
#ifdef PROFILE_MEMORY
    log_print(PLATFORM_LOG_PRIORITY_DEBUG, "Dump memory profile");

    if (pthread_mutex_lock(alock))
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_lock failed");

    ng_dump();

    if (pthread_mutex_unlock(alock))
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_unlock failed");

#endif
}