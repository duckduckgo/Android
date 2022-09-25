
#include "netguard.h"

static void get_server_name(
    const uint8_t *pkt,
    size_t length,
    void *daddr,
    uint8_t version,
    uint8_t *tcp_payload,
    char *server_name
);

void tls_sni_inspection(
    const struct arguments *args,
    const uint8_t *pkt,
    size_t length,
    void *daddr,
    uint8_t version,
    uint8_t *tcp_payload
) {
    char dest[INET6_ADDRSTRLEN + 1];
    inet_ntop(version == 4 ? AF_INET : AF_INET6, daddr, dest, sizeof(dest));
    char sn[256];
    memset(sn, 0, 256);
    *sn = 0;

    get_server_name(pkt, length, daddr, version, tcp_payload, sn);

    if (strlen(sn) == 0) {
        log_print(PLATFORM_LOG_PRIORITY_INFO, "TLS server name not found");
        return;
    }

    log_print(PLATFORM_LOG_PRIORITY_INFO, "TLS server %s (%s) found", sn, dest);

    sni_resolved(args, sn, dest);
}

static void get_server_name(
    const uint8_t *pkt,
    size_t length,
    void *daddr,
    uint8_t version,
    uint8_t *tcp_payload,
    char *server_name
) {
    // ensure length is 0
    *server_name = 0;

    char dest[INET6_ADDRSTRLEN + 1];
    inet_ntop(version == 4 ? AF_INET : AF_INET6, daddr, dest, sizeof(dest));

    // Check TLS client hello header
    uint8_t *tls = (uint8_t *) (tcp_payload + sizeof(struct tcphdr));
    uint8_t content_type = (uint8_t) *tls;
    if (content_type >= 20 && content_type <= 24){
        // extrac TLS versions
        uint8_t tls_major_version = (uint8_t) tls[1];
        uint8_t tls_minor_version = (uint8_t) tls[2];

        log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TLS header found %d, %d/%d", content_type, tls_major_version, tls_minor_version);

        if (tls_major_version < 0x03){
            log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TLS %d does not have SNI header", tls_major_version);
        } else if (content_type == 23) { // content type application data
            log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TLS application data for address %s", dest);
        } else if (content_type == 22) { // content type handshake
            // handshake packet type
            uint16_t tls_handshake_size = (tls[3] << 8 & 0xFF00) + (tls[4] & 0x00FF);
            if (length - (tls - pkt) < 5) {
                log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TLS header too short");
            } else if (tls[5] == 1) {
                log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TLS packet ClientHello msg found");

                // Extract host from ClientHello SNI extension header

                // this skips the TLS header, time and Client Random - and starts with the session ID length
                uint8_t index = 43;
                uint8_t session_id_len = tls[index++];
                index += session_id_len;

                uint16_t cipher_suite_len = (tls[index] << 8 & 0xFF00) + (tls[index + 1] & 0x00FF);
                index += 2;
                index += cipher_suite_len;

                uint16_t compression_method_len = tls[index++];
                index += compression_method_len;

                uint16_t extensions_len = (tls[index] << 8 & 0xFF00) + (tls[index + 1] & 0x00FF);
                index += 2;
                if (extensions_len == 0) {
                    log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TLS ClientHello, no extensions found");
                } else {
                    // Extension headers found
                    log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TLS ClientHello extensions found");

                    uint16_t searched = 0;
                    uint8_t found = 0;

                    while (searched < extensions_len && index < length) {
                        uint16_t extension_type = (tls[index] << 8 & 0xFF00) + (tls[index + 1] & 0x00FF);
                        index += 2;

                        // Extension type is SERVER_NAME_EXTENSION_TYPE
                        if (extension_type == 0) {
                            log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TLS ClientHello SNI found at %d", index);
                            found = 1;
                            break;
                        } else {
                            log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TLS extension type %d", extension_type);

                            uint16_t extension_len = (tls[index] << 8 & 0xFF00) + (tls[index + 1] & 0x00FF);
                            index += 2;
                            // skip to the next extension, if there is one
                            index += extensions_len;

                            // record number of extension bytes searched
                            // which is the current extension length + 4 (2 bytes for type, 2 bytes for length)
                            searched += extensions_len + 4;
                        }
                    }

                    if (found) {
                        // skip 5 bytes for data sizes we don't need to know about
                        index += 5;

                        uint16_t server_name_len = (tls[index] << 8 & 0xFF00) + (tls[index + 1] & 0x00FF);
                        index += 2;

                        memcpy(server_name, &tls[index], server_name_len);
                        server_name[server_name_len] = 0;

                        log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TLS server name (%d bytes) is %s (%s)", server_name_len, server_name, dest);
                    }

                }

            } else {
                log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TLS packet is not ClientHello msg %d", tls[5]);
            }
        } else {
            log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TLS packet is not handshake packet");
        }
    } else{
        log_print(PLATFORM_LOG_PRIORITY_DEBUG, "TLS header NOT found");
    }
}