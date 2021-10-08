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

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.trackerdetection.api.TdsJson
import com.duckduckgo.app.trackerdetection.db.*
import com.duckduckgo.app.trackerdetection.model.TdsMetadata
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@WorkerThread
@ContributesMultibinding(AppObjectGraph::class)
class TrackerDataLoader @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val trackerDetector: TrackerDetector,
    private val tdsTrackerDao: TdsTrackerDao,
    private val tdsEntityDao: TdsEntityDao,
    private val tdsDomainEntityDao: TdsDomainEntityDao,
    private val tdsMetadataDao: TdsMetadataDao,
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val moshi: Moshi
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onApplicationCreated() {
        appCoroutineScope.launch { loadData() }
    }

    private fun loadData() {
        Timber.d("Loading tracker data")
        loadTds()
    }

    private fun loadTds() {
        val count = tdsTrackerDao.count()
        if (count == 0) {
            updateTdsFromFile()
        }
        loadTrackers()
    }

    private fun updateTdsFromFile() {
        Timber.d("Updating tds from file")
        val json = context.resources.openRawResource(R.raw.tds).bufferedReader().use { it.readText() }
        val adapter = moshi.adapter(TdsJson::class.java)
        persistTds(DEFAULT_ETAG, adapter.fromJson(json)!!)
    }

    fun persistTds(eTag: String, tdsJson: TdsJson) {
        appDatabase.runInTransaction {
            tdsMetadataDao.tdsDownloadSuccessful(TdsMetadata(eTag = eTag))
            tdsEntityDao.updateAll(tdsJson.jsonToEntities())
            tdsDomainEntityDao.updateAll(tdsJson.jsonToDomainEntities())
            tdsTrackerDao.updateAll(tdsJson.jsonToTrackers().values)
        }
    }

    fun loadTrackers() {
        val trackers = tdsTrackerDao.getAll()
        Timber.d("Loaded ${trackers.size} tds trackers from DB")
        val client = TdsClient(Client.ClientName.TDS, trackers)
        trackerDetector.addClient(client)
    }

    companion object {
        const val DEFAULT_ETAG = "961c7d692c985496126cad2d64231243"
    }
}
