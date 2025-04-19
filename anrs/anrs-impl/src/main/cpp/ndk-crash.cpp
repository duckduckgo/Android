#include "jni.h"
#include "android.h"
#include "pixel.h"

#include <csignal>
#include <cstdio>
#include <cstring>
#include <memory>
#include <cxxabi.h>
#include <unistd.h>

#define sizeofa(array) sizeof(array) / sizeof(array[0])

// sometimes signal handlers inlinux consume crashes entirely. For those cases we trigger a signal so that we ultimately
// crash properly
#define __NR_tgkill 270

// Caught signals
static const int SIGNALS_TO_CATCH[] = {
        SIGABRT,
        SIGBUS,
        SIGFPE,
        SIGSEGV,
        SIGILL,
        SIGSTKFLT,
        SIGTRAP,
};

// Signal handler context
struct CrashInContext {
    // Old handlers of signals that we restore on de-initialization. Keep values for all possible
    // signals, for unused signals nullptr value is stored.
    struct sigaction old_handlers[NSIG];
};

// Crash handler function signature
typedef void (*CrashSignalHandler)(int, siginfo*, void*);

// Global instance of context. As the app can only crash once per process lifetime, this can be global
static CrashInContext* crashInContext = nullptr;


// Main signal handling function.
static void native_crash_sig_handler(int signo, siginfo* siginfo, void* ctxvoid) {
    // Restoring an old handler to make built-in Android crash mechanism work.
    sigaction(signo, &crashInContext->old_handlers[signo], nullptr);

    // Log crash message
    __android_log_print(ANDROID_LOG_ERROR, "ndk-crash", "Terminating with uncaught exception of type %d", signo);
    send_crash_pixel();

    // sometimes signal handlers inlinux consume crashes entirely. For those cases we trigger a signal so that we ultimately
    // crash properly, ie. to run standard bionic handler
    if (siginfo->si_code <= 0 || signo == SIGABRT) {
        if (syscall(__NR_tgkill, getpid(), gettid(), signo) < 0) {
            _exit(1);
        }
    }
}

// Register signal handler for crashes
static bool register_sig_handler(CrashSignalHandler handler, struct sigaction old_handlers[NSIG]) {
    struct sigaction sigactionstruct;
    memset(&sigactionstruct, 0, sizeof(sigactionstruct));
    sigactionstruct.sa_flags = SA_SIGINFO;
    sigactionstruct.sa_sigaction = handler;

    // Register new handlers for all signals
    for (int index = 0; index < sizeofa(SIGNALS_TO_CATCH); ++index) {
        const int signo = SIGNALS_TO_CATCH[index];

        if (sigaction(signo, &sigactionstruct, &old_handlers[signo])) {
            return false;
        }
    }

    return true;
}

// Unregister already register signal handler
static void unregister_sig_handler(struct sigaction old_handlers[NSIG]) {
    // Recover old handler for all signals
    for (int signo = 0; signo < NSIG; ++signo) {
        const struct sigaction* old_handler = &old_handlers[signo];

        if (!old_handler->sa_handler) {
            continue;
        }

        sigaction(signo, old_handler, nullptr);
    }
}

bool native_crash_handler_fini() {
    // Check if already deinitialized
    if (!crashInContext) return false;

    // Unregister signal handlers
    unregister_sig_handler(crashInContext->old_handlers);

    // Free singleton crash handler context
    free(crashInContext);
    crashInContext = nullptr;

    log_print(ANDROID_LOG_ERROR, "Native crash handler successfully deinitialized.");

    return true;
}

bool native_crash_handler_init() {
    // Check if already initialized
    if (crashInContext) {
        log_print(ANDROID_LOG_INFO, "Native crash handler is already initialized.");
        return false;
    }

    // Initialize singleton crash handler context
    crashInContext = static_cast<CrashInContext *>(malloc(sizeof(CrashInContext)));
    memset(crashInContext, 0, sizeof(CrashInContext));

    // Trying to register signal handler.
    if (!register_sig_handler(&native_crash_sig_handler, crashInContext->old_handlers)) {
        native_crash_handler_fini();
        log_print(ANDROID_LOG_ERROR, "Native crash handler initialization failed.");
        return false;
    }

    return true;
}
