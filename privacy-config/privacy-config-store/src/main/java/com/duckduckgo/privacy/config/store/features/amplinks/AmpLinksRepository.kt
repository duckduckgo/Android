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

package com.duckduckgo.privacy.config.store.features.amplinks

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.privacy.config.api.AmpLinkException
import com.duckduckgo.privacy.config.store.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

interface AmpLinksRepository {
    fun updateAll(exceptions: List<AmpLinkExceptionEntity>, ampLinkFormats: List<AmpLinkFormatEntity>, ampKeywords: List<AmpKeywordEntity>)
    val exceptions: List<AmpLinkException>
    val ampLinkFormats: List<Regex>
    val ampKeywords: List<String>
}

class RealAmpLinksRepository(
    val database: PrivacyConfigDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider
) : AmpLinksRepository {

    private val ampLinksDao: AmpLinksDao = database.ampLinksDao()

    override val exceptions = CopyOnWriteArrayList<AmpLinkException>()
    override val ampLinkFormats = CopyOnWriteArrayList<Regex>()
    override val ampKeywords = CopyOnWriteArrayList<String>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            loadToMemory()
        }
    }

    override fun updateAll(
        exceptions: List<AmpLinkExceptionEntity>,
        ampLinkFormats: List<AmpLinkFormatEntity>,
        ampKeywords: List<AmpKeywordEntity>
    ) {
        ampLinksDao.updateAll(exceptions, ampLinkFormats, ampKeywords)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        ampLinksDao.getAllExceptions().map {
            exceptions.add(it.toAmpLinkException())
        }

        ampLinkFormats.clear()
        ampLinksDao.getAllAmpLinkFormats().map {
            ampLinkFormats.add(it.format.toRegex(RegexOption.IGNORE_CASE))
        }

        ampKeywords.clear()
        ampLinksDao.getAllAmpKeywords().map {
            ampKeywords.add(it.keyword)
        }
    }
}
