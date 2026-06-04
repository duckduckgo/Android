package com.duckduckgo.app.onboarding.ui

import app.cash.turbine.test
import com.duckduckgo.app.browser.newaddressbaroption.NewAddressBarOptionV2DataStore
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId.ADD_WIDGET
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.onboarding.store.AppStage.DAX_ONBOARDING
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@Suppress("DEPRECATION")
class FullOnboardingSkipperTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val dismissedCtaDao: DismissedCtaDao = mock()
    private val userStageStore: UserStageStore = mock()
    private val settingsDataStore: SettingsDataStore = mock()
    private val newAddressBarOptionV2DataStore: NewAddressBarOptionV2DataStore = mock()

    private val testee = FullOnboardingSkipper(
        dispatchers = coroutineTestRule.testDispatcherProvider,
        settingsDataStore = settingsDataStore,
        dismissedCtaDao = dismissedCtaDao,
        userStageStore = userStageStore,
        newAddressBarOptionV2DataStore = newAddressBarOptionV2DataStore,
    )

    @Test
    fun whenPrivacyConfigNotYetDownloadedThenCannotSkipOnboarding() = runTest {
        // checking value before privacy config downloaded
        testee.privacyConfigDownloaded.test {
            assertFalse(awaitItem().skipOnboardingPossible)
        }
    }

    @Test
    fun whenPrivacyConfigDownloadedThenViewStateUpdated() = runTest {
        testee.onPrivacyConfigDownloaded()
        testee.privacyConfigDownloaded.test {
            assertTrue(awaitItem().skipOnboardingPossible)
        }
    }

    @Test
    fun whenSkipperInvokedToMarkOnboardingDoneThenAllOnboardingStoresUpdated() = runTest {
        testee.markOnboardingAsCompleted()
        verifyStoreInvocations(expectingToBeCalled = true)
    }

    @Test
    fun whenMarkOnboardingAsCompletedThenNewAddressBarOptionV2PickerSuppressed() = runTest {
        testee.markOnboardingAsCompleted()
        verify(newAddressBarOptionV2DataStore).setAsShown()
    }

    private suspend fun verifyStoreInvocations(expectingToBeCalled: Boolean) {
        val verificationMode = if (expectingToBeCalled) times(1) else never()

        verify(settingsDataStore, verificationMode).hideTips = true
        verify(dismissedCtaDao, verificationMode).insert(DismissedCta(ADD_WIDGET))
        verify(userStageStore, verificationMode).stageCompleted(DAX_ONBOARDING)
    }
}
