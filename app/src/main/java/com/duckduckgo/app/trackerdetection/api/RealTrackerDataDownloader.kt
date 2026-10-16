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

package com.duckduckgo.app.trackerdetection.api

import com.duckduckgo.app.trackerdetection.Client.ClientName.*
import com.duckduckgo.app.trackerdetection.TrackerDataLoader
import com.duckduckgo.app.trackerdetection.db.TdsMetadataDao
import com.duckduckgo.app.trackerdetection.db.TrackerDetectionDatabase
import com.duckduckgo.common.utils.extensions.extractETag
import com.duckduckgo.common.utils.store.BinaryDataStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import io.reactivex.Completable
import logcat.logcat
import okhttp3.Headers
import okio.BufferedSource
import okio.ByteString.Companion.decodeHex
import java.io.IOException
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealTrackerDataDownloader @Inject constructor(
    private val trackerListService: TrackerListService,
    private val binaryDataStore: BinaryDataStore,
    private val trackerDataLoader: TrackerDataLoader,
    private val trackerDetectionDatabase: TrackerDetectionDatabase,
    private val metadataDao: TdsMetadataDao,
    @TrackerDetectionMoshi moshi: Moshi,
) : TrackerDataDownloader {

    private val tdsAdapter = moshi.adapter(TdsJson::class.java)

    override fun downloadTds(): Completable {
        return Completable.fromAction {
            logcat { "Downloading tds.json" }

            val call = trackerListService.tds()
            val response = call.execute()

            if (!response.isSuccessful) {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }

            val tdsJson = response.body()!!.use { parseTds(it.source()) }
                ?: throw IOException("Empty tds.json body")
            val eTag = response.headers().extractETag()
            val oldEtag = metadataDao.eTag()
            if (eTag != oldEtag) {
                logcat { "Updating tds data from server" }
                trackerDetectionDatabase.runInTransaction {
                    trackerDataLoader.persistTds(eTag, tdsJson)
                    trackerDataLoader.loadTrackers()
                }
            }
        }
    }

    override fun clearLegacyLists(): Completable {
        return Completable.fromAction {
            listOf(EASYLIST, EASYPRIVACY, TRACKERSALLOWLIST).forEach {
                if (binaryDataStore.hasData(it.name)) {
                    binaryDataStore.clearData(it.name)
                }
            }
            return@fromAction
        }
    }

    /**
     * Applies the same document-level rules as Retrofit's MoshiResponseBodyConverter (a UTF-8 BOM is
     * skipped, trailing content is rejected), which Moshi's JsonAdapter leaves to the caller.
     */
    private fun parseTds(source: BufferedSource): TdsJson? {
        if (source.rangeEquals(0, UTF8_BOM)) {
            source.skip(UTF8_BOM.size.toLong())
        }
        val reader = JsonReader.of(source)
        val tdsJson = tdsAdapter.fromJson(reader)
        if (reader.peek() != JsonReader.Token.END_DOCUMENT) {
            throw JsonDataException("JSON document was not fully consumed.")
        }
        return tdsJson
    }

    companion object {
        private val UTF8_BOM = "EFBBBF".decodeHex()
    }
}

fun Headers.extractETag(): String {
    return this["eTag"]?.removePrefix("W/")?.removeSurrounding("\"", "\"").orEmpty() // removes weak eTag validator
}
