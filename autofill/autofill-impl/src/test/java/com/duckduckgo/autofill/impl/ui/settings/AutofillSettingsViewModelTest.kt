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
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_TAPPED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISPLAYED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SETTINGS_OPENED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SYNC_DESKTOP_PASSWORDS_CTA_BUTTON
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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
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
    private val deviceAuthenticator: DeviceAuthenticator = mock()
    private val launchSource = AutofillScreenLaunchSource.SettingsActivity

    private val testee = AutofillSettingsViewModel(
        autofillStore = mockStore,
        pixel = pixel,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        neverSavedSiteRepository = neverSavedSiteRepository,
        autofillFeature = autofillFeature,
        webViewCapabilityChecker = webViewCapabilityChecker,
        deviceAuthenticator = deviceAuthenticator,
    )

    @Before
    fun setup() {
        runTest {
            whenever(mockStore.getAllCredentials()).thenReturn(emptyFlow())
            whenever(mockStore.getCredentialCount()).thenReturn(flowOf(0))
            whenever(neverSavedSiteRepository.neverSaveListCount()).thenReturn(emptyFlow())
            whenever(deviceAuthenticator.isAuthenticationRequiredForAutofill()).thenReturn(true)
            whenever(webViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(true)
            whenever(webViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(true)
            whenever(mockStore.autofillAvailable()).thenReturn(true)
            autofillFeature.self().setRawStoredState(State(enable = true))
            autofillFeature.canImportFromGooglePasswordManager().setRawStoredState(State(enable = true))
            autofillFeature.settingsScreen().setRawStoredState(State(enable = true))
        }
    }

    @Test
    fun whenScreenRendersIfImportButtonAvailableThenPixelIsSent() = runTest {
        testee.viewState(launchSource).test {
            awaitItem()
            val expectedParams = mapOf("source" to "autofill_settings")
            verify(pixel).fire(
                pixel = eq(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_SHOWN),
                parameters = eq(expectedParams),
                any(),
                any(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenScreenRendersTwiceAndImportButtonAvailableThenPixelIsSentOnlyOnce() = runTest {
        testee.viewState(launchSource).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.viewState(launchSource).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        verify(pixel, times(1)).fire(pixel = eq(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_SHOWN), any(), any(), any())
    }

    @Test
    fun whenScreenRendersIfImportButtonNotAvailableThenPixelIsNotSent() = runTest {
        whenever(webViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(false)
        whenever(webViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(false)
        testee.viewState(launchSource).test {
            awaitItem()
            verify(pixel, times(0)).fire(pixel = eq(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_SHOWN), any(), any(), any())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when send launch pixel and no credentials saved then pixel is sent`() = runTest {
        whenever(mockStore.getCredentialCount()).thenReturn(flowOf(0))
        testee.sendLaunchPixel(AutofillScreenLaunchSource.SettingsActivity)
        val expectedParams = mapOf("source" to "settings", "has_credentials_saved" to "0")
        verify(pixel).fire(pixel = eq(AUTOFILL_SETTINGS_OPENED), parameters = eq(expectedParams), any(), any())
    }

    @Test
    fun `when send launch pixel and has credentials saved then pixel is sent`() = runTest {
        whenever(mockStore.getCredentialCount()).thenReturn(flowOf(5))
        testee.sendLaunchPixel(AutofillScreenLaunchSource.BrowserOverflow)
        val expectedParams = mapOf("source" to "overflow_menu", "has_credentials_saved" to "1")
        verify(pixel).fire(pixel = eq(AUTOFILL_SETTINGS_OPENED), parameters = eq(expectedParams), any(), any())
    }

    @Test
    fun whenViewCreatedThenDoesShowToggle() = runTest {
        testee.viewState(launchSource).test {
            assertTrue(this.awaitItem().showAutofillEnabledToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutofillNotAvailableThenUpdateStateToUnsupported() = runTest {
        whenever(mockStore.autofillAvailable()).thenReturn(false)
        testee.viewState(launchSource).test {
            assertTrue(awaitItem().autofillUnsupported)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeviceMissingValidAuthenticationThenUpdateStateToDisabled() = runTest {
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(false)
        testee.viewState(launchSource).test {
            assertTrue(awaitItem().autofillDisabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCheckDeviceRequirementsThenUpdateStateUpdates() = runTest {
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        testee.viewState(launchSource).test {
            awaitItem().let {
                assertFalse(it.autofillDisabled)
                assertFalse(it.autofillUnsupported)
            }
            whenever(mockStore.autofillAvailable()).thenReturn(false)
            whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(false)
            testee.checkDeviceRequirements()
            awaitItem().let {
                assertTrue(it.autofillDisabled)
                assertTrue(it.autofillUnsupported)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenViewCreatedShowNumberOfPasswords() = runTest {
        whenever(mockStore.getCredentialCount()).thenReturn(flowOf(10))
        testee.viewState(launchSource).test {
            assertEquals(10, this.awaitItem().loginsCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserEnablesAutofillThenUpdateStateAndStore() = runTest {
        testee.viewState(launchSource).test {
            awaitItem()
            testee.onEnableAutofill(AutofillScreenLaunchSource.SettingsActivity)
            assertTrue(awaitItem().autofillEnabled)
            verify(mockStore).autofillEnabled = true
            val expectedParams = mapOf("source" to "settings")
            verify(pixel).fire(eq(AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED), parameters = eq(expectedParams), any(), any())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserDisablesAutofillThenUpdateStateAndStore() = runTest {
        testee.viewState(launchSource).test {
            testee.onDisableAutofill(AutofillScreenLaunchSource.SettingsActivity)
            assertFalse(awaitItem().autofillEnabled)
            verify(mockStore).autofillEnabled = false
            val expectedParams = mapOf("source" to "settings")
            verify(pixel).fire(pixel = eq(AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED), parameters = eq(expectedParams), any(), any())
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
        testee.onImportFromDesktopWithSyncClicked(AutofillScreenLaunchSource.SettingsActivity)
        testee.commands.test {
            assertEquals(AutofillSettingsViewModel.Command.NavigateToHowToSyncWithDesktop, awaitItem())
            val expectedParams = mapOf("source" to "settings")
            verify(pixel).fire(pixel = eq(AUTOFILL_SYNC_DESKTOP_PASSWORDS_CTA_BUTTON), parameters = eq(expectedParams), any(), any())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenImportGooglePasswordsIsEnabledThenViewStateReflectsThat() = runTest {
        testee.viewState(launchSource).test {
            assertTrue(awaitItem().canImportFromGooglePasswords)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenImportGooglePasswordsFeatureFlagDisabledThenViewStateReflectsThat() = runTest {
        autofillFeature.canImportFromGooglePasswordManager().setRawStoredState(State(enable = false))
        testee.viewState(launchSource).test {
            assertFalse(awaitItem().canImportFromGooglePasswords)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenImportGooglePasswordsFeatureDisabledDueToWebMessageListenerNotSupportedThenViewStateReflectsThat() = runTest {
        whenever(webViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(false)
        testee.viewState(launchSource).test {
            assertFalse(awaitItem().canImportFromGooglePasswords)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenImportGooglePasswordsFeatureDisabledDueToDocumentStartJavascriptNotSupportedThenViewStateReflectsThat() = runTest {
        whenever(webViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(false)
        testee.viewState(launchSource).test {
            assertFalse(awaitItem().canImportFromGooglePasswords)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClickOnImportGooglePasswordsThenCommandIsSent() = runTest {
        testee.onImportPasswordsClicked(AutofillScreenLaunchSource.SettingsActivity)
        testee.commands.test {
            assertEquals(AutofillSettingsViewModel.Command.ImportPasswordsFromGoogle, awaitItem())
            val expectedParams = mapOf("source" to "autofill_settings")
            verify(pixel).fire(
                pixel = eq(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_TAPPED),
                parameters = eq(expectedParams),
                any(),
                any(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserHasSitesInNeverSavedListThenViewStateReflectsThat() = runTest {
        whenever(neverSavedSiteRepository.neverSaveListCount()).thenReturn(flowOf(1))
        testee.viewState(launchSource).test {
            assertTrue(awaitItem().canResetExcludedSites)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserHasNoSitesInNeverSavedListThenViewStateReflectsThat() = runTest {
        whenever(neverSavedSiteRepository.neverSaveListCount()).thenReturn(flowOf(0))
        testee.viewState(launchSource).test {
            assertFalse(awaitItem().canResetExcludedSites)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserCliksOnResetExcludedSitesThenCommandIsSent() = runTest {
        testee.onResetExcludedSitesClicked(AutofillScreenLaunchSource.SettingsActivity)
        testee.commands.test {
            assertEquals(AutofillSettingsViewModel.Command.AskToConfirmResetExcludedSites, awaitItem())
            val expectedParams = mapOf("source" to "settings")
            verify(pixel).fire(
                pixel = eq(AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISPLAYED),
                parameters = eq(expectedParams),
                any(),
                any(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserConfirmsToResetExcludedSitesThenClearRepository() = runTest {
        testee.onResetExcludedSitesConfirmed()
        verify(neverSavedSiteRepository).clearNeverSaveList()
    }
}
