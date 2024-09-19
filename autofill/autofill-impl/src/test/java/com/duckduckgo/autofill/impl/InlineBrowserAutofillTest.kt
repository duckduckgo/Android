package com.duckduckgo.autofill.impl

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.autofill.impl.configuration.integration.modern.listener.AutofillWebMessageListener
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
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
    private val webMessageAttacher: AutofillWebMessageAttacher = mock()
    private val webMessageListeners = mutableListOf<AutofillWebMessageListener>()
    private val webMessageListenersPlugin: PluginPoint<AutofillWebMessageListener> = object : PluginPoint<AutofillWebMessageListener> {
        override fun getPlugins(): Collection<AutofillWebMessageListener> = webMessageListeners
    }

    @Test
    fun whenAutofillFeatureFlagDisabledThenDoNotAddJsInterface() = runTest {
        val testee = setupConfig(topLevelFeatureEnabled = false)
        testee.addJsInterface()
        verifyJavascriptNotAdded()
    }

    @Test
    fun whenWebViewDoesNotSupportIntegrationThenDoNotAddJsInterface() = runTest {
        val testee = setupConfig(deviceWebViewSupportsAutofill = false)
        testee.addJsInterface()
        verifyJavascriptNotAdded()
    }

    @Test
    fun whenWebViewSupportsIntegrationAndFeatureEnabledThenJsInterfaceIsAdded() = runTest {
        val testee = setupConfig()
        testee.addJsInterface()
        verifyJavascriptIsAdded()
    }

    @Test
    fun whenPluginsIsEmptyThenJsInterfaceIsAdded() = runTest {
        val testee = setupConfig()
        webMessageListeners.clear()
        testee.addJsInterface()
        verifyJavascriptIsAdded()
    }

    @Test
    fun whenPluginsIsNotEmptyThenIsRegisteredWithWebView() = runTest {
        val testee = setupConfig()
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

    @SuppressLint("DenyListedApi")
    private suspend fun setupConfig(
        topLevelFeatureEnabled: Boolean = true,
        autofillEnabledByUser: Boolean = true,
        canInjectCredentials: Boolean = true,
        canSaveCredentials: Boolean = true,
        canGeneratePassword: Boolean = true,
        canAccessCredentialManagement: Boolean = true,
        deviceWebViewSupportsAutofill: Boolean = true,
    ): InlineBrowserAutofill {
        val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
        autofillFeature.self().setEnabled(State(enable = topLevelFeatureEnabled))
        autofillFeature.canInjectCredentials().setEnabled(State(enable = canInjectCredentials))
        autofillFeature.canSaveCredentials().setEnabled(State(enable = canSaveCredentials))
        autofillFeature.canGeneratePasswords().setEnabled(State(enable = canGeneratePassword))
        autofillFeature.canAccessCredentialManagement().setEnabled(State(enable = canAccessCredentialManagement))

        whenever(capabilityChecker.webViewSupportsAutofill()).thenReturn(deviceWebViewSupportsAutofill)

        return InlineBrowserAutofill(
            autofillCapabilityChecker = capabilityChecker,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            autofillJavascriptInjector = autofillJavascriptInjector,
            webMessageListeners = webMessageListenersPlugin,
            autofillFeature = autofillFeature,
            webMessageAttacher = webMessageAttacher,
        )
    }
}
