/*
 * Copyright (c) 2018 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <climits>
#include <cmath>
#include <cstdio>
#include <fstream>
#include "BloomFilter.hpp"

static const size_t BITS_PER_BLOCK = 8;

// Forward declarations

static void checkArchitecture();

static size_t calculateHashRounds(size_t size, size_t maxItems);

static unsigned int djb2Hash(const string &text);

static unsigned int sdbmHash(const string &text);

static unsigned int doubleHash(unsigned int hash1, unsigned int hash2, unsigned int round);

static vector<BlockType> readVectorFromFile(const string &path);

static vector<BlockType> readVectorFromStream(BinaryInputStream &in);


// Implementation

BloomFilter::BloomFilter(size_t maxItems, double targetProbability) {
    checkArchitecture();
    bitCount = (size_t) ceil((maxItems * log(targetProbability)) / log(1.0 / (pow(2.0, log(2.0)))));
    auto blocks = (size_t) ceil(bitCount / (double) BITS_PER_BLOCK);
    bloomVector = vector<BlockType>(blocks);
    hashRounds = calculateHashRounds(bitCount, maxItems);
}

BloomFilter::BloomFilter(const string &importFilePath, size_t bitCount, size_t maxItems) : bitCount(bitCount) {
    checkArchitecture();
    bloomVector = readVectorFromFile(importFilePath);
    hashRounds = calculateHashRounds(bitCount, maxItems);
}

BloomFilter::BloomFilter(BinaryInputStream &in, size_t bitCount, size_t maxItems) : bitCount(bitCount) {
    checkArchitecture();
    bloomVector = readVectorFromStream(in);
    hashRounds = calculateHashRounds(bitCount, maxItems);
}

static void checkArchitecture() {
    if (CHAR_BIT != BITS_PER_BLOCK) {
        throw std::runtime_error("Unsupported architecture: char is not 8 bit");
    }
}

static size_t calculateHashRounds(size_t size, size_t maxItems) {
    return (size_t) round(log(2.0) * size / maxItems);
}

void BloomFilter::add(const string &element) {
    unsigned int hash1 = djb2Hash(element);
    unsigned int hash2 = sdbmHash(element);

    for (size_t i = 0; i < hashRounds; i++) {
        unsigned int hash = doubleHash(hash1, hash2, i);
        size_t bitIndex = hash % bitCount;
        size_t blockIndex = bitIndex / BITS_PER_BLOCK;
        size_t blockOffset = bitIndex % BITS_PER_BLOCK;
        auto block = bloomVector[blockIndex];
        bloomVector[blockIndex] = block | (1 << blockOffset);
    }
}

bool BloomFilter::contains(const string &element) {
    unsigned int hash1 = djb2Hash(element);
    unsigned int hash2 = sdbmHash(element);

    for (size_t i = 0; i < hashRounds; i++) {
        unsigned int hash = doubleHash(hash1, hash2, i);
        size_t bitIndex = hash % bitCount;
        size_t blockIndex = bitIndex / BITS_PER_BLOCK;
        size_t blockOffset = bitIndex % BITS_PER_BLOCK;
        auto block = bloomVector[blockIndex];

        if ((block & (1 << blockOffset)) == 0) {
            return false;
        }
    }
    return true;
}

static unsigned int djb2Hash(const string &text) {
    unsigned int hash = 5381;
    for (const char &iterator : text) {
        hash = ((hash << 5) + hash) + iterator;
    }
    return hash;
}

static unsigned int sdbmHash(const string &text) {
    unsigned int hash = 0;
    for (const char &iterator : text) {
        hash = iterator + ((hash << 6) + (hash << 16) - hash);
    }
    return hash;
}

static unsigned int doubleHash(unsigned int hash1, unsigned int hash2, unsigned int round) {
    switch (round) {
        case 0:
            return hash1;
        case 1:
            return hash2;
        default:
            return (hash1 + (round * hash2) + (round ^ 2));
    }
}

void BloomFilter::writeToFile(const string &path) {
    basic_ofstream<BlockType> out(path.c_str(), ofstream::binary);
    writeToStream(out);
}

void BloomFilter::writeToStream(BinaryOutputStream &out) {
    out.write(&bloomVector[0], bloomVector.size() * sizeof(BlockType));
}

static vector<BlockType> readVectorFromFile(const string &path) {
    basic_ifstream<BlockType> inFile(path, ifstream::binary);
    return readVectorFromStream(inFile);
}

static vector<BlockType> readVectorFromStream(BinaryInputStream &in) {
    vector<BlockType> bloomVector((istreambuf_iterator<BlockType>(in)), istreambuf_iterator<BlockType>());
    return bloomVector;
}

size_t BloomFilter::getBitCount() const {
    return bitCount;
}