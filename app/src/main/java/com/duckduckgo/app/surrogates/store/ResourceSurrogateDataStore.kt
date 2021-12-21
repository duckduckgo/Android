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

package com.duckduckgo.app.surrogates.store

import android.content.Context
import javax.inject.Inject

class ResourceSurrogateDataStore @Inject constructor(private val context: Context) {

    fun hasData(): Boolean = context.fileExists(FILENAME)

    fun loadData(): ByteArray = context.openFileInput(FILENAME).use { it.readBytes() }

    fun saveData(byteArray: ByteArray) {
        context.openFileOutput(FILENAME, Context.MODE_PRIVATE).use { it.write(byteArray) }
    }

    fun clearData() {
        context.deleteFile(FILENAME)
    }

    private fun Context.fileExists(filename: String): Boolean {
        val file = getFileStreamPath(filename)
        return file != null && file.exists()
    }

    companion object {
        private const val FILENAME = "surrogates.js"
    }
}
