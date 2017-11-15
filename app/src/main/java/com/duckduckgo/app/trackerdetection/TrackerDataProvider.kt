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

package com.duckduckgo.app.trackerdetection;

import android.content.Context
import com.duckduckgo.app.browser.R

class TrackerDataProvider constructor(private val context: Context) {

    companion object File {
        val easylistFile = "easylist"
        val easyprivacyFile = "easyprivacy"
    }

    val easylist: ByteArray
        get() = if (hasProcessedData) loadProcessedData(easylistFile) else loadRawData(R.raw.easylist)

    val easyprivacy: ByteArray
        get() = if (hasProcessedData) loadProcessedData(easyprivacyFile) else loadRawData(R.raw.easyprivacy)


    val hasProcessedData: Boolean
        get() = context.fileExists(easylistFile) && context.fileExists(easyprivacyFile)

    fun saveProcessedEasylistData(data: ByteArray) {
        saveProcessedData(data, easylistFile)
    }

    fun saveProcessedEasyprivacyData(data: ByteArray) {
        saveProcessedData(data, easyprivacyFile)
    }

    private fun saveProcessedData(byteArray: ByteArray, filename: String) {
        context.openFileOutput(filename, Context.MODE_PRIVATE).write(byteArray)
    }

    private fun loadProcessedData(filename: String): ByteArray {
        return context.openFileInput(filename).use { it.readBytes() }
    }

    private fun loadRawData(fileId: Int): ByteArray {
        return context.resources.openRawResource(fileId).use { it.readBytes() }
    }

    fun Context.fileExists(filename: String): Boolean {
        val file = getFileStreamPath(filename)
        return file != null && file.exists()
    }

    fun clearProcessedData() {
        context.deleteFile(easylistFile)
        context.deleteFile(easyprivacyFile)
    }
}
