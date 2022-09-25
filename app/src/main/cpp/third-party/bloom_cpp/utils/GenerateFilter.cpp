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

#include <fstream>
#include <iomanip>
#include <sstream>
#include <set>
#include <openssl/sha.h>

#include "BloomFilter.hpp"

using namespace std;


// Forward declarations

static set<string> readStringsFromFile(const string &fileName);

static void writeFalsePositivesToFile(const vector<string> &falsePositives, const string &fileName);

static string generateSha256(const string &fileName);

static string generateSpecification(size_t entries, size_t bitCount, double errorRate, const string &sha256);

static void replace(string &string, const std::string &fromString, const std::string &toString);


// Bloom generation script

int main(int argc, char *argv[]) {

    if (argc != 4) {
        cerr << "Usage: INPUT_FILE VALIDATION_FILE OUTPUT_FILES_PREFIX" << endl;
        return 1;
    }

    string bloomDataFile = argv[1];
    string validationDataFile = argv[2];
    string bloomOutputFile = string(argv[3]) + "-bloom.bin";
    string bloomSpecOutputFile = string(argv[3]) + "-bloom-spec.json";
    string falsePositivesOutputFile = string(argv[3]) + "-false-positives.json";
    double errorRate = 0.000001;

    cout << "Generating filter" << endl;
    set<string> bloomInput = readStringsFromFile(bloomDataFile);
    if (bloomInput.empty()) {
        cerr << "Error there was no data in " << bloomDataFile << endl;
        return 1;
    }

    BloomFilter filter(bloomInput.size(), errorRate);
    for (const string &entry : bloomInput) {
        filter.add(entry);
    }
    filter.writeToFile(bloomOutputFile);

    cout << "Reading generated filter for validation" << endl;
    filter = BloomFilter(bloomOutputFile, filter.getBitCount(), bloomInput.size());

    cout << "Validating data and generating false positives list" << endl;
    set<string> validationData = readStringsFromFile(validationDataFile);
    if (validationData.empty()) {
        cerr << "Error there was no data in " << validationDataFile << endl;
        return 1;
    }

    vector<string> falsePositives;
    for (const string &entry : validationData) {
        bool isInFilter = bloomInput.find(entry) != bloomInput.end();
        if (filter.contains(entry) && !isInFilter) {
            falsePositives.push_back(entry);
        }
        if (!filter.contains(entry) && isInFilter) {
            cerr << "Error false negative on" << entry << "this should not occur" << endl;
            return 1;
        }
    }
    writeFalsePositivesToFile(falsePositives, falsePositivesOutputFile);

    double actualErrorRate = falsePositives.size() / (double) validationData.size();
    cout << "Actual error rate was " << actualErrorRate << endl;

    cout << "Generating filter specification" << endl;
    string sha256 = generateSha256(bloomOutputFile);
    string spec = generateSpecification(bloomInput.size(), filter.getBitCount(), errorRate, sha256);
    ofstream specOutput(bloomSpecOutputFile);
    specOutput << spec;

    cout << "Done!" << endl;
}

static set<string> readStringsFromFile(const string &fileName) {

    set<string> data;

    string line;
    ifstream file(fileName);
    while (getline(file, line)) {
        if (!line.empty()) {
            data.insert(line);
        }
    }

    return data;
}

static void writeFalsePositivesToFile(const vector<string> &falsePositives, const string &fileName) {
    ofstream file(fileName);
    file << "{ \"data\": [" << endl;

    for (size_t i = 0; i < falsePositives.size(); i++) {
        file << "\"" << falsePositives[i] << "\"";
        if (i < falsePositives.size() - 1) {
            file << ",";
        }
        file << endl;
    }

    file << "]}";
}

static string generateSha256(const string &fileName) {

    ifstream file(fileName);
    string fileContents((istreambuf_iterator<char>(file)), istreambuf_iterator<char>());

    unsigned char hash[SHA256_DIGEST_LENGTH];
    SHA256_CTX sha256;
    SHA256_Init(&sha256);
    SHA256_Update(&sha256, fileContents.c_str(), fileContents.size());
    SHA256_Final(hash, &sha256);

    stringstream ss;
    for (auto element : hash) {
        ss << hex << setw(2) << setfill('0') << (int) element;
    }

    return ss.str();
}

static string generateSpecification(size_t entries, size_t bitCount, double errorRate, const string &sha256) {

    string specification = R"({
        "totalEntries" : ENTRIES,
        "bitCount"     : BIT_COUNT,
        "errorRate"    : ERROR,
        "sha256"       : "SHA256"
    })";

    replace(specification, "ENTRIES", to_string(entries));
    replace(specification, "BIT_COUNT", to_string(bitCount));
    replace(specification, "ERROR", to_string(errorRate));
    replace(specification, "SHA256", sha256);

    return specification;
}

static void replace(string &string, const std::string &fromString, const std::string &toString) {
    size_t fromIndex = string.find(fromString);
    size_t toIndex = fromString.length();
    string.replace(fromIndex, toIndex, toString);
}
