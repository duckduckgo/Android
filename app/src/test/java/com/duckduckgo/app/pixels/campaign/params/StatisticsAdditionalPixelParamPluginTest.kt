/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.pixels.campaign.params

import com.duckduckgo.app.statistics.store.StatisticsDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class StatisticsAdditionalPixelParamPluginTest {
    @Test
    fun whenRuVariantSetThenPluginShouldReturnParamTrue() = runTest {
        val statisticsDataStore: StatisticsDataStore = mock()
        whenever(statisticsDataStore.variant).thenReturn("ru")
        val plugin = ReinstallAdditionalPixelParamPlugin(statisticsDataStore)

        Assert.assertEquals("isReinstall" to "true", plugin.params())
    }

    @Test
    fun whenVariantIsNotRuThenPluginShouldReturnParamFalse() = runTest {
        val statisticsDataStore: StatisticsDataStore = mock()
        whenever(statisticsDataStore.variant).thenReturn("atb-1234")
        val plugin = ReinstallAdditionalPixelParamPlugin(statisticsDataStore)

        Assert.assertEquals("isReinstall" to "false", plugin.params())
    }
}
