/*
 * Copyright (c) 2022 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.autofill.impl.ui.credential.management

import app.cash.turbine.test
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.COUNT
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource.BrowserOverflow
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource.BrowserSnackbar
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource.SettingsActivity
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource.Sync
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_COPY_PASSWORD
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_COPY_USERNAME
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DELETE_LOGIN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_MANAGEMENT_SCREEN_OPENED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_CONFIRMED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISPLAYED
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitCredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitListMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitLockedMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.LaunchDeviceAuth
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.OfferUserUndoMassDeletion
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowCredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowDeviceUnsupportedMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowDisabledMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowLockedMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowUserPasswordCopied
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowUserUsernameCopied
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode.EditingExisting
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.LaunchDeleteAllPasswordsConfirmation
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.ListModeCommand.PromptUserToAuthenticateMassDeletion
import com.duckduckgo.autofill.impl.ui.credential.management.searching.CredentialListFilter
import com.duckduckgo.autofill.impl.ui.credential.management.survey.AutofillSurvey
import com.duckduckgo.autofill.impl.ui.credential.management.survey.SurveyDetails
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.duckaddress.DuckAddressIdentifier
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.duckaddress.RealDuckAddressIdentifier
import com.duckduckgo.autofill.impl.ui.credential.repository.DuckAddressStatusRepository
import com.duckduckgo.common.test.CoroutineTestRule
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AutofillSettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockStore: InternalAutofillStore = mock()
    private val emailManager: EmailManager = mock()
    private val duckAddressStatusRepository: DuckAddressStatusRepository = mock()
    private val clipboardInteractor: AutofillClipboardInteractor = mock()
    private val pixel: Pixel = mock()
    private val deviceAuthenticator: DeviceAuthenticator = mock()
    private val credentialListFilter: CredentialListFilter = TestFilterPassthrough()
    private val faviconManager: FaviconManager = mock()
    private val webUrlIdentifier: WebUrlIdentifier = mock()
    private val duckAddressIdentifier: DuckAddressIdentifier = RealDuckAddressIdentifier()
    private val neverSavedSiteRepository: NeverSavedSiteRepository = mock()
    private val autofillSurvey: AutofillSurvey = mock()
    private val testee = AutofillSettingsViewModel(
        autofillStore = mockStore,
        clipboardInteractor = clipboardInteractor,
        deviceAuthenticator = deviceAuthenticator,
        pixel = pixel,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        credentialListFilter = credentialListFilter,
        faviconManager = faviconManager,
        webUrlIdentifier = webUrlIdentifier,
        emailManager = emailManager,
        duckAddressStatusRepository = duckAddressStatusRepository,
        duckAddressIdentifier = duckAddressIdentifier,
        syncEngine = mock(),
        neverSavedSiteRepository = neverSavedSiteRepository,
        autofillSurvey = autofillSurvey,
    )

    @Before
    fun setup() {
        whenever(webUrlIdentifier.isLikelyAUrl(anyOrNull())).thenReturn(true)

        runTest {
            whenever(mockStore.getAllCredentials()).thenReturn(emptyFlow())
            whenever(neverSavedSiteRepository.neverSaveListCount()).thenReturn(emptyFlow())
            whenever(deviceAuthenticator.isAuthenticationRequiredForAutofill()).thenReturn(true)
        }
    }

    @Test
    fun whenUserEnablesAutofillThenViewStateUpdatedToReflectChange() = runTest {
        testee.onEnableAutofill()
        testee.viewState.test {
            assertTrue(this.awaitItem().autofillEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserDisablesAutofillThenViewStateUpdatedToReflectChange() = runTest {
        testee.onDisableAutofill()
        testee.viewState.test {
            assertFalse(this.awaitItem().autofillEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserEnablesAutofillThenCorrectPixelFired() {
        testee.onEnableAutofill()
        verify(pixel).fire(AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED)
    }

    @Test
    fun whenUserDisablesAutofillThenCorrectPixelFired() {
        testee.onDisableAutofill()
        verify(pixel).fire(AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED)
    }

    @Test
    fun whenUserCopiesPasswordThenCommandIssuedToShowChange() = runTest {
        testee.onCopyPassword("hello")

        verify(clipboardInteractor).copyToClipboard("hello", isSensitive = true)
        testee.commands.test {
            awaitItem().first().assertCommandType(ShowUserPasswordCopied::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserCopiesPasswordPixelSent() = runTest {
        testee.onCopyPassword("hello")
        verify(pixel).fire(AUTOFILL_COPY_PASSWORD)
    }

    @Test
    fun whenUserCopiesUsernameThenCommandIssuedToShowChange() = runTest {
        testee.onCopyUsername("username")

        verify(clipboardInteractor).copyToClipboard("username", isSensitive = false)
        testee.commands.test {
            awaitItem().first().assertCommandType(ShowUserUsernameCopied::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserCopiesUsernameThenPixelSent() = runTest {
        testee.onCopyUsername("username")
        verify(pixel).fire(AUTOFILL_COPY_USERNAME)
    }

    @Test
    fun whenUserDeletesViewedCredentialsThenStoreDeletionCalled() = runTest {
        val credentials = someCredentials()
        testee.onDeleteCredentials(credentials)
        verify(mockStore).deleteCredentials(credentials.id!!)
    }

    @Test
    fun whenUserDeletesViewedCredentialsThenPixelSent() = runTest {
        val credentials = someCredentials()
        testee.onDeleteCredentials(credentials)
        verify(pixel).fire(AUTOFILL_DELETE_LOGIN)
    }

    @Test
    fun whenUserDeletesViewedCredentialsLaunchedDirectlyThenCredentialsDeleted() = runTest {
        val credentials = someCredentials()
        testee.onViewCredentials(credentials)
        testee.onDeleteCredentials(credentials)
        verify(mockStore).deleteCredentials(credentials.id!!)
    }

    @Test
    fun whenOnViewCredentialsCalledFromListThenShowCredentialViewingMode() = runTest {
        val credentials = someCredentials()
        testee.onViewCredentials(credentials)

        testee.commands.test {
            assertEquals(ShowCredentialMode, awaitItem().first())
            cancelAndIgnoreRemainingEvents()
        }
        testee.viewState.test {
            assertEquals(CredentialMode.Viewing(credentials, showLinkButton = true), this.awaitItem().credentialMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnViewCredentialsCalledFirstThenShowCredentialViewingMode() = runTest {
        val credentials = someCredentials()
        testee.onViewCredentials(credentials)

        testee.commands.test {
            assertEquals(ShowCredentialMode, awaitItem().first())
            cancelAndIgnoreRemainingEvents()
        }
        testee.viewState.test {
            assertEquals(CredentialMode.Viewing(credentials, showLinkButton = true), this.awaitItem().credentialMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnViewCredentialsCalledWithWebsiteThenShowLinkButton() = runTest {
        whenever(webUrlIdentifier.isLikelyAUrl(anyOrNull())).thenReturn(true)
        val credentials = someCredentials()
        testee.onViewCredentials(credentials)

        testee.commands.test {
            assertEquals(ShowCredentialMode, awaitItem().first())
            cancelAndIgnoreRemainingEvents()
        }
        testee.viewState.test {
            assertEquals(CredentialMode.Viewing(credentials, showLinkButton = true), this.awaitItem().credentialMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnViewCredentialsCalledWithoutValidWebsiteThenHideLinkButton() = runTest {
        whenever(webUrlIdentifier.isLikelyAUrl(anyOrNull())).thenReturn(false)
        val credentials = someCredentials()
        testee.onViewCredentials(credentials)

        testee.commands.test {
            assertEquals(ShowCredentialMode, awaitItem().first())
            cancelAndIgnoreRemainingEvents()
        }
        testee.viewState.test {
            assertEquals(CredentialMode.Viewing(credentials, showLinkButton = false), this.awaitItem().credentialMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnEditCredentialsCalledThenShowCredentialEditingMode() = runTest {
        val credentials = someCredentials()
        testee.onViewCredentials(credentials)
        testee.onEditCurrentCredentials()

        testee.viewState.test {
            assertEquals(
                EditingExisting(credentials, startedCredentialModeWithEdit = false, hasPopulatedFields = true),
                this.awaitItem().credentialMode,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnEditCredentialsCalledDirectlyThenShowCredentialEditingMode() = runTest {
        val credentials = someCredentials()
        testee.onEditCredentials(credentials)

        testee.viewState.test {
            assertEquals(
                EditingExisting(credentials, startedCredentialModeWithEdit = true, hasPopulatedFields = false),
                this.awaitItem().credentialMode,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnEditCredentialsCalledNotFromViewThenShowCredentialEditingMode() = runTest {
        val credentials = someCredentials()

        testee.onEditCredentials(credentials)

        testee.commands.test {
            assertEquals(
                ShowCredentialMode,
                this.awaitItem().first(),
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.viewState.test {
            assertEquals(
                EditingExisting(credentials, startedCredentialModeWithEdit = true, hasPopulatedFields = false),
                this.awaitItem().credentialMode,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnCredentialEditModePopulatedThenViewStateUpdated() = runTest {
        val credentials = someCredentials()

        testee.onEditCredentials(credentials)
        testee.onCredentialEditModePopulated()

        testee.viewState.test {
            assertEquals(
                EditingExisting(credentials, startedCredentialModeWithEdit = true, hasPopulatedFields = true),
                this.awaitItem().credentialMode,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLockCalledThenShowLockedMode() = runTest {
        testee.lock()

        testee.commands.test {
            awaitItem().first().assertCommandType(ShowLockedMode::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLockModeCalledMOreThanOnceThenShowLockedModeOnlyOnce() = runTest {
        testee.lock()

        testee.commands.test {
            val count = awaitItem().filter { it == ShowLockedMode }.size
            assertEquals(1, count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUnlockCalledThenExitLockedMode() = runTest {
        testee.unlock()

        testee.commands.test {
            val commands = this.awaitItem()
            assertTrue(commands.contains(ExitLockedMode))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDisableCalledThenShowDisabledMode() = runTest {
        testee.disabled()

        testee.commands.test {
            assertEquals(
                listOf(
                    ExitListMode,
                    ExitCredentialMode,
                    ExitLockedMode,
                    ShowDisabledMode,
                ),
                this.expectMostRecentItem().toList(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSaveNewCredentialsThenPixelSent() = runTest {
        testee.onCreateNewCredentials()
        val credentials = someCredentials()
        testee.saveOrUpdateCredentials(credentials)
        verify(pixel).fire(AutofillPixelNames.AUTOFILL_MANUALLY_SAVE_CREDENTIAL)
    }

    @Test
    fun whenUpdateCredentialsCalledThenUpdateAutofillStore() = runTest {
        val credentials = someCredentials()
        testee.onEditCredentials(credentials)

        val updatedCredentials = credentials.copy(username = "helloworld123")
        whenever(mockStore.updateCredentials(credentials)).thenReturn(updatedCredentials)
        testee.saveOrUpdateCredentials(updatedCredentials)

        verify(mockStore).updateCredentials(updatedCredentials)
    }

    @Test
    fun whenUpdateCredentialsCalledThenUpdatePixelSent() = runTest {
        val credentials = someCredentials()
        testee.onEditCredentials(credentials)

        val updatedCredentials = credentials.copy(username = "helloworld123")
        whenever(mockStore.updateCredentials(credentials)).thenReturn(updatedCredentials)
        testee.saveOrUpdateCredentials(updatedCredentials)

        verify(pixel).fire(AutofillPixelNames.AUTOFILL_MANUALLY_UPDATE_CREDENTIAL)
    }

    @Test
    fun whenUpdateCredentialsCalledFromManualCreationThenSaveAutofillStore() = runTest {
        val credentials = someCredentials()
        testee.onCreateNewCredentials()
        testee.saveOrUpdateCredentials(credentials)
        verify(mockStore).saveCredentials(any(), eq(credentials))
    }

    @Test
    fun whenOnExitEditModeThenUpdateCredentialModeStateToViewing() = runTest {
        val credentials = someCredentials()
        testee.onViewCredentials(credentials)
        testee.onEditCurrentCredentials()

        testee.onCancelEditMode()

        testee.viewState.test {
            assertEquals(CredentialMode.Viewing(credentials, showLinkButton = true), this.awaitItem().credentialMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnExitEditModeButNotFromViewThenExitCredentialMode() = runTest {
        val credentials = someCredentials()
        testee.onEditCredentials(credentials)

        testee.onCancelEditMode()

        testee.commands.test {
            awaitItem().last().assertCommandType(ExitCredentialMode::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnExitCredentialModeThenExitCredentialMode() = runTest {
        testee.onExitCredentialMode()

        testee.commands.test {
            awaitItem().first().assertCommandType(ExitCredentialMode::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLockingAndUnlockingPreviousViewStateRestoredIfWasListMode() = runTest {
        testee.onInitialiseListMode()
        testee.lock()
        testee.unlock()

        testee.viewState.test {
            assertEquals(CredentialMode.ListMode, this.awaitItem().credentialMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLockingAndUnlockingPreviousViewStateRestoredIfWasCredentialViewingMode() = runTest {
        testee.onViewCredentials(someCredentials())
        testee.lock()
        testee.unlock()

        testee.viewState.test {
            assertTrue(this.awaitItem().credentialMode is CredentialMode.Viewing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenInEditModeAndChangedToDisabledThenUpdateNotInCredentialModeAndShowDisabledMode() = runTest {
        testee.onEditCredentials(someCredentials())
        testee.disabled()

        testee.commands.test {
            val commands = expectMostRecentItem().toList()
            assertTrue(commands[1] is ExitListMode)
            assertTrue(commands[2] is ExitCredentialMode)
            assertTrue(commands[3] is ExitLockedMode)
            assertTrue(commands[4] is ShowDisabledMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAllowSaveInEditModeSetToFalseThenUpdateViewStateToEditingSaveableFalse() = runTest {
        val credentials = someCredentials()
        testee.onViewCredentials(credentials)
        testee.onEditCurrentCredentials()

        testee.allowSaveInEditMode(false)

        testee.viewState.test {
            val finalResult = this.expectMostRecentItem()
            assertEquals(
                EditingExisting(credentials, saveable = false, startedCredentialModeWithEdit = false, hasPopulatedFields = true),
                finalResult.credentialMode,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchDeviceAuthWithDeviceUnsupportedThenEmitUnsupportedModeCommand() = runTest {
        configureDeviceToBeUnsupported()
        testee.launchDeviceAuth()

        testee.commands.test {
            assertEquals(
                listOf(
                    ExitListMode,
                    ExitCredentialMode,
                    ExitLockedMode,
                    ShowDeviceUnsupportedMode,
                ),
                this.expectMostRecentItem().toList(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchDeviceAuthThenUpdateStateToIsAuthenticatingAndEmitLaunchDeviceCommand() = runTest {
        configureDeviceToBeSupported()
        configureDeviceToHaveValidAuthentication(true)
        configureStoreToHaveThisManyCredentialsStored(1)
        testee.launchDeviceAuth()

        testee.commands.test {
            awaitItem().first().assertCommandType(LaunchDeviceAuth::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchDeviceAuthWithNoSavedCredentialsThenIsUnlockedAndAuthNotLaunched() = runTest {
        configureDeviceToHaveValidAuthentication(true)
        configureStoreToHaveThisManyCredentialsStored(0)
        testee.launchDeviceAuth()

        testee.commands.test {
            val commands = this.awaitItem()
            assertTrue(commands.contains(ExitLockedMode))
            assertFalse(commands.contains(LaunchDeviceAuth))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchDeviceAuthWithNoValidAuthThenDisabledShown() = runTest {
        configureDeviceToHaveValidAuthentication(false)
        testee.launchDeviceAuth()

        testee.commands.test {
            val commands = this.awaitItem()
            assertTrue(commands.contains(ExitLockedMode))
            assertFalse(commands.contains(LaunchDeviceAuth))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchedDeviceAuthHasEndedAndLaunchedAgainThenEmitLaunchDeviceCommandTwice() = runTest {
        configureDeviceToBeSupported()
        configureDeviceToHaveValidAuthentication(true)
        configureStoreToHaveThisManyCredentialsStored(1)
        testee.launchDeviceAuth()
        testee.launchDeviceAuth()

        testee.commands.test {
            assertEquals(
                listOf(LaunchDeviceAuth, LaunchDeviceAuth),
                this.expectMostRecentItem().toList(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchDeviceAuthWithNoValidAuthenticationThenShowDisabledViewAndAuthNotLaunched() = runTest {
        configureDeviceToBeSupported()
        configureDeviceToHaveValidAuthentication(false)
        testee.launchDeviceAuth()
        testee.commands.test {
            val commands = awaitItem()
            assertTrue(commands.contains(ShowDisabledMode))
            assertFalse(commands.contains(LaunchDeviceAuth))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenObserveCredentialsCalledWithAutofillDisabledThenAutofillEnabledStateIsReturned() = runTest {
        whenever(mockStore.autofillEnabled).thenReturn(false)
        configureDeviceToHaveValidAuthentication(true)

        testee.onViewCreated()
        testee.viewState.test {
            assertEquals(false, this.awaitItem().autofillEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenObserveCredentialsCalledWithAutofillEnabledThenAutofillEnabledStateIsReturned() = runTest {
        whenever(mockStore.autofillEnabled).thenReturn(true)
        configureDeviceToHaveValidAuthentication(true)

        testee.onViewCreated()
        testee.viewState.test {
            assertEquals(true, this.awaitItem().autofillEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSearchQueryChangesEmptyThenShouldShowEnableToggle() = runTest {
        testee.onSearchQueryChanged("")

        testee.onViewCreated()
        testee.viewState.test {
            assertEquals(true, this.awaitItem().showAutofillEnabledToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSearchQueryChangesNonEmptyThenShouldNotShowEnableToggle() = runTest {
        testee.onSearchQueryChanged("foo")

        testee.onViewCreated()
        testee.viewState.test {
            assertEquals(false, this.awaitItem().showAutofillEnabledToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenScreenLaunchedFromSnackbarThenNoLaunchPixelSent() {
        testee.sendLaunchPixel(BrowserSnackbar)
        verify(pixel, never()).fire(any<PixelName>(), any(), any(), eq(COUNT))
    }

    @Test
    fun whenScreenLaunchedFromBrowserThenCorrectLaunchPixelSent() {
        testee.sendLaunchPixel(BrowserOverflow)
        verify(pixel).fire(eq(AUTOFILL_MANAGEMENT_SCREEN_OPENED), any(), any(), eq(COUNT))
    }

    @Test
    fun whenScreenLaunchedFromSyncThenCorrectLaunchPixelSent() {
        testee.sendLaunchPixel(Sync)
        verify(pixel).fire(eq(AUTOFILL_MANAGEMENT_SCREEN_OPENED), any(), any(), eq(COUNT))
    }

    @Test
    fun whenScreenLaunchedFromSettingsActivityThenCorrectLaunchPixelSent() {
        testee.sendLaunchPixel(SettingsActivity)
        verify(pixel).fire(eq(AUTOFILL_MANAGEMENT_SCREEN_OPENED), any(), any(), eq(COUNT))
    }

    @Test
    fun whenUserFirstChoosesToResetNeverSavedSiteListThenCorrectPixelFired() = runTest {
        testee.onResetNeverSavedSitesInitialSelection()
        verify(pixel).fire(AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISPLAYED)
    }

    @Test
    fun whenUserConfirmsTheyWantToResetNeverSavedSiteListThenRepositoryCleared() = runTest {
        testee.onUserConfirmationToClearNeverSavedSites()
        verify(neverSavedSiteRepository).clearNeverSaveList()
    }

    @Test
    fun whenUserConfirmsTheyWantToResetNeverSavedSiteListThenCorrectPixelFired() = runTest {
        testee.onUserConfirmationToClearNeverSavedSites()
        verify(pixel).fire(AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_CONFIRMED)
    }

    @Test
    fun whenUserDismissesPromptToResetNeverSavedSiteListThenCorrectPixelFired() = runTest {
        testee.onUserCancelledFromClearNeverSavedSitesPrompt()
        verify(pixel).fire(AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_CONFIRMATION_PROMPT_DISMISSED)
    }

    @Test
    fun whenDeleteAllFirstCalledWithNoSavedLoginsThenNoCommandSentToShowConfirmationDialog() = runTest {
        configureStoreToHaveThisManyCredentialsStored(0)
        testee.onViewCreated()
        testee.onDeleteAllPasswordsInitialSelection()
        testee.commandsListView.test {
            awaitItem().verifyDoesNotHaveCommandToShowDeleteAllConfirmation()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAllFirstCalledWithOneSavedLoginThenCommandSentToShowConfirmationDialog() = runTest {
        configureStoreToHaveThisManyCredentialsStored(1)
        testee.onViewCreated()
        testee.onDeleteAllPasswordsInitialSelection()
        testee.commandsListView.test {
            awaitItem().verifyHasCommandToShowDeleteAllConfirmation(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAllPasswordsConfirmedButNoPasswordsSavedThenDoesNotIssueCommandToShowUndoSnackbar() = runTest {
        whenever(mockStore.deleteAllCredentials()).thenReturn(emptyList())
        testee.onDeleteAllPasswordsConfirmed()
        testee.commandsListView.test {
            awaitItem().verifyHasCommandToAuthenticateMassDeletion()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAllPasswordsConfirmedWithPasswordsSavedThenDoesIssueCommandToShowUndoSnackbar() = runTest {
        testee.onDeleteAllPasswordsConfirmed()
        testee.commandsListView.test {
            awaitItem().verifyHasCommandToAuthenticateMassDeletion()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAuthenticationSucceedsToMassDeletePasswordsThenDoesIssueCommandToShowUndoSnackbar() = runTest {
        whenever(mockStore.deleteAllCredentials()).thenReturn(listOf(someCredentials()))
        testee.onAuthenticatedToDeleteAllPasswords()
        testee.commands.test {
            awaitItem().verifyDoesHaveCommandToShowUndoDeletionSnackbar(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAuthenticationSucceedsToMassDeletePasswordsThenPixelSent() = runTest {
        whenever(mockStore.deleteAllCredentials()).thenReturn(listOf(someCredentials()))
        testee.onAuthenticatedToDeleteAllPasswords()
        verify(pixel).fire(AutofillPixelNames.AUTOFILL_DELETE_ALL_LOGINS)
    }

    @Test
    fun whenDeleteAllFirstCalledWithManySavedLoginThenCommandSentToShowConfirmationDialog() = runTest {
        configureStoreToHaveThisManyCredentialsStored(100)
        testee.onViewCreated()
        testee.onDeleteAllPasswordsInitialSelection()
        testee.commandsListView.test {
            awaitItem().verifyHasCommandToShowDeleteAllConfirmation(100)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNoSurveysAvailableThenNoSurveyInViewState() = runTest {
        testee.onViewStarted()
        verifySurveyNotAvailable()
    }

    @Test
    fun whenUnusedSurveyAvailableThenSurveyInViewState() = runTest {
        whenever(autofillSurvey.firstUnusedSurvey()).thenReturn(SurveyDetails("surveyId-1", "example.com"))
        testee.onInitialiseListMode()
        "surveyId-1".verifySurveyAvailable()
    }

    @Test
    fun whenSurveyShownThenNoSurveyInViewState() = runTest {
        testee.onSurveyShown("surveyId-1")
        verifySurveyNotAvailable()
    }

    @Test
    fun whenSurveyShownThenSurveyMarkedAsUsed() = runTest {
        testee.onSurveyShown("surveyId-1")
        verify(autofillSurvey).recordSurveyAsUsed("surveyId-1")
    }

    @Test
    fun whenSurveyPromptDismissedThenNoSurveyInViewState() = runTest {
        testee.onSurveyPromptDismissed("surveyId-1")
        verifySurveyNotAvailable()
    }

    @Test
    fun whenSurveyPromptDismissedThenSurveyMarkedAsUsed() = runTest {
        testee.onSurveyPromptDismissed("surveyId-1")
        verify(autofillSurvey).recordSurveyAsUsed("surveyId-1")
    }

    private fun String.verifySurveyAvailable() {
        val survey = testee.viewState.value.survey
        assertNotNull(survey)
        assertEquals(this, survey!!.id)
    }

    private fun verifySurveyNotAvailable() {
        val survey = testee.viewState.value.survey
        assertNull(survey)
    }

    private fun List<ListModeCommand>.verifyHasCommandToShowDeleteAllConfirmation(expectedNumberOfCredentialsToDelete: Int) {
        val confirmationCommand = this.firstOrNull { it is LaunchDeleteAllPasswordsConfirmation }
        assertNotNull(confirmationCommand)
        assertEquals(expectedNumberOfCredentialsToDelete, (confirmationCommand as LaunchDeleteAllPasswordsConfirmation).numberToDelete)
    }

    private fun List<ListModeCommand>.verifyDoesNotHaveCommandToShowDeleteAllConfirmation() {
        val confirmationCommand = this.firstOrNull { it is LaunchDeleteAllPasswordsConfirmation }
        assertNull(confirmationCommand)
    }

    private fun List<Command>.verifyDoesHaveCommandToShowUndoDeletionSnackbar(expectedNumberOfCredentialsToDelete: Int) {
        val confirmationCommand = this.firstOrNull { it is OfferUserUndoMassDeletion }
        assertNotNull(confirmationCommand)
        assertEquals(expectedNumberOfCredentialsToDelete, (confirmationCommand as OfferUserUndoMassDeletion).credentials.size)
    }

    private fun List<ListModeCommand>.verifyHasCommandToAuthenticateMassDeletion() {
        val command = this.firstOrNull { it is PromptUserToAuthenticateMassDeletion }
        assertNotNull(command)
        assertTrue((command as PromptUserToAuthenticateMassDeletion).authConfiguration.requireUserAction)
    }

    private fun List<Command>.verifyDoesNotHaveCommandToShowUndoDeletionSnackbar() {
        val confirmationCommand = this.firstOrNull { it is OfferUserUndoMassDeletion }
        assertNull(confirmationCommand)
    }

    private suspend fun configureStoreToHaveThisManyCredentialsStored(value: Int) {
        whenever(mockStore.getCredentialCount()).thenReturn(flowOf(value))

        val credentialList = mutableListOf<LoginCredentials>()
        repeat(value) { credentialList.add(someCredentials()) }
        whenever(mockStore.getAllCredentials()).thenReturn(flowOf(credentialList))
    }

    private fun configureDeviceToHaveValidAuthentication(hasValidAuth: Boolean) {
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(hasValidAuth)
    }

    private fun configureDeviceToBeUnsupported() {
        whenever(mockStore.autofillAvailable).thenReturn(false)
    }

    private fun configureDeviceToBeSupported() {
        whenever(mockStore.autofillAvailable).thenReturn(true)
    }

    private fun someCredentials(): LoginCredentials {
        return LoginCredentials(
            id = -1,
            domain = "example.com",
            username = "username",
            password = "password",
        )
    }

    private fun Command.assertCommandType(expectedType: KClass<out Command>) {
        assertTrue(String.format("Unexpected command type: %s", this::class.simpleName), this::class == expectedType)
    }
}

private class TestFilterPassthrough : CredentialListFilter {
    override suspend fun filter(
        originalList: List<LoginCredentials>,
        query: String,
    ): List<LoginCredentials> {
        return originalList
    }
}
