/* Copyright (c) 2015 Brian R. Bondy. Distributed under the MPL2 license.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include <string.h>
#include "BloomFilter.h"

BloomFilter::BloomFilter(unsigned int bitsPerElement,
    unsigned int estimatedNumElements, HashFn *hashFns, int numHashFns) :
    hashFns(nullptr), numHashFns(0), byteBufferSize(0), buffer(nullptr) {
  this->hashFns = hashFns;
  this->numHashFns = numHashFns;
  lastHashes = new uint64_t[numHashFns];
  byteBufferSize = bitsPerElement * estimatedNumElements / 8 + 1;
  bitBufferSize = byteBufferSize * 8;
  buffer = new char[byteBufferSize];
  memset(buffer, 0, byteBufferSize);
}

// Constructs a BloomFilter by copying the specified buffer and number of bytes
BloomFilter::BloomFilter(const char *buffer, int byteBufferSize,
    HashFn *hashFns, int numHashFns) :
    hashFns(nullptr), numHashFns(0), byteBufferSize(0), buffer(nullptr) {
  this->hashFns = hashFns;
  this->numHashFns = numHashFns;
  lastHashes = new uint64_t[numHashFns];
  this->byteBufferSize = byteBufferSize;
  bitBufferSize = byteBufferSize * 8;
  this->buffer = new char[byteBufferSize];
  memcpy(this->buffer, buffer, byteBufferSize);
}

BloomFilter::~BloomFilter() {
  if (buffer) {
    delete[] buffer;
  }
  if (lastHashes) {
    delete[] lastHashes;
  }
}

void BloomFilter::setBit(unsigned int bitLocation) {
  buffer[bitLocation / 8] |= 1 << bitLocation % 8;
}

bool BloomFilter::isBitSet(unsigned int bitLocation) {
  return !!(buffer[bitLocation / 8] & 1 << bitLocation % 8);
}

void BloomFilter::add(const char *input, int len) {
  for (int j = 0; j < numHashFns; j++) {
    setBit(hashFns[j](input, len) % bitBufferSize);
  }
}

void BloomFilter::add(const char *sz) {
  add(sz, static_cast<int>(strlen(sz)));
}

bool BloomFilter::exists(const char *input, int len) {
  bool allSet = true;
  for (int j = 0; j < numHashFns; j++) {
    allSet = allSet && isBitSet(hashFns[j](input, len) % bitBufferSize);
  }
  return allSet;
}

bool BloomFilter::exists(const char *sz) {
  return exists(sz, static_cast<int>(strlen(sz)));
}

void BloomFilter::getHashesForCharCodes(const char *input, int inputLen,
    uint64_t *lastHashes, uint64_t *newHashes, unsigned char lastCharCode) {
  for (int i = 0; i < numHashFns; i++) {
    if (lastHashes) {
      *(newHashes + i) = hashFns[i](input, inputLen,
          lastCharCode, *(lastHashes+i));
    } else {
      *(newHashes + i) = hashFns[i](input, inputLen);
    }
  }
}

bool BloomFilter::substringExists(const char *data, int dataLen,
    int substringLength) {
  unsigned char lastCharCode = 0;
  for (int i = 0; i < dataLen - substringLength + 1; i++) {
    getHashesForCharCodes(data + i, substringLength, i == 0
        ? nullptr : lastHashes, lastHashes, lastCharCode);
    bool allSet = true;
    for (int j = 0; j < numHashFns; j++) {
      allSet = allSet && isBitSet(lastHashes[j] % bitBufferSize);
    }
    if (allSet) {
      return true;
    }
    lastCharCode = data[i];
  }
  return false;
}

bool BloomFilter::substringExists(const char *data, int substringLength) {
  return substringExists(data, static_cast<int>(strlen(data)), substringLength);
}

void BloomFilter::clear() {
  memset(buffer, 0, byteBufferSize);
}
