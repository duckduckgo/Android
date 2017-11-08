/* Copyright (c) 2015 Brian R. Bondy. Distributed under the MPL2 license.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// You should probably not be using this.  This is only useful in environments
// without std lib and having specific serialization and memory requirements.
// Instead consider using `hash_set` which is a more generic implementation
// with templates.

#ifndef HASHSET_H_
#define HASHSET_H_

#include <stdint.h>
#include <math.h>
#include <string.h>
#include <stdio.h>
#include "./base.h"
#include "./HashItem.h"

template<class T>
class HashSet {
 public:
  typedef uint64_t (*HashSetFnPtr)(const T *hashItem);

  explicit HashSet(uint32_t bucketCount) {
    init(bucketCount);
  }

  ~HashSet() {
    cleanup();
  }

  /**
   * Adds the specified data if it doesn't exist
   * A copy of the data will be created, so the memory used to do the add
   * doesn't need to stick around.
   *
   * @param newHashItem The node to add
   * @param updateIfExists true if the item should be updated if it is already there
   *   false if the add should fail if it is alraedy there.
   * @return true if the data was added
   */
  bool add(const T &itemToAdd, bool updateIfExists = true) {
    uint64_t hash = itemToAdd.hash();
    HashItem<T> *hashItem = buckets[hash % bucketCount];
    if (!hashItem) {
      hashItem = new HashItem<T>();
      hashItem->hashItemStorage = new T(itemToAdd);
      buckets[hash % bucketCount] = hashItem;
      _size++;
      return true;
    }

    while (true) {
      if (*hashItem->hashItemStorage == itemToAdd) {
        if (updateIfExists) {
          hashItem->hashItemStorage->update(itemToAdd);
        }
        return false;
      }
      if (!hashItem->next) {
        HashItem<T> *createdHashItem = new HashItem<T>();
        createdHashItem->hashItemStorage = new T(itemToAdd);
        hashItem->next = createdHashItem;
        break;
      }
      hashItem = hashItem->next;
    }

    _size++;
    return true;
  }

  /**
   * Determines if the specified data exists in the set or not`
   * @param dataToCheck The data to check
   * @return true if the data found
   */
  bool exists(const T &dataToCheck) {
    uint64_t hash = dataToCheck.hash();
    HashItem<T> *hashItem = buckets[hash % bucketCount];
    if (!hashItem) {
      return false;
    }

    while (hashItem) {
      if (*hashItem->hashItemStorage == dataToCheck) {
        return true;
      }
      hashItem = hashItem->next;
    }

    return false;
  }

  /**
   * Finds the specific data in the hash set.
   * This is useful because sometimes it contains more context
   * than the object used for the lookup.
   * @param dataToCheck The data to check
   * @return The data stored in the hash set or nullptr if none is found.
   */
  T * find(const T &dataToCheck) {
    uint64_t hash = dataToCheck.hash();
    HashItem<T> *hashItem = buckets[hash % bucketCount];
    if (!hashItem) {
      return nullptr;
    }

    while (hashItem) {
      if (*hashItem->hashItemStorage == dataToCheck) {
        return hashItem->hashItemStorage;
      }
      hashItem = hashItem->next;
    }

    return nullptr;
  }

  /**
   * Removes the specific data in the hash set.
   * @param dataToCheck The data to remove
   * @return true if an item matching the data was removed
   */
  bool remove(const T &dataToCheck) {
    uint64_t hash = dataToCheck.hash();
    HashItem<T> *hashItem = buckets[hash % bucketCount];
    if (!hashItem) {
      return false;
    }

    HashItem<T> *lastItem = nullptr;
    while (hashItem) {
      if (*hashItem->hashItemStorage == dataToCheck) {
        if (lastItem) {
          lastItem->next = hashItem->next;
          delete hashItem;
        } else {
          buckets[hash % bucketCount] = hashItem->next;
          delete hashItem;
        }
        _size--;
        return true;
      }

      lastItem = hashItem;
      hashItem = hashItem->next;
    }

    return false;
  }


  /**
   * Obtains the number of items in the Hash Set
   */
  uint32_t size() {
    return _size;
  }

  /**
   * Serializes the parsed data and bloom filter data into a single buffer.
   * @param size The size is returned in the out parameter if it's needed to
   * write to a file.
   * @return The returned buffer should be deleted by the caller.
   */
  char * serialize(uint32_t *size) {
     *size = 0;
     *size += serializeBuckets(nullptr);
     char *buffer = new char[*size];
     memset(buffer, 0, *size);
     serializeBuckets(buffer);
     return buffer;
  }

  /**
   * Deserializes the buffer.
   * Memory passed in will be used by this instance directly without copying
   * it in.
   * @param buffer The serialized data to deserialize
   * @param bufferSize the size of the buffer to deserialize
   * @return true if the operation was successful
   */
  bool deserialize(char *buffer, uint32_t bufferSize) {
    cleanup();
    uint32_t pos = 0;
    if (!hasNewlineBefore(buffer, bufferSize)) {
      return false;
    }
    sscanf(buffer + pos, "%x", &bucketCount);
    buckets = new HashItem<T> *[bucketCount];
    memset(buckets, 0, sizeof(HashItem<T>*) * bucketCount);
    pos += static_cast<uint32_t>(strlen(buffer + pos)) + 1;
    if (pos >= bufferSize) {
      return false;
    }
    for (uint32_t i = 0; i < bucketCount; i++) {
      HashItem<T> *lastHashItem = nullptr;
      while (*(buffer + pos) != '\0') {
        if (pos >= bufferSize) {
          return false;
        }

        HashItem<T> *hashItem = new HashItem<T>();
        hashItem->hashItemStorage = new T();
        uint32_t deserializeSize =
          hashItem->hashItemStorage->deserialize(buffer + pos,
              bufferSize - pos);
        pos += deserializeSize;
        if (pos >= bufferSize || deserializeSize == 0) {
          return false;
        }

        _size++;

        if (lastHashItem) {
          lastHashItem->next = hashItem;
        } else {
          buckets[i] = hashItem;
        }
        lastHashItem = hashItem;
      }
      pos++;
    }
    return true;
  }

  /**
   * Clears the HashSet back to the original dimensions but
   * with no data.
   */
  void clear() {
    auto oldBucketCount = bucketCount;
    cleanup();
    init(oldBucketCount);
  }

 private:
  bool hasNewlineBefore(char *buffer, uint32_t bufferSize) {
    char *p = buffer;
    for (uint32_t i = 0; i < bufferSize; ++i) {
      if (*p == '\0')
        return true;
      p++;
    }
    return false;
  }

  void init(uint32_t numBuckets) {
    bucketCount = numBuckets;
    buckets = nullptr;
    _size = 0;
    if (bucketCount != 0) {
      buckets = new HashItem<T>*[bucketCount];
      memset(buckets, 0, sizeof(HashItem<T>*) * bucketCount);
    }
  }

  void cleanup() {
    if (buckets) {
      for (uint32_t i = 0; i < bucketCount; i++) {
        HashItem<T> *hashItem = buckets[i];
        while (hashItem) {
          HashItem<T> *tempHashItem = hashItem;
          hashItem = hashItem->next;
          delete tempHashItem;
        }
      }
      delete[] buckets;
      buckets = nullptr;
      bucketCount = 0;
      _size = 0;
    }
  }

  uint32_t serializeBuckets(char *buffer) {
    uint32_t totalSize = 0;
    char sz[512];
    totalSize += 1 + snprintf(sz, sizeof(sz), "%x", bucketCount);
    if (buffer) {
      memcpy(buffer, sz, totalSize);
    }
    for (uint32_t i = 0; i < bucketCount; i++) {
      HashItem<T> *hashItem = buckets[i];
      while (hashItem) {
        if (buffer) {
          totalSize +=
            hashItem->hashItemStorage->serialize(buffer + totalSize);
        } else {
          totalSize += hashItem->hashItemStorage->serialize(nullptr);
        }
        hashItem = hashItem->next;
      }
      if (buffer) {
        buffer[totalSize] = '\0';
      }
      // Second null terminator to show next bucket
      totalSize++;
    }
    return totalSize;
  }

 protected:
  uint32_t bucketCount;
  HashItem<T> **buckets;
  uint32_t _size;
};

#endif  // HASHSET_H_
