#include "netguard.h"

///////////////////////////////////////////////////////////////////////////////
// Definitions
///////////////////////////////////////////////////////////////////////////////
struct alloc_record {
    const char *tag;
    time_t time;
    void *ptr;
};


int allocs = 0;
int balance = 0;
struct alloc_record *alloc = NULL;
pthread_mutex_t *alock = NULL;

///////////////////////////////////////////////////////////////////////////////
// Functions
///////////////////////////////////////////////////////////////////////////////

void ng_add_alloc(const void *ptr, const char *tag) {
#ifdef PROFILE_MEMORY
    if (ptr == NULL)
        return;

    if (alock == NULL) {
        alock = malloc(sizeof(pthread_mutex_t));
        if (pthread_mutex_init(alock, NULL))
            log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_init failed");
    }

    if (pthread_mutex_lock(alock))
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_lock failed");

    int c = 0;
    for (; c < allocs; c++)
        if (alloc[c].ptr == NULL)
            break;

    if (c >= allocs) {
        if (allocs == 0)
            alloc = malloc(sizeof(struct alloc_record));
        else
            alloc = realloc(alloc, sizeof(struct alloc_record) * (allocs + 1));
        c = allocs;
        allocs++;
    }

    alloc[c].tag = tag;
    alloc[c].time = time(NULL);
    alloc[c].ptr = ptr;
    balance++;

    if (pthread_mutex_unlock(alock))
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_unlock failed");
#endif
}

void ng_delete_alloc(const void *ptr, const char *file, int line) {
#ifdef PROFILE_MEMORY
    if (ptr == NULL)
        return;

    if (pthread_mutex_lock(alock))
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_lock failed");

    int found = 0;
    for (int c = 0; c < allocs; c++)
        if (alloc[c].ptr == ptr) {
            found = 1;
            alloc[c].tag = "[free]";
            alloc[c].ptr = NULL;
            break;
        }

    if (found == 1)
        balance--;

    log_print(found ? PLATFORM_LOG_PRIORITY_DEBUG : PLATFORM_LOG_PRIORITY_ERROR,
                "alloc/free balance %d records %d found %d", balance, allocs, found);
    if (found == 0)
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "Not found at %s:%d", file, line);

    if (pthread_mutex_unlock(alock))
        log_print(PLATFORM_LOG_PRIORITY_ERROR, "pthread_mutex_unlock failed");
#endif
}

void *ng_malloc(size_t __byte_count, const char *tag) {
    void *ptr = malloc(__byte_count);
    ng_add_alloc(ptr, tag);
    return ptr;
}

void *ng_calloc(size_t __item_count, size_t __item_size, const char *tag) {
    void *ptr = calloc(__item_count, __item_size);
    ng_add_alloc(ptr, tag);
    return ptr;
}

void *ng_realloc(void *__ptr, size_t __byte_count, const char *tag) {
    ng_delete_alloc(__ptr, NULL, 0);
    void *ptr = realloc(__ptr, __byte_count);
    ng_add_alloc(ptr, tag);
    return ptr;
}

void ng_free(void *__ptr, const char *file, int line) {
    ng_delete_alloc(__ptr, file, line);
    free(__ptr);
}

void ng_dump() {
    int r = 0;
    for (int c = 0; c < allocs; c++)
        if (alloc[c].ptr != NULL)
            log_print(PLATFORM_LOG_PRIORITY_WARN,
                        "holding %d [%s] %s",
                        ++r, alloc[c].tag, ctime(&alloc[c].time));
}
