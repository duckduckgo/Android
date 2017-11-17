/* Copyright (c) 2015 Brian R. Bondy. Distributed under the MPL2 license.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BASE_H_
#define BASE_H_

#if !defined(nullptr) && !defined(_MSC_VER)
#define nullptr 0
#endif

#include <stdint.h>

#if defined(_MSC_VER) && _MSC_VER < 1900
#include <stdarg.h>
#include <stdio.h>
#define snprintf c99_snprintf
#define vsnprintf c99_vsnprintf
inline int c99_vsnprintf(char *outBuf, size_t size,
    const char *format, va_list ap) {
  int count = -1;
  if (size != 0) {
    count = _vsnprintf_s(outBuf, size, _TRUNCATE, format, ap);
  }
  if (count == -1) {
    count = _vscprintf(format, ap);
  }
  return count;
}

inline int c99_snprintf(char *outBuf, size_t size,
    const char *format, ...) {
  int count;
  va_list ap;
  va_start(ap, format);
  count = c99_vsnprintf(outBuf, size, format, ap);
  va_end(ap);
  return count;
}
#endif

#endif  // BASE_H_
