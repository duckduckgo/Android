/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.common.utils.store

import android.content.Context
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.verifySha256
import javax.inject.Inject
import kotlinx.coroutines.withContext
import timber.log.Timber

class BinaryDataStore @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) {

    fun hasData(name: String): Boolean = context.fileExists(name)

    suspend fun loadData(name: String): ByteArray? {
        Timber.d("PERF METRICS: Loading data from File: $name")
        return withContext(dispatchers.io()) {
            try {
                context.openFileInput(name).use { it.readBytes() }
            } catch (e: Exception) {
                Timber.e("BinaryDataStore: Error loading data from file: $name, $e")
                null
            }
        }
    }

    fun getData(name: String): ByteArray? {
        return try {
            context.openFileInput(name).use { it.readBytes() }
        } catch (e: Exception) {
            Timber.e("BinaryDataStore: Error loading data from file: $name, $e")
            null
        }
    }

    fun saveData(
        name: String,
        byteArray: ByteArray,
    ) {
        try {
            context.openFileOutput(name, Context.MODE_PRIVATE).use { it.write(byteArray) }
        } catch (e: Exception) {
            Timber.e("BinaryDataStore: Error saving data to file: $name, $e")
        }
    }

    fun clearData(name: String) {
        try {
            context.deleteFile(name)
        } catch (e: Exception) {
            Timber.e("BinaryDataStore: Error clearing data for file: $name, $e")
        }
    }

    fun dataFilePath(name: String): String? {
        return context.filePath(name)
    }

    fun verifyCheckSum(
        name: String,
        sha256: String,
    ): Boolean {
        if (context.fileExists(name)) {
            return verifyCheckSum(getData(name) ?: "".toByteArray(), sha256)
        }
        return false
    }

    fun verifyCheckSum(
        bytes: ByteArray,
        sha256: String,
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
