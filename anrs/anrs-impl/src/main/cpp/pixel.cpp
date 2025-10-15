#include "android.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
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

void send_crash_handle_init_pixel() {
    const char* host = "improving.duckduckgo.com";
    char path[2048];
    sprintf(path, "/t/m_app_register_native_crash_handler_android?appVersion=%s&pn=%s&customTab=%s&webViewPackage=%s&webViewVersion=%s", appVersion, pname, isCustomTab ? "true" : "false", wvPackage, wvVersion);
    send_request(host, path);
    log_print(ANDROID_LOG_ERROR, "Native crash handler init pixel sent on %s", pname);
}
