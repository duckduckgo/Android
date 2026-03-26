package com.duckduckgo.autofill.impl.email.incontext.availability

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.autofill.impl.AutofillGlobalCapabilityChecker
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupFeature
import com.duckduckgo.autofill.impl.email.remoteconfig.EmailProtectionInContextExceptions
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class RealEmailProtectionInContextAvailabilityRulesTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val appBuildConfig: AppBuildConfig = mock()
    private val emailProtectionInContextSignupFeature = FakeFeatureToggleFactory.create(EmailProtectionInContextSignupFeature::class.java)
    private val internalTestUserChecker: InternalTestUserChecker = mock()
    private val exceptions: EmailProtectionInContextExceptions = mock()
    private val autofillGlobalCapabilityChecker: AutofillGlobalCapabilityChecker = mock()
    private val recentInstallChecker: EmailProtectionInContextRecentInstallChecker = mock()

    private val testee = RealEmailProtectionInContextAvailabilityRules(
        appBuildConfig = appBuildConfig,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        emailProtectionInContextSignupFeature = emailProtectionInContextSignupFeature,
        internalTestUserChecker = internalTestUserChecker,
        exceptions = exceptions,
        autofillGlobalCapabilityChecker = autofillGlobalCapabilityChecker,
        recentInstallChecker = recentInstallChecker,
    )

    @Before
    fun setup() {
        coroutineTestRule.testScope.launch {
            // setup sensible defaults
            configureEnglishLocale()
            configureAsRecentInstall()

            emailProtectionInContextSignupFeature.self().setRawStoredState(State(enable = true))
            whenever(exceptions.isAnException(any())).thenReturn(false)
            whenever(exceptions.isAnException(DISALLOWED_URL)).thenReturn(true)
            whenever(internalTestUserChecker.isInternalTestUser).thenReturn(false)
            whenever(autofillGlobalCapabilityChecker.isSecureAutofillAvailable()).thenReturn(true)
        }
    }

    @Test
    fun whenLocaleNotEnglishThenNotPermitted() = runTest {
        configureNonEnglishLocale()
        assertFalse(testee.permittedToShow(ALLOWED_URL))
    }

    @Test
    fun whenInstalledALongTimeAgoThenNotPermitted() = runTest {
        configureAsNotRecentInstall()
        assertFalse(testee.permittedToShow(ALLOWED_URL))
    }

    @Test
    fun whenInstalledRecentlyThenPermitted() = runTest {
        configureAsRecentInstall()
        assertTrue(testee.permittedToShow(ALLOWED_URL))
    }

    @Test
    fun whenSecureAutofillUnavailableThenNotPermitted() = runTest {
        whenever(autofillGlobalCapabilityChecker.isSecureAutofillAvailable()).thenReturn(false)
        assertFalse(testee.permittedToShow(ALLOWED_URL))
    }

    @Test
    fun whenFeatureDisabledInRemoteConfigThenNotPermitted() = runTest {
        emailProtectionInContextSignupFeature.self().setRawStoredState(State(enable = false))
        assertFalse(testee.permittedToShow(ALLOWED_URL))
    }

    @Test
    fun whenUrlOnExceptionListThenNotPermitted() = runTest {
        assertFalse(testee.permittedToShow(DISALLOWED_URL))
    }

    private fun configureNonEnglishLocale() = configureLocale("fr")
    private fun configureEnglishLocale() = configureLocale("en")

    private fun configureLocale(languageCode: String) {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale(languageCode))
    }

    private suspend fun configureAsRecentInstall() {
        whenever(recentInstallChecker.isRecentInstall()).thenReturn(true)
    }

    private suspend fun configureAsNotRecentInstall() {
        whenever(recentInstallChecker.isRecentInstall()).thenReturn(false)
    }

    companion object {
        private const val ALLOWED_URL = "https://duckduckgo.com"
        private const val DISALLOWED_URL = "https://example.com"
    }
}
