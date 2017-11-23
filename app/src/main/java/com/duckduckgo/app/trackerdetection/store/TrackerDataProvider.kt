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

package com.duckduckgo.app.trackerdetection.store

import android.content.Context
import com.duckduckgo.app.trackerdetection.Client.ClientName
import javax.inject.Inject

class TrackerDataProvider @Inject constructor(private val context: Context) {

    fun hasData(client: ClientName): Boolean {
        return context.fileExists(client.name)
    }

    fun loadData(client: ClientName): ByteArray {
        return context.openFileInput(client.name).use { it.readBytes() }
    }

    fun saveData(client: ClientName, byteArray: ByteArray) {
        context.openFileOutput(client.name, Context.MODE_PRIVATE).write(byteArray)
    }

    fun clearData(client: ClientName) {
        context.deleteFile(client.name)
    }

    fun clearAll() {
        for (client: ClientName in ClientName.values()) {
            context.deleteFile(client.name)
        }
    }

    private fun Context.fileExists(filename: String): Boolean {
        val file = getFileStreamPath(filename)
        return file != null && file.exists()
    }

}
