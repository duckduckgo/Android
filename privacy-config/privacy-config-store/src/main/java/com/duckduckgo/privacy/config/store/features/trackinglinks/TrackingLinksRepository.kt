/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.store.features.trackinglinks

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.privacy.config.api.TrackingLinksException
import com.duckduckgo.privacy.config.store.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

interface TrackingLinksRepository {
    fun updateAll(exceptions: List<TrackingLinksExceptionEntity>, ampLinkFormats: List<AmpLinkFormatEntity>, ampKeywords: List<AmpKeywordEntity>, trackingParameters: List<TrackingParameterEntity>)
    val exceptions: CopyOnWriteArrayList<TrackingLinksException>
    val ampLinkFormats: CopyOnWriteArrayList<String>
    val ampKeywords: CopyOnWriteArrayList<String>
    val trackingParameters: CopyOnWriteArrayList<String>
}

class RealTrackingLinksRepository(val database: PrivacyConfigDatabase, coroutineScope: CoroutineScope, dispatcherProvider: DispatcherProvider) :
    TrackingLinksRepository {

    private val trackingLinksDao: TrackingLinksDao = database.trackingLinksDao()

    override val exceptions = CopyOnWriteArrayList<TrackingLinksException>()
    override val ampLinkFormats = CopyOnWriteArrayList<String>()
    override val ampKeywords = CopyOnWriteArrayList<String>()
    override val trackingParameters = CopyOnWriteArrayList<String>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            loadToMemory()
        }
    }

    override fun updateAll(exceptions: List<TrackingLinksExceptionEntity>, ampLinkFormats: List<AmpLinkFormatEntity>, ampKeywords: List<AmpKeywordEntity>, trackingParameters: List<TrackingParameterEntity>) {
        trackingLinksDao.updateAll(exceptions, ampLinkFormats, ampKeywords, trackingParameters)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        trackingLinksDao.getAllExceptions().map {
            exceptions.add(it.toTrackingLinksException())
        }

        ampLinkFormats.clear()
        trackingLinksDao.getAllAmpLinkFormats().map {
            ampLinkFormats.add(it.format)
        }

        ampKeywords.clear()
        trackingLinksDao.getAllAmpKeywords().map {
            ampKeywords.add(it.keyword)
        }

        trackingParameters.clear()
        trackingLinksDao.getAllTrackingParameters().map {
            trackingParameters.add(it.parameter)
        }
    }
}
