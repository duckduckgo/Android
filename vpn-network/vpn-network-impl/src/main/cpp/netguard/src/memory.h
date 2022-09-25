#ifndef MEMORY_H
#define MEMORY_H

#include <stdint.h>

void ng_add_alloc(const void *ptr, const char *tag);

void ng_delete_alloc(const void *ptr, const char *file, int line);

void *ng_malloc(size_t __byte_count, const char *tag);

void *ng_calloc(size_t __item_count, size_t __item_size, const char *tag);

void *ng_realloc(void *__ptr, size_t __byte_count, const char *tag);

void ng_free(void *__ptr, const char *file, int line);

void ng_dump();

#endif // MEMORY_H
