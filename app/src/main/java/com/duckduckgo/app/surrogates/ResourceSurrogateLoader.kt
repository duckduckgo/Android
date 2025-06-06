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

package com.duckduckgo.app.surrogates

import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.surrogates.store.ResourceSurrogateDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import java.io.ByteArrayInputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

@WorkerThread
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class ResourceSurrogateLoader @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val resourceSurrogates: ResourceSurrogates,
    private val surrogatesDataStore: ResourceSurrogateDataStore,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) { loadData() }
    }

    fun loadData() {
        logcat(VERBOSE) { "Loading surrogate data" }
        if (surrogatesDataStore.hasData()) {
            val bytes = surrogatesDataStore.loadData()
            resourceSurrogates.loadSurrogates(convertBytes(bytes))
        }
    }

    @VisibleForTesting
    fun convertBytes(bytes: ByteArray): List<SurrogateResponse> {
        return try {
            parse(bytes)
        } catch (e: Throwable) {
            logcat(WARN) { "Failed to parse surrogates file; file may be corrupt or badly formatted: ${e.asLog()}" }
            emptyList()
        }
    }

    private fun parse(bytes: ByteArray): List<SurrogateResponse> {
        val surrogates = mutableListOf<SurrogateResponse>()
        val existingLines = readExistingLines(bytes)

        var nextLineIsNewRule = true

        var scriptId = ""
        var ruleName = ""
        var mimeType = ""
        val functionBuilder = StringBuilder()

        existingLines.forEach {
            if (it.startsWith("#")) {
                return@forEach
            }

            if (nextLineIsNewRule) {
                with(it.split(" ")) {
                    ruleName = this[0]
                    mimeType = this[1]
                }
                with(ruleName.split("/")) {
                    scriptId = this.last()
                }
                logcat { "Found new surrogate rule: $scriptId - $ruleName - $mimeType" }
                nextLineIsNewRule = false
                return@forEach
            }

            if (it.isBlank()) {
                surrogates.add(
                    SurrogateResponse(
                        scriptId = scriptId,
                        name = ruleName,
                        mimeType = mimeType,
                        jsFunction = functionBuilder.toString(),
                    ),
                )

                functionBuilder.setLength(0)

                nextLineIsNewRule = true
                return@forEach
            }

            functionBuilder.append(it)
            functionBuilder.append("\n")
        }

        logcat { "Processed ${surrogates.size} surrogates" }
        return surrogates
    }

    private fun readExistingLines(bytes: ByteArray): List<String> {
        val existingLines = ByteArrayInputStream(bytes).bufferedReader().use { reader ->
            reader.readLines().toMutableList()
        }

        if (existingLines.isNotEmpty() && existingLines.last().isNotBlank()) {
            existingLines.add("")
        }
        return existingLines
    }
}
