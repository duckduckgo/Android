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

package com.duckduckgo.mobile.android.vpn.cohort

import com.duckduckgo.app.global.api.FakeChain
import com.duckduckgo.app.global.api.InMemorySharedPreferences
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.threeten.bp.LocalDate

class CohortPixelInterceptorTest {
    private lateinit var cohortPixelInterceptor: CohortPixelInterceptor
    private lateinit var cohortStore: CohortStore
    private lateinit var cohortCalculator: CohortCalculator

    private val sharedPreferencesProvider = mock<VpnSharedPreferencesProvider>()

    @Before
    fun setup() {
        val prefs = InMemorySharedPreferences()
        whenever(
            sharedPreferencesProvider.getSharedPreferences(eq("com.duckduckgo.mobile.atp.cohort.prefs"), eq(true), eq(true)),
        ).thenReturn(prefs)

        cohortStore = RealCohortStore(sharedPreferencesProvider)
        cohortCalculator = RealCohortCalculator()
        cohortPixelInterceptor = CohortPixelInterceptor(cohortCalculator, cohortStore)
    }

    @Test
    fun whenCohortNotSetPixelDropped() {
        Assert.assertNull(cohortStore.getCohortStoredLocalDate())

        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_atp_breakage_report")
        val result = cohortPixelInterceptor.intercept(FakeChain(pixelUrl))

        Assert.assertEquals("Dropped ATP pixel because no cohort is assigned", result.message)
        Assert.assertNotEquals(null, result.body)
    }

    @Test
    fun whenCohortSetPixelFired() {
        val date = LocalDate.now().plusDays(3)
        cohortStore.setCohortLocalDate(date)

        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_atp_breakage_report")
        val result = cohortPixelInterceptor.intercept(FakeChain(pixelUrl))
        val resultUrl = result.request.url

        Assert.assertEquals(cohortCalculator.calculateCohortForDate(date), resultUrl.queryParameter(CohortPixelInterceptor.COHORT_PARAM))
        Assert.assertEquals("", result.message)
        Assert.assertEquals(null, result.body)
    }

    @Test
    fun whenCohortSetPixelFiredCohortRemovedForException() {
        val date = LocalDate.now().plusDays(3)
        cohortStore.setCohortLocalDate(date)

        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_atp_ev_cpu_usage_above_10")
        val result = cohortPixelInterceptor.intercept(FakeChain(pixelUrl))
        val resultUrl = result.request.url

        Assert.assertEquals(null, resultUrl.queryParameter(CohortPixelInterceptor.COHORT_PARAM))
        Assert.assertEquals("", result.message)
        Assert.assertEquals(null, result.body)
    }

    companion object {
        private const val PIXEL_TEMPLATE = "https://improving.duckduckgo.com/t/%s_android_phone?appVersion=5.135.0&test=1"
    }
}
