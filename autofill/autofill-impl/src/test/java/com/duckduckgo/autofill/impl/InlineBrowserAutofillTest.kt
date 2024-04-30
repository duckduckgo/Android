package com.duckduckgo.autofill.impl

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.autofill.impl.configuration.integration.modern.listener.AutofillWebMessageListener
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.feature.toggles.api.toggle.AutofillTestFeature
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class InlineBrowserAutofillTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockWebView: WebView = mock()
    private val autofillCallback: Callback = mock()
    private val capabilityChecker: InternalAutofillCapabilityChecker = mock()
    private val autofillJavascriptInjector: AutofillJavascriptInjector = mock()
    private val autofillFeature = AutofillTestFeature()
    private val webMessageAttacher: AutofillWebMessageAttacher = mock()
    private val webMessageListeners = mutableListOf<AutofillWebMessageListener>()
    private val webMessageListenersPlugin: PluginPoint<AutofillWebMessageListener> = object : PluginPoint<AutofillWebMessageListener> {
        override fun getPlugins(): Collection<AutofillWebMessageListener> = webMessageListeners
    }

    private val testee = InlineBrowserAutofill(
        autofillCapabilityChecker = capabilityChecker,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        autofillJavascriptInjector = autofillJavascriptInjector,
        webMessageListeners = webMessageListenersPlugin,
        autofillFeature = autofillFeature,
        webMessageAttacher = webMessageAttacher,
    )

    @Before
    fun setup() {
        whenever(capabilityChecker.webViewSupportsAutofill()).thenReturn(true)
        with(autofillFeature) {
            topLevelFeatureEnabled = true
            canInjectCredentials = true
            canSaveCredentials = true
            canGeneratePassword = true
            canAccessCredentialManagement = true
            onByDefault = true
        }
    }

    @Test
    fun whenAutofillFeatureFlagDisabledThenDoNotAddJsInterface() = runTest {
        autofillFeature.topLevelFeatureEnabled = false
        testee.addJsInterface()
        verifyJavascriptNotAdded()
    }

    @Test
    fun whenWebViewDoesNotSupportIntegrationThenDoNotAddJsInterface() = runTest {
        whenever(capabilityChecker.webViewSupportsAutofill()).thenReturn(false)
        testee.addJsInterface()
        verifyJavascriptNotAdded()
    }

    @Test
    fun whenWebViewSupportsIntegrationAndFeatureEnabledThenJsInterfaceIsAdded() = runTest {
        testee.addJsInterface()
        verifyJavascriptIsAdded()
    }

    @Test
    fun whenPluginsIsEmptyThenJsInterfaceIsAdded() = runTest {
        webMessageListeners.clear()
        testee.addJsInterface()
        verifyJavascriptIsAdded()
    }

    @Test
    fun whenPluginsIsNotEmptyThenIsRegisteredWithWebView() = runTest {
        val mockMessageListener: AutofillWebMessageListener = mock()
        webMessageListeners.add(mockMessageListener)
        testee.addJsInterface()
        verify(webMessageAttacher).addListener(any(), eq(mockMessageListener))
    }

    private suspend fun verifyJavascriptNotAdded() {
        verify(autofillJavascriptInjector, never()).addDocumentStartJavascript(any())
    }

    private suspend fun verifyJavascriptIsAdded() {
        verify(autofillJavascriptInjector).addDocumentStartJavascript(any())
    }

    private suspend fun InlineBrowserAutofill.addJsInterface() {
        addJsInterface(mockWebView, autofillCallback, "tab-id-123")
    }
}
