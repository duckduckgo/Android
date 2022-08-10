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

package com.duckduckgo.app.global.api

import com.duckduckgo.app.global.api.PixelAddAtbQueryParamInterceptor.Companion.PIXELS_SET
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PixelAddAtbQueryParamInterceptorTest {

    private lateinit var pixelAddAtbQueryParamInterceptor: PixelAddAtbQueryParamInterceptor
    private val mockStatisticsDataStore: StatisticsDataStore = mock()
    private val mockVariantManager: VariantManager = mock()

    @Before
    fun setup() {
        pixelAddAtbQueryParamInterceptor = PixelAddAtbQueryParamInterceptor(
            mockStatisticsDataStore,
            mockVariantManager
        )
    }

    @Test
    fun whenSendPixelWithNameInSetThenAddAtbInfo() {
        givenVariant()
        givenAtbVariant()
        PIXELS_SET.forEach { pixelName ->
            val pixelUrl = String.format(PIXEL_TEMPLATE, pixelName)

            val result = pixelAddAtbQueryParamInterceptor.intercept(FakeChain(pixelUrl)).request.url

            assertNotNull(result.queryParameter("atb"))
        }
    }

    @Test
    fun whenSendPixelWithNameNotInSetThenDoNotAddAtbInfo() {
        givenVariant()
        givenAtbVariant()

        val pixelName = "pixel_name"
        assertTrue(!PIXELS_SET.contains(pixelName))

        val pixelUrl = String.format(PIXEL_TEMPLATE, pixelName)

        val result = pixelAddAtbQueryParamInterceptor.intercept(FakeChain(pixelUrl)).request.url

        assertNull(result.queryParameter("atb"))
    }

    private fun givenVariant() {
        whenever(mockVariantManager.getVariant()).thenReturn(Variant("variant", filterBy = { true }))
    }

    private fun givenAtbVariant() {
        whenever(mockStatisticsDataStore.atb).thenReturn(Atb("atb"))
    }

    companion object {
        private const val PIXEL_TEMPLATE = "https://improving.duckduckgo.com/t/%s_android_phone?appVersion=5.132.1"
    }
}
