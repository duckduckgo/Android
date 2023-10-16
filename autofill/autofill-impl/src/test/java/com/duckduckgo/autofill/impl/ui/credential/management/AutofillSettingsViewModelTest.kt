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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitCredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitListMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ExitLockedMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.LaunchDeviceAuth
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowCredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowDeviceUnsupportedMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowDisabledMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowLockedMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowUserPasswordCopied
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.Command.ShowUserUsernameCopied
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillSettingsViewModel.CredentialMode.EditingExisting
import com.duckduckgo.autofill.impl.ui.credential.management.searching.CredentialListFilter
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.duckaddress.DuckAddressIdentifier
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.duckaddress.RealDuckAddressIdentifier
import com.duckduckgo.autofill.impl.ui.credential.repository.DuckAddressStatusRepository
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class AutofillSettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockStore: AutofillStore = mock()
    private val emailManager: EmailManager = mock()
    private val duckAddressStatusRepository: DuckAddressStatusRepository = mock()
    private val clipboardInteractor: AutofillClipboardInteractor = mock()
    private val pixel: Pixel = mock()
    private val deviceAuthenticator: DeviceAuthenticator = mock()
    private val credentialListFilter: CredentialListFilter = mock()
    private val faviconManager: FaviconManager = mock()
    private val webUrlIdentifier: WebUrlIdentifier = mock()
    private val duckAddressIdentifier: DuckAddressIdentifier = RealDuckAddressIdentifier()
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
    )

    @Before
    fun setup() {
        whenever(webUrlIdentifier.isLikelyAUrl(anyOrNull())).thenReturn(true)
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
    fun whenUserCopiesPasswordThenCommandIssuedToShowChange() = runTest {
        testee.onCopyPassword("hello")

        verify(clipboardInteractor).copyToClipboard("hello", isSensitive = true)
        testee.commands.test {
            awaitItem().first().assertCommandType(ShowUserPasswordCopied::class)
            cancelAndIgnoreRemainingEvents()
        }
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
    fun whenUserDeletesViewedCredentialsThenStoreDeletionCalled() = runTest {
        val credentials = someCredentials()
        testee.onDeleteCredentials(credentials)
        verify(mockStore).deleteCredentials(credentials.id!!)
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
    fun whenUpdateCredentialsCalledThenUpdateAutofillStore() = runTest {
        val credentials = someCredentials()
        testee.onEditCredentials(credentials)

        val updatedCredentials = credentials.copy(username = "helloworld123")
        whenever(mockStore.updateCredentials(credentials)).thenReturn(updatedCredentials)
        testee.saveOrUpdateCredentials(updatedCredentials)

        verify(mockStore).updateCredentials(updatedCredentials)
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
        testee.onShowListMode()
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

        testee.observeCredentials()
        testee.viewState.test {
            assertEquals(false, this.awaitItem().autofillEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenObserveCredentialsCalledWithAutofillEnabledThenAutofillEnabledStateIsReturned() = runTest {
        whenever(mockStore.autofillEnabled).thenReturn(true)
        configureDeviceToHaveValidAuthentication(true)

        testee.observeCredentials()
        testee.viewState.test {
            assertEquals(true, this.awaitItem().autofillEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSearchQueryChangesEmptyThenShouldShowEnableToggle() = runTest {
        testee.onSearchQueryChanged("")

        testee.observeCredentials()
        testee.viewState.test {
            assertEquals(true, this.awaitItem().showAutofillEnabledToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSearchQueryChangesNonEmptyThenShouldNotShowEnableToggle() = runTest {
        testee.onSearchQueryChanged("foo")

        testee.observeCredentials()
        testee.viewState.test {
            assertEquals(false, this.awaitItem().showAutofillEnabledToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun configureStoreToHaveThisManyCredentialsStored(value: Int) {
        whenever(mockStore.getCredentialCount()).thenReturn(flowOf(value))
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
