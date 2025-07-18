package com.duckduckgo.autofill.impl.importing

import android.annotation.SuppressLint
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.WebMessageListener
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.importing.RealInSettingsPasswordImportPromoRules.Companion.MAX_CREDENTIALS_FOR_PROMO
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class RealInSettingsPasswordImportPromoRulesTest {

    private val webViewCapabilityChecker: WebViewCapabilityChecker = mock()
    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
    private val autofillStore: InternalAutofillStore = mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun setup() = runTest {
        configureAllConditionsToAllowPromo()
    }

    private val testee = RealInSettingsPasswordImportPromoRules(
        autofillStore = autofillStore,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        autofillFeature = autofillFeature,
        webViewCapabilityChecker = webViewCapabilityChecker,
    )

    @Test
    fun whenAllConditionsAllowItThenPromoCanShow() = runTest {
        // by default, all conditions are set to allow the promo
        assertTrue(testee.canShowPromo())
    }

    @Test
    fun whenAutofillTopLevelFeatureDisabledThenCannotShowPromo() = runTest {
        autofillFeature.self().setRawStoredState(Toggle.State(enable = false))
        assertFalse(testee.canShowPromo())
    }

    @Test
    fun whenAutofillImportSettingsFeatureDisabledThenCannotShowPromo() = runTest {
        autofillFeature.canShowImportOptionInAppSettings().setRawStoredState(Toggle.State(enable = false))
        assertFalse(testee.canShowPromo())
    }

    @Test
    fun whenPreviouslyImportedPasswordsThenCannotShowPromo() = runTest {
        whenever(autofillStore.hasEverImportedPasswords).thenReturn(true)
        assertFalse(testee.canShowPromo())
    }

    @Test
    fun whenPreviouslyDismissedImportSettingThenCannotShowPromo() = runTest {
        whenever(autofillStore.hasDismissedMainAppSettingsPromo).thenReturn(true)
        assertFalse(testee.canShowPromo())
    }

    @Test
    fun whenAlreadyHave25PasswordsSavedThenCannotShowPromo() = runTest {
        whenever(autofillStore.getCredentialCount()).thenReturn(flowOf(MAX_CREDENTIALS_FOR_PROMO))
        assertFalse(testee.canShowPromo())
    }

    @Test
    fun whenWebViewDoesNotSupportWebMessageListenersThenCannotShowPromo() = runTest {
        configureWebMessageListenerAvailable(isAvailable = false)
        assertFalse(testee.canShowPromo())
    }

    @Test
    fun whenWebViewDoesNotSupportDocumentStartJavascriptThenCannotShowPromo() = runTest {
        configureDocumentStartJavascriptAvailable(isAvailable = false)
        assertFalse(testee.canShowPromo())
    }

    private suspend fun configureWebMessageListenerAvailable(isAvailable: Boolean) {
        whenever(webViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(isAvailable)
    }

    private suspend fun configureDocumentStartJavascriptAvailable(isAvailable: Boolean) {
        whenever(webViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(isAvailable)
    }

    private suspend fun configureAllConditionsToAllowPromo() {
        autofillFeature.self().setRawStoredState(Toggle.State(enable = true))
        autofillFeature.canShowImportOptionInAppSettings().setRawStoredState(Toggle.State(enable = true))
        whenever(autofillStore.hasEverImportedPasswords).thenReturn(false)
        whenever(autofillStore.hasDismissedMainAppSettingsPromo).thenReturn(false)
        whenever(autofillStore.getCredentialCount()).thenReturn(flowOf(0))
        whenever(webViewCapabilityChecker.isSupported(any())).thenReturn(true)
        configureWebMessageListenerAvailable(isAvailable = true)
        configureDocumentStartJavascriptAvailable(isAvailable = true)
    }
}
