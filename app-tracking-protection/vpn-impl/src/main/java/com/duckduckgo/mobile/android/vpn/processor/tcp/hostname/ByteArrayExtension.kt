/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.processor.tcp.hostname

/**
 * Searches for the provided sequence
 * @param sequence to search for
 * @param offset to start the search from
 * @returns the index of this ByteArray after the sequence, or -1 if the sequence is empty or it was not found
 */
fun ByteArray.indexOf(sequence: ByteArray, offset: Int = 0): Int {
    // sanity check
    if (sequence.isEmpty() || offset >= this.size) {
        return -1
    }

    var matchIdx = 0
    for (i in offset until this.size) {
        if (this[i] == sequence[matchIdx]) {
            matchIdx++
            if (matchIdx == sequence.size)
                return i + 1
        } else {
            matchIdx = 0
        }
    }
    return -1
}
