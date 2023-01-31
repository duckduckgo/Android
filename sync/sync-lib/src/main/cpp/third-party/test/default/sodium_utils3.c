
#include <stdlib.h>
#include <sys/types.h>

#include <limits.h>
#ifdef HAVE_CATCHABLE_SEGV
# include <signal.h>
#endif
#ifndef _WIN32
# include <unistd.h>
#endif

#define TEST_NAME "sodium_utils3"
#include "cmptest.h"

#ifdef __SANITIZE_ADDRESS__
# warning The sodium_utils3 test is expected to fail with address sanitizer
#endif

__attribute__((noreturn)) static void
segv_handler(int sig)
{
    (void) sig;

    printf("Intentional segfault / bus error caught\n");
    printf("OK\n");
#ifdef SIG_DFL
# ifdef SIGSEGV
    signal(SIGSEGV, SIG_DFL);
# endif
# ifdef SIGBUS
    signal(SIGBUS, SIG_DFL);
# endif
# ifdef SIGABRT
    signal(SIGABRT, SIG_DFL);
# endif
#endif
    _exit(0);
}

int
main(void)
{
    void * buf;
    size_t size;

#ifdef SIG_DFL
# ifdef SIGSEGV
    signal(SIGSEGV, segv_handler);
# endif
# ifdef SIGBUS
    signal(SIGBUS, segv_handler);
# endif
# ifdef SIGABRT
    signal(SIGABRT, segv_handler);
# endif
#endif
    size = 1U + randombytes_uniform(100000U);
    buf  = sodium_malloc(size);
    assert(buf != NULL);

/* old versions of asan emit a warning because they don't support mlock*() */
#ifndef __SANITIZE_ADDRESS__
    sodium_mprotect_noaccess(buf);
    sodium_mprotect_readwrite(buf);
#endif

#if defined(HAVE_CATCHABLE_SEGV) && !defined(__EMSCRIPTEN__) && !defined(__SANITIZE_ADDRESS__)
    sodium_memzero(((unsigned char *) buf) - 8, 8U);
    sodium_mprotect_readonly(buf);
    sodium_free(buf);
    printf("Underflow not caught\n");
#else
    segv_handler(0);
#endif
    return 0;
}
