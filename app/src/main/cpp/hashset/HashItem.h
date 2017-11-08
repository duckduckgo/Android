/* Copyright (c) 2015 Brian R. Bondy. Distributed under the MPL2 license.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef HASHITEM_H_
#define HASHITEM_H_

#include "./base.h"

template<class T>
class HashItem {
 public:
  HashItem() : next(nullptr), hashItemStorage(nullptr) {
  }

  ~HashItem() {
    if (hashItemStorage) {
      delete hashItemStorage;
    }
  }

  HashItem *next;
  T *hashItemStorage;
};

#endif  // HASHITEM_H_
