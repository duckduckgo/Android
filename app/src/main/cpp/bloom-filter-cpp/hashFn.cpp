/* Copyright (c) 2015 Brian R. Bondy. Distributed under the MPL2 license.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "hashFn.h"

uint64_t HashFn::operator()(const char *input, int len,
      unsigned char lastCharCode, uint64_t lastHash) {
    // See the abracadabra example:
    // https://en.wikipedia.org/wiki/Rabin%E2%80%93Karp_algorithm
    return (lastHash - lastCharCode *
      customPow(&precomputedPowers, precompute, p, len - 1)) *
      p + input[len - 1];
  }

uint64_t HashFn::operator()(const char *input, int len) {
    uint64_t total = 0;
    for (int i = 0; i < len; i++) {
      total += input[i] *
        customPow(&precomputedPowers, precompute, p, len - i - 1);
    }
    return total;
  }
