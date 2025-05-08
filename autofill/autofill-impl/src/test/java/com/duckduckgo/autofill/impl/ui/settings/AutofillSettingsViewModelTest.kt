package com.duckduckgo.autofill.impl.ui.settings

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.WebMessageListener
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class AutofillSettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockStore: InternalAutofillStore = mock()
    private val pixel: Pixel = mock()
    private val neverSavedSiteRepository: NeverSavedSiteRepository = mock()
    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
    private val webViewCapabilityChecker: WebViewCapabilityChecker = mock()

    private val testee = AutofillSettingsViewModel(
        autofillStore = mockStore,
        pixel = pixel,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        neverSavedSiteRepository = neverSavedSiteRepository,
        autofillFeature = autofillFeature,
        webViewCapabilityChecker = webViewCapabilityChecker,
    )

    @Before
    fun setup() {
        runTest {
            whenever(mockStore.getAllCredentials()).thenReturn(emptyFlow())
            whenever(mockStore.getCredentialCount()).thenReturn(flowOf(0))
            whenever(neverSavedSiteRepository.neverSaveListCount()).thenReturn(emptyFlow())
            whenever(webViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(true)
            whenever(webViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(true)
            whenever(mockStore.autofillAvailable()).thenReturn(true)
            autofillFeature.self().setRawStoredState(State(enable = true))
            autofillFeature.canImportFromGooglePasswordManager().setRawStoredState(State(enable = true))
            autofillFeature.settingsScreen().setRawStoredState(State(enable = true))
        }
    }

    @Test
    fun whenViewCreatedThenDoesShowToggle() = runTest {
        testee.viewState.test {
            assertTrue(this.awaitItem().showAutofillEnabledToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutofillNotAvailableThenUpdateState() = runTest {
        whenever(mockStore.autofillAvailable()).thenReturn(false)
        testee.viewState.test {
            assertFalse(awaitItem().autofillAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenViewCreatedShowNumberOfPasswords() = runTest {
        whenever(mockStore.getCredentialCount()).thenReturn(flowOf(10))
        testee.viewState.test {
            assertEquals(10, this.awaitItem().loginsCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserEnablesAutofillThenUpdateStateAndStore() = runTest {
        testee.viewState.test {
            awaitItem()
            testee.onEnableAutofill()
            assertTrue(awaitItem().autofillEnabled)
            verify(mockStore).autofillEnabled = true
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserDisablesAutofillThenUpdateStateAndStore() = runTest {
        testee.viewState.test {
            testee.onDisableAutofill(AutofillScreenLaunchSource.SettingsActivity)
            assertFalse(awaitItem().autofillEnabled)
            verify(mockStore).autofillEnabled = false
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnPasswordListThenCommandIsSent() = runTest {
        testee.onPasswordListClicked()
        testee.commands.test {
            assertEquals(AutofillSettingsViewModel.Command.NavigatePasswordList, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnImportFromDesktopThenCommandIsSent() = runTest {
        testee.onImportFromDesktopWithSyncClicked()
        testee.commands.test {
            assertEquals(AutofillSettingsViewModel.Command.NavigateToHowToSyncWithDesktop, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenImportGooglePasswordsIsEnabledThenViewStateReflectsThat() = runTest {
        testee.viewState.test {
            assertTrue(awaitItem().canImportFromGooglePasswords)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenImportGooglePasswordsFeatureFlagDisabledThenViewStateReflectsThat() = runTest {
        autofillFeature.canImportFromGooglePasswordManager().setRawStoredState(State(enable = false))
        testee.viewState.test {
            assertFalse(awaitItem().canImportFromGooglePasswords)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenImportGooglePasswordsFeatureDisabledDueToWebMessageListenerNotSupportedThenViewStateReflectsThat() = runTest {
        whenever(webViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(false)
        testee.viewState.test {
            assertFalse(awaitItem().canImportFromGooglePasswords)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenImportGooglePasswordsFeatureDisabledDueToDocumentStartJavascriptNotSupportedThenViewStateReflectsThat() = runTest {
        whenever(webViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(false)
        testee.viewState.test {
            assertFalse(awaitItem().canImportFromGooglePasswords)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClickOnImportGooglePasswordsThenCommandIsSent() = runTest {
        testee.onImportPasswordsClicked()
        testee.commands.test {
            assertEquals(AutofillSettingsViewModel.Command.ImportPasswordsFromGoogle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserHasSitesInNeverSavedListThenViewStateReflectsThat() = runTest {
        whenever(neverSavedSiteRepository.neverSaveListCount()).thenReturn(flowOf(1))
        testee.viewState.test {
            assertTrue(awaitItem().canResetExcludedSites)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserHasNoSitesInNeverSavedListThenViewStateReflectsThat() = runTest {
        whenever(neverSavedSiteRepository.neverSaveListCount()).thenReturn(flowOf(0))
        testee.viewState.test {
            assertFalse(awaitItem().canResetExcludedSites)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserCliksOnResetExcludedSitesThenCommandIsSent() = runTest {
        testee.onResetExcludedSitesClicked()
        testee.commands.test {
            assertEquals(AutofillSettingsViewModel.Command.AskToConfirmResetExcludedSites, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserConfirmsToResetExcludedSitesThenClearRepository() = runTest {
        testee.onResetExcludedSitesConfirmed()
        verify(neverSavedSiteRepository).clearNeverSaveList()
    }
}
