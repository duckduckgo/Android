/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection

import com.duckduckgo.app.trackerdetection.TrackerDetectionClient.ClientName
import timber.log.Timber


class AdBlockPlus(override val name: ClientName) : TrackerDetectionClient {

    private val nativeClientPointer: Long
    private var processedDataPointer: Long = 0

    init {
        System.loadLibrary("adblockplus-lib")
    }

    private external fun createClient(): Long

    fun loadBasicData(data: ByteArray) {
        Timber.v("Loading basic data")
        loadBasicData(nativeClientPointer, data)
        Timber.v("Loading complete")
    }

    private external fun loadBasicData(clientPointer: Long, data: ByteArray)

    fun loadProcessedData(data: ByteArray) {
        Timber.v("Loading preprocessed data")
        processedDataPointer = loadProcessedData(nativeClientPointer, data)
        Timber.v("Loading complete")
    }

    private external fun loadProcessedData(clientPointer: Long, data: ByteArray): Long

    fun getProcessedData(): ByteArray {
        return getProcessedData(nativeClientPointer)
    }

    private external fun getProcessedData(clientPointer: Long): ByteArray

    override fun matches(url: String, documentUrl: String, resourceType: ResourceType): Boolean {
        return matches(nativeClientPointer, url, documentUrl, resourceType.filterOption)
    }

    private external fun matches(clientPointer: Long, url: String, documentUrl: String, filterOption: Int): Boolean


    @SuppressWarnings("unused")
    protected fun finalize() {
        releaseClient(nativeClientPointer, processedDataPointer)
    }

    private external fun releaseClient(clientPointer: Long, processedDataPointer: Long)

    init {
        nativeClientPointer = createClient()
    }

}
