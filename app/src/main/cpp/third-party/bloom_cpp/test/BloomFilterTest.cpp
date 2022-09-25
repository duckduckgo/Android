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

#include <iostream>
#include <uuid/uuid.h>
#include "BloomFilter.hpp"

#include "Test.hpp"

using namespace std;

static const unsigned int FILTER_ELEMENT_COUNT = 5000;
static const unsigned int FILTER_ELEMENT_COUNT_NOT_DIVISIBLE_BY_8_BITS = 5111;
static const unsigned int ADDITIONAL_TEST_DATA_ELEMENT_COUNT = 5000;
static const double TARGET_ERROR_RATE = 0.001;
static const double ACCEPTABLE_ERROR_RATE = TARGET_ERROR_RATE * 2;
static const string FILE_PATH = "test-bloom-filter.bin";

static string createRandomString() {
    uuid_t id;
    uuid_generate(id);

    constexpr int MAX_UUID_UNPARSE_LENGTH = 37;
    char stringId[MAX_UUID_UNPARSE_LENGTH] = {};
    uuid_unparse(id, stringId);

    return stringId;
}

static set<string> createRandomStrings(unsigned int count) {
    set<string> set;
    for (unsigned int i = 0; i < count; i++) {
        set.insert(createRandomString());
    }
    return set;
}

static bool contains(set<string> set, const string &element) {
    return set.find(element) != set.end();
}

TEST_CASE("when BloomFilter is empty then contains is false") {
    BloomFilter testee(FILTER_ELEMENT_COUNT, TARGET_ERROR_RATE);
    REQUIRE_FALSE(testee.contains("abc"));
}

TEST_CASE("when BloomFilter contains element then contains is true") {
    BloomFilter testee(FILTER_ELEMENT_COUNT, TARGET_ERROR_RATE);
    testee.add("abc");
    REQUIRE(testee.contains("abc"));
}

TEST_CASE("when BloomFilter contains items then error is within range") {

    unsigned int falsePositives = 0, truePositives = 0, falseNegatives = 0, trueNegatives = 0;

    BloomFilter testee(FILTER_ELEMENT_COUNT, TARGET_ERROR_RATE);
    set<string> bloomData = createRandomStrings(FILTER_ELEMENT_COUNT);
    set<string> testData = createRandomStrings(ADDITIONAL_TEST_DATA_ELEMENT_COUNT);
    testData.insert(bloomData.begin(), bloomData.end());

    for (const auto &i : bloomData) {
        testee.add(i);
    }

    for (const auto &element : testData) {
        bool result = testee.contains(element);
        if (contains(bloomData, element) && !result) falseNegatives++;
        if (!contains(bloomData, element) && result) falsePositives++;
        if (!contains(bloomData, element) && !result) trueNegatives++;
        if (contains(bloomData, element) && result) truePositives++;
    }

    double errorRate = (falsePositives + falseNegatives) / (double) testData.size();
    REQUIRE(falseNegatives == 0);
    REQUIRE(truePositives == bloomData.size());
    REQUIRE(trueNegatives <= (testData.size() - bloomData.size()));
    REQUIRE(errorRate <= ACCEPTABLE_ERROR_RATE);
}

TEST_CASE("when BloomFilter is saved and reloaded then results are identical") {

    BloomFilter original(FILTER_ELEMENT_COUNT, TARGET_ERROR_RATE);
    set<string> bloomData = createRandomStrings(FILTER_ELEMENT_COUNT);
    set<string> testData = createRandomStrings(ADDITIONAL_TEST_DATA_ELEMENT_COUNT);
    testData.insert(bloomData.begin(), bloomData.end());

    for (const auto &i : bloomData) {
        original.add(i);
    }
    original.writeToFile(FILE_PATH);

    BloomFilter duplicate = BloomFilter(FILE_PATH, original.getBitCount(), FILTER_ELEMENT_COUNT);
    for (const auto &element : bloomData) {
        REQUIRE(original.contains(element) == duplicate.contains(element));
    }
}

TEST_CASE("when BloomFilter not divisible by 8 bits is saved and reloaded then results are identical") {

    BloomFilter original(FILTER_ELEMENT_COUNT_NOT_DIVISIBLE_BY_8_BITS, TARGET_ERROR_RATE);
    set<string> bloomData = createRandomStrings(FILTER_ELEMENT_COUNT);
    set<string> testData = createRandomStrings(ADDITIONAL_TEST_DATA_ELEMENT_COUNT);
    testData.insert(bloomData.begin(), bloomData.end());

    for (const auto &i : bloomData) {
        original.add(i);
    }
    original.writeToFile(FILE_PATH);

    BloomFilter duplicate = BloomFilter(FILE_PATH, original.getBitCount(), FILTER_ELEMENT_COUNT);
    for (const auto &element : bloomData) {
        REQUIRE(original.contains(element) == duplicate.contains(element));
    }
}