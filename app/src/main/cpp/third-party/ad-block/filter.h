/* Copyright (c) 2015 Brian R. Bondy. Distributed under the MPL2 license.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef FILTER_H_
#define FILTER_H_

#include <stdint.h>
#include <string.h>
#include "base.h"
#include "../bloom-filter-cpp/BloomFilter.h"

enum FilterType {
  FTNoFilterType = 0,
  FTRegex = 01,
  FTElementHiding = 02,
  FTElementHidingException = 04,
  FTHostAnchored = 010,
  FTLeftAnchored = 020,
  FTRightAnchored = 040,
  FTComment = 0100,
  FTException = 0200,
  FTEmpty = 0400,
  FTHostOnly = 01000,
  FTHTMLFiltering = 02000,
  FTListTypesMask = FTException|FTElementHiding|
    FTElementHidingException|FTEmpty|FTComment|
    FTHTMLFiltering,
};

enum FilterOption {
  FONoFilterOption = 0,
  FOScript = 01,
  FOImage = 02,
  FOStylesheet = 04,
  FOObject = 010,
  FOXmlHttpRequest = 020,
  FOObjectSubrequest = 040,
  FOSubdocument = 0100,
  FODocument = 0200,
  FOOther = 0400,
  FOXBL = 01000,
  FOCollapse = 02000,
  FODoNotTrack = 04000,
  FOElemHide = 010000,
  FOThirdParty = 020000,  // Used internally only, do not use
  FONotThirdParty = 040000,  // Used internally only, do not use
  FOPing = 0100000,  // Not supported, but we will ignore these rules
  FOResourcesOnly = FOScript|FOImage|FOStylesheet|FOObject|FOXmlHttpRequest|
    FOObjectSubrequest|FOSubdocument|FODocument|FOOther|FOXBL,
  FOUnsupported = FOPing
};

class Filter {
friend class AdBlockClient;
 public:
  Filter();
  Filter(const Filter &other);
  Filter(const char * data, int dataLen, char *domainList = nullptr,
      const char * host = nullptr, int hostLen = -1);

  Filter(FilterType filterType, FilterOption filterOption,
         FilterOption antiFilterOption,
         const char * data, int dataLen,
         char *domainList = nullptr, const char * host = nullptr,
         int hostLen = -1);

  ~Filter();

  // Swaps the data members for 'this' and the passed in filter
  void swapData(Filter *f);

  // Checks to see if any filter matches the input but does not match
  // any exception rule You may want to call the first overload to be
  // slighly more efficient
  bool matches(const char *input, int inputLen,
      FilterOption contextOption = FONoFilterOption,
      const char *contextDomain = nullptr,
      BloomFilter *inputBloomFilter = nullptr,
      const char *inputHost = nullptr, int inputHostLen = 0);
  bool matches(const char *input, FilterOption contextOption = FONoFilterOption,
      const char *contextDomain = nullptr,
      BloomFilter *inputBloomFilter = nullptr,
      const char *inputHost = nullptr, int inputHostLen = 0);

  // Nothing needs to be updated when a filter is added multiple times
  void update(const Filter &) {}
  bool hasUnsupportedOptions() const;

  // Checks to see if the filter options match for the passed in data
  bool matchesOptions(const char *input, FilterOption contextOption,
      const char *contextDomain = nullptr);

  void parseOptions(const char *input);
  bool containsDomain(const char *domain, bool anti = false) const;
  uint32_t getDomainCount(bool anti = false);

  uint64_t hash() const;

  bool operator==(const Filter &rhs) const {
    /*
     if (filterType != rhs.filterType || filterOption != rhs.filterOption ||
         antiFilterOption != rhs.antiFilterOption) {
      return false;
    }
    */

    int hostLen = 0;
    if (host) {
      hostLen = this->hostLen == -1 ?
        static_cast<int>(strlen(host)) : this->hostLen;
    }
    int rhsHostLen = 0;
    if (rhs.host) {
      rhsHostLen = rhs.hostLen == -1 ?
        static_cast<int>(strlen(rhs.host)) : rhs.hostLen;
    }

    if (hostLen != rhsHostLen) {
      return false;
    }

    return !memcmp(host, rhs.host, hostLen);
  }

  bool operator!=(const Filter &rhs) const {
    return !(*this == rhs);
  }

  uint32_t serialize(char *buffer);
  uint32_t deserialize(char *buffer, uint32_t bufferSize);

  // Holds true if the filter should not free memory because for example it
  // was loaded from a large buffer somewhere else via the serialize and
  // deserialize functions.
  bool borrowedData;

  FilterType filterType;
  FilterOption filterOption;
  FilterOption antiFilterOption;
  char *data;
  int dataLen;
  char *domainList;
  char *host;
  uint32_t domainCount;
  uint32_t antiDomainCount;
  int hostLen;

 protected:
  // Filters the domain list down to what's applicable for the context domain
  void filterDomainList(const char *domainList, char *destBuffer,
      const char *contextDomain, bool anti);
  // Checks for what is not excluded by the opposite list
  int getLeftoverDomainCount(const char *shouldBlockDomains,
      const char *shouldSkipDomains);

  // Parses a single option
  void parseOption(const char *input, int len);
};

bool isThirdPartyHost(const char *baseContextHost,
    int baseContextHostLen,
    const char *testHost,
    int testHostLen);

static inline bool isEndOfLine(char c) {
  return c == '\r' || c == '\n';
}

#endif  // FILTER_H_
