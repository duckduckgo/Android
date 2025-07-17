package com.duckduckgo.autofill.impl.importing

import android.annotation.SuppressLint
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.WebMessageListener
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.importing.RealInBrowserImportPromo.Companion.MAX_PROMO_SHOWN_COUNT
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(Parameterized::class)
class RealInBrowserImportPromoParameterizedTest(
    private val testCase: CanShowPromoTestCase,
) {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)

    private val autofillStore: InternalAutofillStore = mock()
    private val neverSavedSiteRepository: NeverSavedSiteRepository = mock()
    private val webViewCapabilityChecker: WebViewCapabilityChecker = mock()

    private val testee = RealInBrowserImportPromo(
        autofillStore = autofillStore,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        neverSavedSiteRepository = neverSavedSiteRepository,
        autofillFeature = autofillFeature,
        webViewCapabilityChecker = webViewCapabilityChecker,
    )

    @Before
    fun setup() = runTest {
        whenever(autofillStore.hasEverImportedPasswords).thenReturn(testCase.hasEverImportedPasswords)
        whenever(autofillStore.hasDeclinedInBrowserPasswordImportPromo).thenReturn(testCase.hasDeclinedPromo)
        whenever(autofillStore.getCredentialCount()).thenReturn(flowOf(testCase.credentialCount))
        whenever(autofillStore.inBrowserImportPromoShownCount).thenReturn(testCase.promoShownCount)
        whenever(neverSavedSiteRepository.isInNeverSaveList(EXAMPLE_URL)).thenReturn(false)
        whenever(neverSavedSiteRepository.isInNeverSaveList(NEVER_SAVE_URL)).thenReturn(true)
        autofillFeature.canPromoteImportGooglePasswordsInBrowser().setRawStoredState(State(enable = testCase.inBrowserPromoFeatureEnabled))
        autofillFeature.self().setRawStoredState(State(enable = testCase.autofillFeatureEnabled))
        whenever(webViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(testCase.webViewWebMessageSupport)
        whenever(webViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(testCase.webViewDocumentStartJavascript)
    }

    @Test
    fun inBrowserPromoRules() = runTest {
        val result = testee.canShowPromo(testCase.credentialsAvailableForCurrentPage, testCase.url)
        assertEquals(testCase.description, testCase.expected, result)
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun data(): List<CanShowPromoTestCase> {
            return listOf(
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = 0,
                    promoShownCount = 0,
                    expected = true,
                    description = "eligible: all conditions met",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = true,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = 0,
                    promoShownCount = 0,
                    expected = false,
                    description = "ineligible: credentials available for current page",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = false,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = 0,
                    promoShownCount = 0,
                    expected = false,
                    description = "ineligible: can import password feature disabled",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = false,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = 0,
                    promoShownCount = 0,
                    expected = false,
                    description = "ineligible: autofill feature disabled",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = true,
                    hasDeclinedPromo = false,
                    credentialCount = 0,
                    promoShownCount = 0,
                    expected = false,
                    description = "ineligible: has ever imported passwords",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = true,
                    credentialCount = 0,
                    promoShownCount = 0,
                    expected = false,
                    description = "ineligible: user has declined promo",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = RealInBrowserImportPromo.MAX_CREDENTIALS_FOR_PROMO - 1,
                    promoShownCount = 0,
                    expected = true,
                    description = "eligible: credentialCount just below max",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = RealInBrowserImportPromo.MAX_CREDENTIALS_FOR_PROMO,
                    promoShownCount = 0,
                    expected = false,
                    description = "ineligible: credential count at max limit",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = RealInBrowserImportPromo.MAX_CREDENTIALS_FOR_PROMO + 1,
                    promoShownCount = 0,
                    expected = false,
                    description = "ineligible: credentialCount just above max",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = 0,
                    promoShownCount = MAX_PROMO_SHOWN_COUNT - 1,
                    expected = true,
                    description = "eligible: promoShownCount just below max",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = 0,
                    promoShownCount = MAX_PROMO_SHOWN_COUNT,
                    expected = false,
                    description = "ineligible: promo shown count at max limit",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = 0,
                    promoShownCount = MAX_PROMO_SHOWN_COUNT + 1,
                    expected = false,
                    description = "ineligible: promoShownCount just above max",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = NEVER_SAVE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = 0,
                    promoShownCount = 0,
                    expected = false,
                    description = "ineligible: url in never save list",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = null,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = 0,
                    promoShownCount = 0,
                    expected = true,
                    description = "eligible: url is null, all other conditions met",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = true,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = false,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = true,
                    hasDeclinedPromo = true,
                    credentialCount = RealInBrowserImportPromo.MAX_CREDENTIALS_FOR_PROMO,
                    promoShownCount = MAX_PROMO_SHOWN_COUNT,
                    expected = false,
                    description = "ineligible: multiple disqualifying conditions",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = 0,
                    promoShownCount = 0,
                    webViewWebMessageSupport = false,
                    webViewDocumentStartJavascript = true,
                    expected = false,
                    description = "ineligible: webview does not support WebMessageListener",
                ),
                CanShowPromoTestCase(
                    credentialsAvailableForCurrentPage = false,
                    url = EXAMPLE_URL,
                    inBrowserPromoFeatureEnabled = true,
                    autofillFeatureEnabled = true,
                    hasEverImportedPasswords = false,
                    hasDeclinedPromo = false,
                    credentialCount = 0,
                    promoShownCount = 0,
                    webViewWebMessageSupport = true,
                    webViewDocumentStartJavascript = false,
                    expected = false,
                    description = "ineligible: webview does not support DocumentStartJavaScript",
                ),
            )
        }

        private const val EXAMPLE_URL = "https://example.com"
        private const val NEVER_SAVE_URL = "https://neversave.example.com"
    }

    override fun toString(): String {
        return testCase.description
    }
}

data class CanShowPromoTestCase(
    val credentialsAvailableForCurrentPage: Boolean,
    val url: String?,
    val inBrowserPromoFeatureEnabled: Boolean,
    val autofillFeatureEnabled: Boolean,
    val hasEverImportedPasswords: Boolean,
    val hasDeclinedPromo: Boolean,
    val credentialCount: Int,
    val promoShownCount: Int,
    val webViewWebMessageSupport: Boolean = true,
    val webViewDocumentStartJavascript: Boolean = true,
    val expected: Boolean,
    val description: String,
) {
    override fun toString() = description
}
