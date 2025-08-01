package com.duckduckgo.autofill.impl.importing.capability

import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.WebMessageListener
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class DefaultImportGooglePasswordsCapabilityCheckerTest {

    private val webViewCapabilityChecker: WebViewCapabilityChecker = mock()

    private val testee = DefaultImportGooglePasswordsCapabilityChecker(webViewCapabilityChecker = webViewCapabilityChecker)

    @Before
    fun setup() = runTest {
        whenever(webViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(true)
        whenever(webViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(true)
    }

    @Test
    fun whenWebViewSupportsDocumentStartJavascriptApiAndWebMessageListenersThenSupportsImportingPasswords() = runTest {
        assertTrue(testee.webViewCapableOfImporting())
    }

    @Test
    fun whenWebViewDoesNotSupportDocumentStartJavascriptApiThenDoesNotSupportImportingPasswords() = runTest {
        whenever(webViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(false)
        assertFalse(testee.webViewCapableOfImporting())
    }

    @Test
    fun whenWebViewDoesNotSupportWebMessageListenersThenDoesNotSupportImportingPasswords() = runTest {
        whenever(webViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(false)
        assertFalse(testee.webViewCapableOfImporting())
    }
}
