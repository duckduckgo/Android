/* Copyright (c) 2015 Brian R. Bondy. Distributed under the MPL2 license.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BLOOMFILTER_H_
#define BLOOMFILTER_H_

#include <math.h>
#include <stdint.h>
#include "./hashFn.h"
#include "./base.h"

static HashFn h1(13);
static HashFn h2(17);
static HashFn h3(31);
static HashFn h4(41);
static HashFn h5(53);
static HashFn defaultHashFns[5] = {h1, h2, h3, h4, h5};


/**
 * Implements a Bloom Filter using Rabin Karp for char* buffer lookups
 */
class BloomFilter {
 public:
    BloomFilter(unsigned int bitsPerElement = 10,
        unsigned int estimatedNumElements = 50000,
        HashFn hashFns[] = defaultHashFns,
        int numHashFns = sizeof(defaultHashFns)/sizeof(defaultHashFns[0]));
    BloomFilter(const char *buffer, int byteBufferSize,
        HashFn hashFns[] = defaultHashFns,
        int numHashFns = sizeof(defaultHashFns)/sizeof(defaultHashFns[0]));
    virtual ~BloomFilter();
    // Sets the specified bit in the buffer
    void setBit(unsigned int bitLocation);
    // Checks if the specified bit is set in the buffer
    bool isBitSet(unsigned int bitLocation);
    // Adds the specified buffer to the bloom filter
    void add(const char *input, int len);
    void add(const char *sz);
    // Empty the Bloom Filter
    void clear();

    /**
     * Checks whether an element probably exists in the set, or definitely
     * doesn't.
     * @param sz Either a string to check for existance or an array of the
     *   string's char codes. The main reason why you'd want to pass in a char
     *   code array is because passing a string will use JS directly to get
     *   the char codes which is very inneficient compared to calling into C++
     *   code to get it and then making the call.
     *
     * Returns true if the element probably exists in the set
     * Returns false if the element definitely does not exist in the set
     */
    bool exists(const char *input, int len);
    bool exists(const char *sz);

    /**
     * Checks if any substring of length substringLength probably exists or
     * definitely doesn't. If false is returned then no substring of the
     * specified string of the specified length is in the bloom filter
     *
     * @param data The substring or char array to check substrings on.
     */
    bool substringExists(const char *data, int dataLen, int substringLength);
    bool substringExists(const char *sz, int substringLength);

    /**
     * Obtains the buffer used as the bloom filter data
     */
    const char * getBuffer() {
      return buffer;
    }

    /**
     * Obtains the Bloom Filter's buffer size in bytes
     */
    int getByteBufferSize() {
      return byteBufferSize;
    }

 private:
  HashFn *hashFns;
  uint64_t *lastHashes;
  int numHashFns;
  unsigned int byteBufferSize;
  unsigned int bitBufferSize;
  char *buffer;

  /**
   * Obtains the hashes for the specified charCodes
   * See "Rabin fingerprint" in
   * https://en.wikipedia.org/wiki/Rabin%E2%80%93Karp_algorithm
   * for more information.
   *
   * @param charCodes An array of the char codes to use for the hash
   * @param lastHashes Input and output for the last hash value
   * function for a faster computation.  Must be called with lastCharCode but
   * can be nullptr otherwise.
   *
   * @param newHashses fills in the corresponding new hashes, can be the same
   *  as lastHashes
   * @param lastCharCode if specified, it will pass the last char code
   *  to the hashing function for a faster computation. Must be called
   *  with lastHashes.
   */
  void getHashesForCharCodes(const char *input, int inputLen,
      uint64_t *lastHashes, uint64_t *newHashes, unsigned char lastCharCode);
};

#endif  // BLOOMFILTER_H_
