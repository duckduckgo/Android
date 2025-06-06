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

package com.duckduckgo.app.surrogates.api

import com.duckduckgo.app.surrogates.ResourceSurrogateLoader
import com.duckduckgo.app.surrogates.store.ResourceSurrogateDataStore
import com.duckduckgo.common.utils.extensions.isCached
import io.reactivex.Completable
import java.io.IOException
import javax.inject.Inject
import logcat.logcat

class ResourceSurrogateListDownloader @Inject constructor(
    private val service: ResourceSurrogateListService,
    private val surrogatesDataStore: ResourceSurrogateDataStore,
    private val resourceSurrogateLoader: ResourceSurrogateLoader,
) {

    fun downloadList(): Completable {
        return Completable.fromAction {
            logcat { "Downloading Google Analytics Surrogates data" }

            val call = service.surrogates()
            val response = call.execute()

            logcat { "Response received, success=${response.isSuccessful}" }

            if (response.isCached && surrogatesDataStore.hasData()) {
                logcat { "Surrogates data already cached and stored" }
                return@fromAction
            }

            if (response.isSuccessful) {
                val bodyBytes = response.body()!!.bytes()
                logcat { "Updating surrogates data store with new data" }
                persistData(bodyBytes)
                resourceSurrogateLoader.loadData()
            } else {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }
        }
    }

    private fun persistData(bodyBytes: ByteArray) {
        surrogatesDataStore.saveData(bodyBytes)
    }
}
