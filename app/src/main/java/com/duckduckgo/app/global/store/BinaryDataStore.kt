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

package com.duckduckgo.app.global.store

import android.content.Context
import com.duckduckgo.app.global.verifySha256
import javax.inject.Inject

class BinaryDataStore @Inject constructor(private val context: Context) {

    fun hasData(name: String): Boolean = context.fileExists(name)

    fun loadData(name: String): ByteArray =
        context.openFileInput(name).use { it.readBytes() }

    fun saveData(
        name: String,
        byteArray: ByteArray
    ) {
        context.openFileOutput(name, Context.MODE_PRIVATE).use { it.write(byteArray) }
    }

    fun clearData(name: String) {
        context.deleteFile(name)
    }

    fun dataFilePath(name: String): String? {
        return context.filePath(name)
    }

    fun verifyCheckSum(
        name: String,
        sha256: String
    ): Boolean {
        if (context.fileExists(name)) {
            return verifyCheckSum(loadData(name), sha256)
        }
        return false
    }

    fun verifyCheckSum(
        bytes: ByteArray,
        sha256: String
    ): Boolean {
        return bytes.verifySha256(sha256)
    }

    private fun Context.filePath(filename: String): String? {
        val file = getFileStreamPath(filename)
        return if (file != null && file.exists()) file.path else null
    }

    private fun Context.fileExists(filename: String): Boolean {
        return filePath(filename) != null
    }
}
