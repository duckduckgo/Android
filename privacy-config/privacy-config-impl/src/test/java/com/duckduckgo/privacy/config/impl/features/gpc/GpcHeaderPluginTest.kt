package com.duckduckgo.privacy.config.impl.features.gpc

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER_VALUE
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GpcHeaderPluginTest {

    private lateinit var testee: GpcHeaderPlugin

    private val mockGpc: Gpc = mock()

    @Test
    fun whenGetHeadersCalledWithUrlThenGpcGetHeadersIsCalledWithTheSameUrlAndHeadersReturned() {
        val url = "url"
        val gpcHeaders = mapOf(GPC_HEADER to GPC_HEADER_VALUE)
        whenever(mockGpc.getHeaders(url)).thenReturn(gpcHeaders)
        testee = GpcHeaderPlugin(mockGpc)

        val headers = testee.getHeaders(url, BrowserMode.REGULAR)

        assertEquals(gpcHeaders, headers)
    }

    @Test
    fun whenGetHeadersCalledFromFireModeThenGpcHeadersStillReturned() {
        val url = "url"
        val gpcHeaders = mapOf(GPC_HEADER to GPC_HEADER_VALUE)
        whenever(mockGpc.getHeaders(url)).thenReturn(gpcHeaders)
        testee = GpcHeaderPlugin(mockGpc)

        val headers = testee.getHeaders(url, BrowserMode.FIRE)

        assertEquals(gpcHeaders, headers)
    }
}
