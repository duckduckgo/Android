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

package com.duckduckgo.privacy.config.store.features.trackinglinkdetection

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.privacy.config.api.TrackingLinkException
import com.duckduckgo.privacy.config.store.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

interface TrackingLinkDetectionRepository {
    fun updateAll(exceptions: List<TrackingLinkExceptionEntity>, ampLinkFormats: List<AmpLinkFormatEntity>, ampKeywords: List<AmpKeywordEntity>)
    val exceptions: List<TrackingLinkException>
    val ampLinkFormats: List<Regex>
    val ampKeywords: List<String>
}

class RealTrackingLinkDetectionRepository(
    val database: PrivacyConfigDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider
) : TrackingLinkDetectionRepository {

    private val trackingLinkDetectionDao: TrackingLinkDetectionDao = database.trackingLinkDetectionDao()

    override val exceptions = CopyOnWriteArrayList<TrackingLinkException>()
    override val ampLinkFormats = CopyOnWriteArrayList<Regex>()
    override val ampKeywords = CopyOnWriteArrayList<String>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            loadToMemory()
        }
    }

    override fun updateAll(
        exceptions: List<TrackingLinkExceptionEntity>,
        ampLinkFormats: List<AmpLinkFormatEntity>,
        ampKeywords: List<AmpKeywordEntity>
    ) {
        trackingLinkDetectionDao.updateAll(exceptions, ampLinkFormats, ampKeywords)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        trackingLinkDetectionDao.getAllExceptions().map {
            exceptions.add(it.toTrackingLinkException())
        }

        ampLinkFormats.clear()
        trackingLinkDetectionDao.getAllAmpLinkFormats().map {
            ampLinkFormats.add(it.format.toRegex(RegexOption.IGNORE_CASE))
        }

        ampKeywords.clear()
        trackingLinkDetectionDao.getAllAmpKeywords().map {
            ampKeywords.add(it.keyword)
        }
    }
}
