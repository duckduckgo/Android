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

package com.duckduckgo.voice.impl.remoteconfig

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.voice.store.ManufacturerEntity
import com.duckduckgo.voice.store.MinVersionEntity
import com.duckduckgo.voice.store.VoiceSearchDao
import com.duckduckgo.voice.store.VoiceSearchDatabase
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface VoiceSearchFeatureRepository {
    fun updateAllExceptions(exceptions: List<Manufacturer>, minVersion: Int)
    val manufacturerExceptions: CopyOnWriteArrayList<Manufacturer>
    val minVersion: Int?
}

class RealVoiceSearchFeatureRepository constructor(
    database: VoiceSearchDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : VoiceSearchFeatureRepository {

    private val voiceSearchDao: VoiceSearchDao = database.voiceSearchDao()
    private var _minVersion: Int? = null
    override val manufacturerExceptions = CopyOnWriteArrayList<Manufacturer>()
    override val minVersion: Int?
        get() = _minVersion

    init {
        coroutineScope.launch(dispatcherProvider.io()) { loadToMemory() }
    }

    override fun updateAllExceptions(exceptions: List<Manufacturer>, minVersion: Int) {
        voiceSearchDao.updateAll(exceptions.map { ManufacturerEntity(it.name) }, MinVersionEntity(minVersion))
        loadToMemory()
    }

    private fun loadToMemory() {
        manufacturerExceptions.clear()
        val manufacturerExceptionsEntityList = voiceSearchDao.getAllExceptions()
        manufacturerExceptions.addAll(manufacturerExceptionsEntityList.map { Manufacturer(it.name) })
        _minVersion = voiceSearchDao.getMinVersion()?.minVersion
    }
}
