/*
 * Copyright (c) 2018 DuckDuckGo
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

import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.statistics.api.PixelService
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.pixels.ApiBasedPixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelDefinition.*
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock

class ApiBasedPixelTest {

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Mock
    val mockPixelService: PixelService = mock()

    @Mock
    val mockStatisticsDataStore: StatisticsDataStore = mock()

    @Mock
    val mockVariantManager: VariantManager = mock()

    @Test
    fun whenPixelFiredThenPixelServiceCalledWithCorrectAtbAndVariant() {
        whenever(mockPixelService.fire(any(), any())).thenReturn(Completable.complete())
        whenever(mockStatisticsDataStore.atb).thenReturn(Atb("atb"))
        whenever(mockVariantManager.getVariant()).thenReturn(Variant("variant"))

        val pixel = ApiBasedPixel(mockPixelService, mockStatisticsDataStore, mockVariantManager)
        pixel.fire(PRIVACY_DASHBOARD_OPENED)

        verify(mockPixelService).fire("mp", "atbvariant")
    }

    @Test
    fun whenPixelFiredThenPixelServiceCalledWithCorrectAtb() {
        whenever(mockPixelService.fire(any(), any())).thenReturn(Completable.complete())
        whenever(mockStatisticsDataStore.atb).thenReturn(Atb("atb"))
        whenever(mockVariantManager.getVariant()).thenReturn(VariantManager.DEFAULT_VARIANT)

        val pixel = ApiBasedPixel(mockPixelService, mockStatisticsDataStore, mockVariantManager)
        pixel.fire(FORGET_ALL_EXECUTED)

        verify(mockPixelService).fire("mf", "atb")
    }

    @Test
    fun whenPixelFiredThenPixelServiceCalledWithCorrectPixelName() {
        whenever(mockPixelService.fire(any(), any())).thenReturn(Completable.complete())

        val pixel = ApiBasedPixel(mockPixelService, mockStatisticsDataStore, mockVariantManager)
        pixel.fire(APP_LAUNCH)

        verify(mockPixelService).fire("ml", "")
    }

}