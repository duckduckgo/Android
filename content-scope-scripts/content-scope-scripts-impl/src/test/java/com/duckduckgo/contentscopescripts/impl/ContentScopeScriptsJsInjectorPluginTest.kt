package com.duckduckgo.contentscopescripts.impl

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ContentScopeScriptsJsInjectorPluginTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockCoreContentScopeScripts: CoreContentScopeScripts = mock()
    private val adsJsContentScopeScripts: AdsJsContentScopeScripts = mock()
    private val mockWebView: WebView = mock()
    private val mockContentScopeExperiments: ContentScopeExperiments = mock()
    private val mockWebViewCompatWrapper: WebViewCompatWrapper = mock()
    private val mockToggle = mock<Toggle>()

    private lateinit var contentScopeScriptsJsInjectorPlugin: ContentScopeScriptsJsInjectorPlugin

    @Before
    fun setUp() = runTest {
        whenever(mockWebViewCompatWrapper.isDocumentStartScriptSupported()).thenReturn(true)
        whenever(mockContentScopeExperiments.getActiveExperiments()).thenReturn(listOf(mockToggle))
        contentScopeScriptsJsInjectorPlugin = ContentScopeScriptsJsInjectorPlugin(
            mockCoreContentScopeScripts,
            adsJsContentScopeScripts,
            mockContentScopeExperiments,
            coroutineRule.testDispatcherProvider,
            mockWebViewCompatWrapper,
        )
    }

    @Test
    fun whenEnabledAndInjectContentScopeScriptsThenPopulateMessagingParameters() = runTest {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockCoreContentScopeScripts.getScript(null, listOf())).thenReturn("")
        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null)

        verify(mockCoreContentScopeScripts).getScript(null, listOf())
        verify(mockWebView).evaluateJavascript(any(), anyOrNull())
    }

    @Test
    fun whenDisabledAndInjectContentScopeScriptsThenDoNothing() = runTest {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(false)
        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null)

        verifyNoInteractions(mockWebView)
    }

    @Test
    fun whenEnabledAndInjectContentScopeScriptsThenUseParams() = runTest {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(mockCoreContentScopeScripts.getScript(true, listOf())).thenReturn("")
        contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, true)

        verify(mockCoreContentScopeScripts).getScript(true, listOf())
    }

    @Test
    fun whenEnabledAndPageStartedWithNoInitJsThenReturnEmptyActiveExperiments() = runTest {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)

        val result = contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenEnabledAndPageStartedWithInitJsThenReturnActiveExperiments() = runTest {
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)

        contentScopeScriptsJsInjectorPlugin.onInit(mockWebView)
        val result = contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null)

        assertEquals(listOf(mockToggle), result)
    }

    @Test
    fun whenEnabledAndPageStartedWithInitJsAndActiveExperimentsChangedAfterwardsThenReturnActiveExperimentsFromInit() = runTest {
        val mockToggle2 = mock<Toggle>()
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)

        contentScopeScriptsJsInjectorPlugin.onInit(mockWebView)

        whenever(mockContentScopeExperiments.getActiveExperiments()).thenReturn(listOf(mockToggle2))

        val result = contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null)

        assertEquals(listOf(mockToggle), result)
    }

    @Test
    fun whenInitJsActiveExperimentsUpdated() = runTest {
        val mockToggle2 = mock<Toggle>()
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        contentScopeScriptsJsInjectorPlugin.onInit(mockWebView)

        val result = contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null)

        assertEquals(listOf(mockToggle), result)

        whenever(mockContentScopeExperiments.getActiveExperiments()).thenReturn(listOf(mockToggle2))
        contentScopeScriptsJsInjectorPlugin.onInit(mockWebView)

        val result2 = contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null)

        assertEquals(listOf(mockToggle2), result2)
    }

    @Test
    fun whenPageFinishedActiveExperimentsUpdated() = runTest {
        val mockToggle2 = mock<Toggle>()
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        contentScopeScriptsJsInjectorPlugin.onInit(mockWebView)

        val result = contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null)

        assertEquals(listOf(mockToggle), result)

        whenever(mockContentScopeExperiments.getActiveExperiments()).thenReturn(listOf(mockToggle2))
        contentScopeScriptsJsInjectorPlugin.onPageFinished(mockWebView, null)

        val result2 = contentScopeScriptsJsInjectorPlugin.onPageStarted(mockWebView, null, null)

        assertEquals(listOf(mockToggle2), result2)
    }

    @Test
    fun whenDocumentStartScriptSupportedAndInitCalledWithScriptChangedThenScriptInjected() = runTest {
        whenever(mockWebViewCompatWrapper.isDocumentStartScriptSupported()).thenReturn(true)
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(adsJsContentScopeScripts.getScript(any())).thenReturn("mockScript")

        contentScopeScriptsJsInjectorPlugin.onInit(mockWebView)

        verify(adsJsContentScopeScripts).getScript(listOf(mockToggle))
        verify(mockWebViewCompatWrapper).addDocumentStartJavaScript(any(), eq("mockScript"), any())
    }

    @Test
    fun whenDocumentStartScriptNotSupportedAndInitCalledThenNoScriptInjected() = runTest {
        whenever(mockWebViewCompatWrapper.isDocumentStartScriptSupported()).thenReturn(false)
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)
        whenever(adsJsContentScopeScripts.getScript(any())).thenReturn("mockScript")

        contentScopeScriptsJsInjectorPlugin.onInit(mockWebView)

        verifyNoInteractions(adsJsContentScopeScripts)
        verify(mockWebViewCompatWrapper, never()).addDocumentStartJavaScript(any(), any(), any())
    }

    @Test
    fun whenDocumentStartScriptNotSupportedAndPageFinishedCalledThenNoScriptInjected() = runTest {
        whenever(mockWebViewCompatWrapper.isDocumentStartScriptSupported()).thenReturn(false)
        whenever(mockCoreContentScopeScripts.isEnabled()).thenReturn(true)

        contentScopeScriptsJsInjectorPlugin.onPageFinished(mockWebView, null)

        verifyNoInteractions(adsJsContentScopeScripts)
        verify(mockWebViewCompatWrapper, never()).addDocumentStartJavaScript(any(), any(), any())
    }
}
