/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright © 2017-2021 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 */

#include "netguard.h"
#include "tls.h"
#include "uid_mapping.h"

struct go_string { const char *str; long n; };
extern int wgTurnOn(struct go_string ifname, int tun_fd, struct go_string settings);
extern void wgTurnOff(int handle);
extern int wgGetSocketV4(int handle);
extern int wgGetSocketV6(int handle);
extern char *wgGetConfig(int handle);
extern char *wgVersion();

static JavaVM *JVM = NULL;
static jobject GO_BACKEND = NULL;
static jmethodID MID_SHOULD_ALLOW = NULL;
static jint SDK = 0;

JNIEXPORT jint JNICALL Java_com_wireguard_android_backend_GoBackend_wgTurnOn(JNIEnv *env, jobject  goBackend, jstring ifname,
                                          jint tun_fd, jstring settings, jint loglevel_,
                                          jint sdk)
{
    loglevel = loglevel_;
    SDK = sdk;

    // Init global variables
    jint rs = (*env)->GetJavaVM(env, &JVM);
    if (rs != JNI_OK) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "Could not get JVM");
        return -1;
    }

    GO_BACKEND = (*env)->NewGlobalRef(env, goBackend);

    jclass clsGoBackend = (*env)->GetObjectClass(env, goBackend);
    const char *shouldAllowSig = "(ILjava/lang/String;ILjava/lang/String;ILjava/lang/String;I)Z";
    MID_SHOULD_ALLOW = jniGetMethodID(env, clsGoBackend, "shouldAllow", shouldAllowSig);
    (*env)->DeleteLocalRef(env, clsGoBackend);

    // Call Go method
    const char *ifname_str = (*env)->GetStringUTFChars(env, ifname, 0);
    size_t ifname_len = (*env)->GetStringUTFLength(env, ifname);
    const char *settings_str = (*env)->GetStringUTFChars(env, settings, 0);
    size_t settings_len = (*env)->GetStringUTFLength(env, settings);
    int ret = wgTurnOn((struct go_string){
        .str = ifname_str,
        .n = ifname_len
    }, tun_fd, (struct go_string){
        .str = settings_str,
        .n = settings_len
    });
    (*env)->ReleaseStringUTFChars(env, ifname, ifname_str);
    (*env)->ReleaseStringUTFChars(env, settings, settings_str);
    return ret;
}

/**
 * Called from Go code to check if the packet is allowed
 * @param buffer IP datagram to check
 * @param length length of the datagram
 * @return 1 (true) if packet is allowed, 0 (false) otherwise
 */
int is_pkt_allowed(const uint8_t *buffer, int length) {
    JNIEnv *env;
    jint rs = (*JVM)->AttachCurrentThread(JVM, &env, NULL);
    if (rs != JNI_OK) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "Could not attach to JVM thread");
        return 1;
    }

    void *saddr;
    void *daddr;
    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];

    struct iphdr *ip4hdr = (struct iphdr *) buffer;
    saddr = &ip4hdr->saddr;
    daddr = &ip4hdr->daddr;

    int version = 4;
    inet_ntop(version == 4 ? AF_INET : AF_INET6, saddr, source, sizeof(source));
    inet_ntop(version == 4 ? AF_INET : AF_INET6, daddr, dest, sizeof(dest));

    uint8_t ipoptlen = (uint8_t) ((ip4hdr->ihl - 5) * 4);
    if (length < sizeof(struct tcphdr) + sizeof(struct iphdr) + ipoptlen) {
        log_print(PLATFORM_LOG_PRIORITY_INFO, "TCP packet is too short");
        return 1;
    }

    const struct tcphdr *tcp = (struct tcphdr *) (buffer + sizeof(struct iphdr) + ipoptlen);
    jint sport = ntohs(tcp->source);
    jint dport = ntohs(tcp->dest);

    const uint8_t tcpoptlen = (uint8_t) ((tcp->doff - 5) * 4);
    const uint8_t tlsPos = sizeof(struct iphdr) + ipoptlen + sizeof(struct tcphdr) + tcpoptlen;
    const uint8_t *tls = (uint8_t *) (buffer + tlsPos);

    // Get SNI
    char sn[FQDN_LENGTH];
    memset(sn, 0, FQDN_LENGTH);
    *sn = 0;
    get_server_name(buffer, length, daddr, version, tls, sn);
    if (strlen(sn) == 0) {
        log_print(PLATFORM_LOG_PRIORITY_INFO, "TLS server name not found");
        return 1;
    }

    log_print(PLATFORM_LOG_PRIORITY_INFO, "TLS server %s (%s) found", sn, dest);

    // Get UID natively if we can; otherwise we will get it in Kotlin
    jint uid = -1;
    if (SDK <= 28) // Android 9 Pie
        uid = get_uid(version, IPPROTO_TCP, saddr, sport, daddr, dport);

    // Prep call to Kotlin
    jstring jsource = (*env)->NewStringUTF(env, source);
    jstring jdest = (*env)->NewStringUTF(env, dest);
    jstring jsni = (*env)->NewStringUTF(env, sn);

    jboolean jallow = (*env)->CallBooleanMethod(
            env, GO_BACKEND, MID_SHOULD_ALLOW,
            IPPROTO_TCP, jsource, sport, jdest, dport, jsni, uid);
    jniCheckException(env);

    // Cleanup
    (*env)->DeleteLocalRef(env, jsource);
    (*env)->DeleteLocalRef(env, jdest);
    (*env)->DeleteLocalRef(env, jsni);

    if (jallow == JNI_FALSE)
        return 0;

    return 1;
}

JNIEXPORT void JNICALL Java_com_wireguard_android_backend_GoBackend_wgTurnOff(JNIEnv *env, jclass c, jint handle)
{
    wgTurnOff(handle);

    // Cleanup globals
    if (GO_BACKEND != NULL)
        (*env)->DeleteGlobalRef(env, GO_BACKEND);

    cleanup_uid_cache();
    wg_close_pcap();
}

JNIEXPORT jint JNICALL Java_com_wireguard_android_backend_GoBackend_wgGetSocketV4(JNIEnv *env, jclass c, jint handle)
{
    return wgGetSocketV4(handle);
}

JNIEXPORT jint JNICALL Java_com_wireguard_android_backend_GoBackend_wgGetSocketV6(JNIEnv *env, jclass c, jint handle)
{
    return wgGetSocketV6(handle);
}

JNIEXPORT jstring JNICALL Java_com_wireguard_android_backend_GoBackend_wgGetConfig(JNIEnv *env, jclass c, jint handle)
{
    jstring ret;
    char *config = wgGetConfig(handle);
    if (!config)
        return NULL;
    ret = (*env)->NewStringUTF(env, config);
    free(config);
    return ret;
}

JNIEXPORT jstring JNICALL Java_com_wireguard_android_backend_GoBackend_wgVersion(JNIEnv *env, jclass c)
{
    jstring ret;
    char *version = wgVersion();
    if (!version)
        return NULL;
    ret = (*env)->NewStringUTF(env, version);
    free(version);
    return ret;
}

JNIEXPORT void JNICALL Java_com_wireguard_android_backend_GoBackend_wgPcap(
    JNIEnv *env,
    jclass c,
    jstring name_,
    jint record_size,
    jint file_size
) {

    pcap_record_size = (size_t) record_size;
    pcap_file_size = file_size;

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
}

int wg_write_pcap(const uint8_t *buffer, size_t length) {
    if (pcap_file == NULL) return -1;

    write_pcap_rec(buffer, length);

    return 0;
}

int wg_close_pcap() {
    if (pcap_file == NULL) return 0;
    if (fclose(pcap_file)) {
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "PCAP fclose error %d: %s", errno, strerror(errno));
        return -1;
    }

    return 0;
}
