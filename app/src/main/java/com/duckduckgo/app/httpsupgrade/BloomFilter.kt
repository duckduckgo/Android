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

package com.duckduckgo.app.httpsupgrade

class BloomFilter {

    private val nativePointer: Long

    init {
        System.loadLibrary("https-bloom-lib")
    }

    constructor(
        maxItems: Int,
        targetProbability: Double
    ) {
        nativePointer = createBloomFilter(maxItems, targetProbability)
    }

    constructor(
        path: String,
        bits: Int,
        maxItems: Int
    ) {
        nativePointer = createBloomFilterFromFile(path, bits, maxItems)
    }

    private external fun createBloomFilter(
        maxItems: Int,
        targetProbability: Double
    ): Long

    private external fun createBloomFilterFromFile(
        path: String,
        bits: Int,
        maxItems: Int
    ): Long

    fun add(element: String) {
        add(nativePointer, element)
    }

    private external fun add(
        nativePointer: Long,
        element: String
    )

    fun contains(element: String): Boolean {
        return contains(nativePointer, element)
    }

    private external fun contains(
        nativePointer: Long,
        element: String
    ): Boolean

    @Suppress("unused", "protectedInFinal")
    protected fun finalize() {
        releaseBloomFilter(nativePointer)
    }

    private external fun releaseBloomFilter(nativePointer: Long)
}
