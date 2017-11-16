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


class AdBlockPlus : TrackerDetectionClient {

    companion object FilterOption {
        val None = 0
        val Script = 1
        val Image = 2
        val Stylesheet = 4
    }

    override val name: ClientName
    private val nativeClientPointer: Long

    init {
        System.loadLibrary("adblockplus-lib")
    }

    constructor(name: ClientName) {
        this.name = name
        nativeClientPointer = createClient()
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
        loadProcessedData(nativeClientPointer, data)
        Timber.v("Loading complete")
    }

    private external fun loadProcessedData(clientPointer: Long, data: ByteArray)

    fun getProcessedData(): ByteArray {
        return getProcessedData(nativeClientPointer)
    }

    private external fun getProcessedData(clientPointer: Long): ByteArray

    override fun matches(url: String, documentUrl: String): Boolean {

        var filterOption = FilterOption.None

        if (url.endsWith(".js", true)) {
            filterOption = FilterOption.Script
        } else if (url.endsWith(".css", true)) {
            filterOption = FilterOption.Stylesheet
        } else if (url.endsWith(".png", true) || url.endsWith(".jpg", true) || url.endsWith(".jpeg", true) || url.endsWith(".gif", true)) {
            filterOption = FilterOption.Image
        }

        return matches(nativeClientPointer, url, documentUrl, filterOption)
    }

    private external fun matches(clientPointer: Long, url: String, documentUrl: String, filterOption: Int): Boolean


    protected fun finalize() {
        releaseClient(nativeClientPointer)
    }

    private external fun releaseClient(clientPointer: Long)

}
