/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.config.store.features.trackingparameters

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.privacy.config.api.TrackingParameterException
import com.duckduckgo.privacy.config.store.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

interface TrackingParametersRepository {
    fun updateAll(exceptions: List<TrackingParameterExceptionEntity>, parameters: List<TrackingParameterEntity>)
    val exceptions: List<TrackingParameterException>
    val parameters: List<Regex>
}

class RealTrackingParametersRepository(
    val database: PrivacyConfigDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider
) : TrackingParametersRepository {

    private val trackingParametersDao: TrackingParametersDao = database.trackingParametersDao()

    override val exceptions = CopyOnWriteArrayList<TrackingParameterException>()
    override val parameters = CopyOnWriteArrayList<Regex>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            loadToMemory()
        }
    }

    override fun updateAll(
        exceptions: List<TrackingParameterExceptionEntity>,
        parameters: List<TrackingParameterEntity>
    ) {
        trackingParametersDao.updateAll(exceptions, parameters)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        trackingParametersDao.getAllExceptions().map {
            exceptions.add(it.toTrackingParameterException())
        }

        parameters.clear()
        trackingParametersDao.getAllTrackingParameters().map {
            parameters.add(it.parameter.toRegex(RegexOption.IGNORE_CASE))
        }
    }
}
