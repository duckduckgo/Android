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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.surrogates.store.ResourceSurrogateDataStore
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayInputStream
import javax.inject.Inject

@WorkerThread
@ContributesMultibinding(AppObjectGraph::class)
class ResourceSurrogateLoader @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val resourceSurrogates: ResourceSurrogates,
    private val surrogatesDataStore: ResourceSurrogateDataStore
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onApplicationCreated() {
        appCoroutineScope.launch { loadData() }
    }

    fun loadData() {
        Timber.v("Loading surrogate data")
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
            Timber.w(e, "Failed to parse surrogates file; file may be corrupt or badly formatted")
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
                Timber.d("Found new surrogate rule: $scriptId - $ruleName - $mimeType")
                nextLineIsNewRule = false
                return@forEach
            }

            if (it.isBlank()) {
                surrogates.add(
                    SurrogateResponse(
                        scriptId = scriptId,
                        name = ruleName,
                        mimeType = mimeType,
                        jsFunction = functionBuilder.toString()
                    )
                )

                functionBuilder.setLength(0)

                nextLineIsNewRule = true
                return@forEach
            }

            functionBuilder.append(it)
            functionBuilder.append("\n")
        }

        Timber.d("Processed ${surrogates.size} surrogates")
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
