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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FeatureExceptions.FeatureException
import com.duckduckgo.privacy.config.store.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface TrackingParametersRepository {
    fun updateAll(exceptions: List<TrackingParameterExceptionEntity>, parameters: List<TrackingParameterEntity>)
    val exceptions: List<FeatureException>
    val parameters: List<String>
}

class RealTrackingParametersRepository(
    val database: PrivacyConfigDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : TrackingParametersRepository {

    private val trackingParametersDao: TrackingParametersDao = database.trackingParametersDao()

    override val exceptions = CopyOnWriteArrayList<FeatureException>()
    override val parameters = CopyOnWriteArrayList<String>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(
        exceptions: List<TrackingParameterExceptionEntity>,
        parameters: List<TrackingParameterEntity>,
    ) {
        trackingParametersDao.updateAll(exceptions, parameters)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        trackingParametersDao.getAllExceptions().map {
            exceptions.add(it.toFeatureException())
        }

        parameters.clear()
        trackingParametersDao.getAllTrackingParameters().map {
            parameters.add(it.parameter)
        }
    }
}
