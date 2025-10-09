package com.duckduckgo.autofill.impl.importing.gpm.webflow

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowWebViewClient.WebFlowCallback
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class ImportGooglePasswordsWebFlowWebViewClientTest {

    private val mockCallback: WebFlowCallback = mock()
    private val mockWebView: WebView = mock()
    private val mockDetail: RenderProcessGoneDetail = mock()

    private val testee = ImportGooglePasswordsWebFlowWebViewClient(mockCallback)

    @Test
    fun whenOnRenderProcessGoneThenCallsCallbackOnWebViewCrash() {
        testee.onRenderProcessGone(mockWebView, mockDetail)
        verify(mockCallback).onWebViewCrash()
    }

    @Test
    fun whenOnRenderProcessGoneThenReturnsTrue() {
        val result = testee.onRenderProcessGone(mockWebView, mockDetail)
        assertTrue(result)
    }
}
