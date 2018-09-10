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

import com.duckduckgo.app.trackerdetection.Client.ClientName
import com.duckduckgo.app.trackerdetection.model.ResourceType
import timber.log.Timber


class AdBlockClient(override val name: ClientName) : Client {

    private val nativeClientPointer: Long
    private var rawDataPointer: Long
    private var processedDataPointer: Long

    init {
        System.loadLibrary("adblockclient-lib")
        nativeClientPointer = createClient()
        rawDataPointer = 0
        processedDataPointer = 0
    }

    private external fun createClient(): Long

    fun loadBasicData(data: ByteArray) {
        val timestamp = System.currentTimeMillis()
        Timber.d("Loading basic data for ${name.name}")
        rawDataPointer = loadBasicData(nativeClientPointer, data)
        Timber.d("Loading basic data for ${name.name} completed in ${System.currentTimeMillis() - timestamp}ms")
    }

    private external fun loadBasicData(clientPointer: Long, data: ByteArray): Long

    fun loadProcessedData(data: ByteArray) {
        val timestamp = System.currentTimeMillis()
        Timber.d("Loading preprocessed data for ${name.name}")
        processedDataPointer = loadProcessedData(nativeClientPointer, data)
        Timber.d("Loading preprocessed data for ${name.name} completed in ${System.currentTimeMillis() - timestamp}ms")
    }

    private external fun loadProcessedData(clientPointer: Long, data: ByteArray): Long

    fun getProcessedData(): ByteArray = getProcessedData(nativeClientPointer)

    private external fun getProcessedData(clientPointer: Long): ByteArray

    override fun matches(url: String, documentUrl: String, resourceType: ResourceType): Boolean =
        matches(nativeClientPointer, url, documentUrl, resourceType.filterOption)

    private external fun matches(clientPointer: Long, url: String, documentUrl: String, filterOption: Int): Boolean


    @Suppress("unused", "protectedInFinal")
    protected fun finalize() {
        releaseClient(nativeClientPointer, rawDataPointer, processedDataPointer)
    }

    private external fun releaseClient(clientPointer: Long, rawDataPointer: Long, processedDataPointer: Long)

}
