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

package com.duckduckgo.autofill.ui.credential.management

import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.store.AutofillStore
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.Command
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.Command.*
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.CredentialMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.reflect.KClass

@ExperimentalCoroutinesApi
class AutofillSettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockStore: AutofillStore = mock()
    private val clipboardInteractor: AutofillClipboardInteractor = mock()
    private val pixel: Pixel = mock()
    private val testee = AutofillSettingsViewModel(mockStore, clipboardInteractor, pixel)

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

        verify(clipboardInteractor).copyToClipboard("hello")
        testee.commands.test {
            awaitItem().first().assertCommandType(ShowUserPasswordCopied::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserCopiesUsernameThenCommandIssuedToShowChange() = runTest {
        testee.onCopyUsername("username")

        verify(clipboardInteractor).copyToClipboard("username")
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
    fun whenOnViewCredentialsCalledFromListThenShowCredentialViewingMode() = runTest {
        val credentials = someCredentials()
        testee.onViewCredentials(credentials, false)

        testee.commands.test {
            assertEquals(ShowCredentialMode(credentials, false), awaitItem().first())
            cancelAndIgnoreRemainingEvents()
        }
        testee.viewState.test {
            assertEquals(CredentialMode.Viewing(credentials), this.awaitItem().credentialMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnViewCredentialsCalledFirstThenShowCredentialViewingMode() = runTest {
        val credentials = someCredentials()
        testee.onViewCredentials(credentials, true)

        testee.commands.test {
            assertEquals(ShowCredentialMode(credentials, true), awaitItem().first())
            cancelAndIgnoreRemainingEvents()
        }
        testee.viewState.test {
            assertEquals(CredentialMode.Viewing(credentials), this.awaitItem().credentialMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnEditCredentialsCalledThenShowCredentialEditingMode() = runTest {
        val credentials = someCredentials()

        testee.onEditCredentials(credentials, true)

        testee.viewState.test {
            assertEquals(
                CredentialMode.Editing(credentials, startedCredentialModeWithEdit = false, hasPopulatedFields = true),
                this.awaitItem().credentialMode
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnEditCredentialsCalledNotFromViewThenShowCredentialEditingMode() = runTest {
        val credentials = someCredentials()

        testee.onEditCredentials(credentials, false)

        testee.commands.test {
            assertEquals(
                ShowCredentialMode(credentials, isLaunchedDirectly = false),
                this.awaitItem().first()
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.viewState.test {
            assertEquals(
                CredentialMode.Editing(credentials, startedCredentialModeWithEdit = true, hasPopulatedFields = false),
                this.awaitItem().credentialMode
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
            assertEquals(
                listOf(
                    ExitDisabledMode,
                    ExitLockedMode,
                ),
                this.expectMostRecentItem().toList()
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.viewState.test {
            assertFalse(this.awaitItem().isLocked)
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
                    ShowDisabledMode
                ),
                this.expectMostRecentItem().toList()
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.viewState.test {
            assertTrue(this.awaitItem().isLocked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUpdateCredentialsCalledThenUpdateAutofillStore() = runTest {
        val credentials = someCredentials()
        testee.onEditCredentials(credentials, true)

        val updatedCredentials = credentials.copy(username = "helloworld123")
        whenever(mockStore.getCredentialsWithId(-1)).thenReturn(updatedCredentials)
        testee.updateCredentials(updatedCredentials)

        verify(mockStore).updateCredentials(updatedCredentials)
        testee.viewState.test {
            assertEquals(CredentialMode.Viewing(updatedCredentials), this.expectMostRecentItem().credentialMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnExitEditModeThenUpdateCredentialModeStateToViewing() = runTest {
        val credentials = someCredentials()
        testee.onEditCredentials(credentials, true)

        testee.onCancelEditMode()

        testee.viewState.test {
            assertEquals(CredentialMode.Viewing(credentials), this.awaitItem().credentialMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnExitEditModeButNotFromViewThenExitCredentialMode() = runTest {
        val credentials = someCredentials()
        testee.onEditCredentials(credentials, false)

        testee.onCancelEditMode()

        testee.commands.test {
            awaitItem().last().assertCommandType(ExitCredentialMode::class)
            cancelAndIgnoreRemainingEvents()
        }

        testee.viewState.test {
            assertEquals(CredentialMode.NotInCredentialMode, this.awaitItem().credentialMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnExitCredentialModeThenExitCredentialMode() = runTest {
        testee.onExitCredentialMode()

        testee.viewState.test {
            assertEquals(CredentialMode.NotInCredentialMode, this.awaitItem().credentialMode)
            cancelAndIgnoreRemainingEvents()
        }
        testee.commands.test {
            awaitItem().first().assertCommandType(ExitCredentialMode::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenInEditModeAndChangedToDisabledThenUpdateNotInCredentialModeAndShowDisabledMode() = runTest {
        testee.onEditCredentials(someCredentials(), true)
        testee.disabled()

        testee.viewState.test {
            val finalResult = this.expectMostRecentItem()
            assertTrue(finalResult.isLocked)
            cancelAndIgnoreRemainingEvents()
        }
        testee.commands.test {
            assertEquals(
                listOf(
                    ExitListMode,
                    ExitCredentialMode,
                    ExitLockedMode,
                    ShowDisabledMode
                ),
                this.expectMostRecentItem().toList()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAllowSaveInEditModeSetToFalseThenUpdateViewStateToEditingSaveableFalse() = runTest {
        val credentials = someCredentials()
        testee.onViewCredentials(credentials, true)
        testee.onEditCredentials(credentials, true)

        testee.allowSaveInEditMode(false)

        testee.viewState.test {
            val finalResult = this.expectMostRecentItem()
            assertEquals(
                CredentialMode.Editing(credentials, saveable = false, startedCredentialModeWithEdit = false, hasPopulatedFields = true),
                finalResult.credentialMode
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchDeviceAuthThenUpdateStateToIsAuthenticatingAndEmitLaunchDeviceCommand() = runTest {
        testee.launchDeviceAuth()

        testee.viewState.test {
            val finalResult = this.expectMostRecentItem()
            assertTrue(finalResult.isAuthenticating)
            cancelAndIgnoreRemainingEvents()
        }
        testee.commands.test {
            awaitItem().first().assertCommandType(LaunchDeviceAuth::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchDeviceAuthTwiceThenEmitLaunchDeviceCommandOnceOnly() = runTest {
        testee.launchDeviceAuth()
        testee.launchDeviceAuth()

        testee.commands.test {
            assertEquals(
                listOf(LaunchDeviceAuth),
                this.expectMostRecentItem().toList()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchedDeviceAuthHasEndedAndLaunchedAgainThenEmitLaunchDeviceCommandtwice() = runTest {
        testee.launchDeviceAuth()
        testee.onAuthenticationEnded()

        testee.viewState.test {
            val finalResult = this.expectMostRecentItem()
            assertFalse(finalResult.isAuthenticating)
            cancelAndIgnoreRemainingEvents()
        }

        testee.launchDeviceAuth()

        testee.commands.test {
            assertEquals(
                listOf(LaunchDeviceAuth, LaunchDeviceAuth),
                this.expectMostRecentItem().toList()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAuthWasLaunchAndThenDeviceAuthDisabledThenEmitViewStateWithIsAuthenticatingFalse() = runTest {
        testee.launchDeviceAuth()
        testee.disabled()

        testee.viewState.test {
            val finalResult = this.expectMostRecentItem()
            assertFalse(finalResult.isAuthenticating)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun someCredentials(): LoginCredentials {
        return LoginCredentials(
            id = -1,
            domain = "example.com",
            username = "username",
            password = "password"
        )
    }

    private fun Command.assertCommandType(expectedType: KClass<out Command>) {
        assertTrue(String.format("Unexpected command type: %s", this::class.simpleName), this::class == expectedType)
    }
}
