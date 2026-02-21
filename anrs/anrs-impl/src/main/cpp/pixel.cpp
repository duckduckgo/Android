#include "android.h"
#include "pixel.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>

#define BUFFER_SIZE 1024

static void send_request(const char* host, const char* path) {
    // Create socket
    int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) {
        log_print(ANDROID_LOG_ERROR, "Error opening socket");
        return;
    }

    // Resolve host name
    struct hostent *server = gethostbyname(host);
    if (server == NULL) {
        log_print(ANDROID_LOG_ERROR, "Error resolving host");
        close(sockfd);
        return;
    }

    // Fill in the address structure
    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    bcopy((char *)server->h_addr, (char *)&server_addr.sin_addr.s_addr, server->h_length);
    server_addr.sin_port = htons(80); // HTTP port

    // Connect to server
    if (connect(sockfd, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        log_print(ANDROID_LOG_ERROR, "Error connecting to server");
        close(sockfd);
        return;
    }

    // Send HTTP GET request
    char request[BUFFER_SIZE];
    snprintf(request, BUFFER_SIZE, "GET %s HTTP/1.1\r\nHost: %s\r\n\r\n", path, host);
    if (write(sockfd, request, strlen(request)) < 0) {
        log_print(ANDROID_LOG_ERROR, "Error writing to socket");
        close(sockfd);
        return;
    }

    // Close socket
    close(sockfd);
}

void send_crash_pixel() {
    const char* host = "improving.duckduckgo.com";
    char path[2048];
    sprintf(path, "/t/m_app_native_crash_android?appVersion=%s&pn=%s&customTab=%s&webViewPackage=%s&webViewVersion=%s", appVersion, pname, isCustomTab ? "true" : "false", wvPackage, wvVersion);
    send_request(host, path);
    log_print(ANDROID_LOG_ERROR, "Native crash pixel sent on %s", pname);
}

static void sanitize_and_extract_lib_name(const char* lib_name, char* safe_name, size_t safe_name_size) {
    if (!safe_name || safe_name_size == 0) {
        return;
    }
    safe_name[0] = '\0';

    if (!lib_name || lib_name[0] == '\0') {
        strncpy(safe_name, "unknown", safe_name_size - 1);
        safe_name[safe_name_size - 1] = '\0';
        return;
    }

    // Extract just the filename (strip directory path) for shorter, consistent URLs
    const char* start = lib_name;
    for (const char* p = lib_name; *p; ++p) {
        if (*p == '/') {
            start = p + 1;
        }
    }

    size_t j = 0;
    for (size_t i = 0; start[i] && j < safe_name_size - 1; ++i) {
        const char c = start[i];
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
            (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.') {
            safe_name[j++] = c;
        }
    }
    safe_name[j] = '\0';

    if (j == 0) {
        strncpy(safe_name, "unknown", safe_name_size - 1);
        safe_name[safe_name_size - 1] = '\0';
    }
}

void send_crash_pixel_with_location(int signo, const char* lib_name, uintptr_t offset) {
    const char* host = "improving.duckduckgo.com";
    char path[2048];
    char safe_lib_name[256];
    sanitize_and_extract_lib_name(lib_name, safe_lib_name, sizeof(safe_lib_name));
    snprintf(path, sizeof(path),
        "/t/m_app_native_crash_android"
        "?appVersion=%s"
        "&pn=%s"
        "&customTab=%s"
        "&webViewPackage=%s"
        "&webViewVersion=%s"
        "&sig=%d"
        "&lib=%s"
        "&offset=0x%lx",
        appVersion, pname, isCustomTab ? "true" : "false",
        wvPackage, wvVersion,
        signo, safe_lib_name, static_cast<unsigned long>(offset));
    send_request(host, path);
    log_print(ANDROID_LOG_ERROR, "Native crash pixel sent on %s: %s", pname, path);
}

void send_crash_handle_init_pixel() {
    const char* host = "improving.duckduckgo.com";
    char path[2048];
    sprintf(path, "/t/m_app_register_native_crash_handler_android?appVersion=%s&pn=%s&customTab=%s&webViewPackage=%s&webViewVersion=%s", appVersion, pname, isCustomTab ? "true" : "false", wvPackage, wvVersion);
    send_request(host, path);
    log_print(ANDROID_LOG_ERROR, "Native crash handler init pixel sent on %s", pname);
}
