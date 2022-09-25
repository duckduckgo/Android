/**
 * @file platform.h
 * @brief Platform-specific definitions.
 */

#ifndef PLATFORM_H
#define PLATFORM_H

extern int loglevel;

/**
* @brief Platform-specific debug priority levels.
*/
enum platform_logPriority {
    PLATFORM_LOG_PRIORITY_UNKNOWN = 0,
    PLATFORM_LOG_PRIORITY_DEFAULT,
    PLATFORM_LOG_PRIORITY_VERBOSE,
    PLATFORM_LOG_PRIORITY_DEBUG,
    PLATFORM_LOG_PRIORITY_INFO,
    PLATFORM_LOG_PRIORITY_WARN,
    PLATFORM_LOG_PRIORITY_ERROR
};

/**
 * @brief Platform-specific login function.
 */
void __platform_log_print(int prio, const char *tag, const char *fmt, ...);

#define log_print(prio, format, ...) do { if (prio >= loglevel) __platform_log_print(prio, "NetGuard.JNI", format, ##__VA_ARGS__); } while (0)

#endif // PLATFORM_H
