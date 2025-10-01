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

package com.duckduckgo.networkprotection.impl.cohort

import com.duckduckgo.common.test.api.FakeChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.time.LocalDate

class NetpCohortPixelInterceptorTest {
    @Mock
    private lateinit var netpCohortStore: NetpCohortStore

    private lateinit var testee: NetpCohortPixelInterceptor

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = NetpCohortPixelInterceptor(netpCohortStore)
    }

    @Test
    fun whenCohortLocalDateIsNotSetThenDropNetpPixelUrl() {
        whenever(netpCohortStore.cohortLocalDate).thenReturn(null)
        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_netp_ev_enabled_d")

        val result = testee.intercept(FakeChain(pixelUrl))

        assertEquals("Dropped NetP pixel because no cohort is assigned", result.message)
        assertNotEquals(null, result.body)
    }

    @Test
    fun whenCohortLocalDateIsSetThenFireNetpEvPixelUrl() {
        whenever(netpCohortStore.cohortLocalDate).thenReturn(LocalDate.of(2023, 1, 1))
        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_netp_ev_enabled_d")

        val result = testee.intercept(FakeChain(pixelUrl))

        assertEquals(pixelUrl, result.request.url.toString())
        assertEquals("", result.message)
        assertEquals(null, result.body)
    }

    @Test
    fun whenCohortLocalDateIsSetThenFireNetpImpPixelUrl() {
        whenever(netpCohortStore.cohortLocalDate).thenReturn(LocalDate.of(2023, 1, 1))
        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_netp_imp_dialog_d")

        val result = testee.intercept(FakeChain(pixelUrl))

        assertEquals(pixelUrl, result.request.url.toString())
        assertEquals("", result.message)
        assertEquals(null, result.body)
    }

    @Test
    fun whenCohortLocalDateIsNotSetButPixelUrlIsNotNetpThenFireNonNetpPixelUrl() {
        whenever(netpCohortStore.cohortLocalDate).thenReturn(null)
        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_atp_ev_enabled_d")

        val result = testee.intercept(FakeChain(pixelUrl))

        assertEquals(pixelUrl, result.request.url.toString())
        assertEquals("", result.message)
        assertEquals(null, result.body)
    }

    @Test
    fun whenCohortLocalDateIsNotSetThenSendExemptedBackendApiErrorNetpPixelUrl() {
        whenever(netpCohortStore.cohortLocalDate).thenReturn(null)
        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_netp_ev_backend_api_error_device_registration_failed_c")

        val result = testee.intercept(FakeChain(pixelUrl))

        assertEquals(pixelUrl, result.request.url.toString())
        assertEquals("", result.message)
        assertEquals(null, result.body)
    }

    @Test
    fun whenCohortLocalDateIsNotSetThenSendExemptedWgErrorNetpPixelUrl() {
        whenever(netpCohortStore.cohortLocalDate).thenReturn(null)
        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_netp_ev_wireguard_error_unable_to_load_wireguard_library_d")

        val result = testee.intercept(FakeChain(pixelUrl))

        assertEquals(pixelUrl, result.request.url.toString())
        assertEquals("", result.message)
        assertEquals(null, result.body)
    }

    @Test
    fun whenCohortLocalDateIsNotSetThenSendExemptedVpnConflictPixelUrl() {
        whenever(netpCohortStore.cohortLocalDate).thenReturn(null)
        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_netp_imp_vpn_conflict_dialog_c")

        val result = testee.intercept(FakeChain(pixelUrl))

        assertEquals(pixelUrl, result.request.url.toString())
        assertEquals("", result.message)
        assertEquals(null, result.body)
    }

    @Test
    fun whenCohortLocalDateIsNotSetThenSendExemptedAlwaysOnConflictPixelUrl() {
        whenever(netpCohortStore.cohortLocalDate).thenReturn(null)
        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_netp_imp_always_on_conflict_dialog_d")

        val result = testee.intercept(FakeChain(pixelUrl))

        assertEquals(pixelUrl, result.request.url.toString())
        assertEquals("", result.message)
        assertEquals(null, result.body)
    }

    @Test
    fun whenCohortLocalDateIsNotSetThenSendExemptedInfoVpnPixelUrl() {
        whenever(netpCohortStore.cohortLocalDate).thenReturn(null)
        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_netp_imp_info_vpn_c")

        val result = testee.intercept(FakeChain(pixelUrl))

        assertEquals(pixelUrl, result.request.url.toString())
        assertEquals("", result.message)
        assertEquals(null, result.body)
    }

    @Test
    fun whenCohortLocalDateIsNotSetThenSendExemptedFaqsPixelUrl() {
        whenever(netpCohortStore.cohortLocalDate).thenReturn(null)
        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_netp_imp_faqs_d")

        val result = testee.intercept(FakeChain(pixelUrl))

        assertEquals(pixelUrl, result.request.url.toString())
        assertEquals("", result.message)
        assertEquals(null, result.body)
    }

    @Test
    fun `when cohort local date is not set then send exempted main VPN settings pressed pixel`() {
        whenever(netpCohortStore.cohortLocalDate).thenReturn(null)
        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_netp_ev_setting_pressed_c")

        val result = testee.intercept(FakeChain(pixelUrl))

        assertEquals(pixelUrl, result.request.url.toString())
        assertEquals("", result.message)
        assertEquals(null, result.body)
    }

    companion object {
        private const val PIXEL_TEMPLATE = "https://improving.duckduckgo.com/t/%s_android_phone?appVersion=5.135.0&test=1"
    }
}
