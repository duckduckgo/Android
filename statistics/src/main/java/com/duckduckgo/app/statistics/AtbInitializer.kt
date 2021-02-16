/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.statistics

import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import kotlinx.coroutines.withTimeoutOrNull

interface AtbInitializerListener {

    /**
     * This method will be called before initializing the ATB
     */
    suspend fun beforeAtbInit()

    /**
     * @return the timeout in milliseconds after which [beforeAtbInit]
     * will be stopped
     */
    fun beforeAtbInitTimeoutMillis(): Long
}

class AtbInitializer(
    private val statisticsDataStore: StatisticsDataStore,
    private val statisticsUpdater: StatisticsUpdater,
    private val listeners: Set<AtbInitializerListener>
) {

    suspend fun initialize() {
        listeners.forEach {
            withTimeoutOrNull(it.beforeAtbInitTimeoutMillis()) {
                it.beforeAtbInit()
            }
        }

        initializeAtb()
    }

    private fun initializeAtb() {
        if (statisticsDataStore.hasInstallationStatistics) {
            statisticsUpdater.refreshAppRetentionAtb()
        } else {
            statisticsUpdater.initializeAtb()
        }
    }

}
