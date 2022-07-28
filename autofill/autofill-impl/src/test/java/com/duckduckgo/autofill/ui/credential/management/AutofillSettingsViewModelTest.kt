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
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.store.AutofillStore
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.Command
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.Command.*
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.CredentialModeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.reflect.KClass

@ExperimentalCoroutinesApi
class AutofillSettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockStore: AutofillStore = mock()
    private val clipboardInteractor: AutofillClipboardInteractor = mock()
    private val testee = AutofillSettingsViewModel(mockStore, clipboardInteractor)

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
    fun whenUserDeletesCredentialsThenIsReturnedToListMode() = runTest {
        testee.onDeleteCredentials(someCredentials())
        testee.commands.test {
            awaitItem().first().assertCommandType(ShowListMode::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserDeletesCredentialsThenStoreDeletionCalled() = runTest {
        val credentials = someCredentials()
        testee.onDeleteCredentials(credentials)
        verify(mockStore).deleteCredentials(credentials.id!!)
    }

    @Test
    fun whenOnViewCredentialsCalledThenShowCredentialViewingMode() = runTest {
        val credentials = someCredentials()
        testee.onViewCredentials(credentials)

        testee.commands.test {
            awaitItem().first().assertCommandType(ShowCredentialMode::class)
            cancelAndIgnoreRemainingEvents()
        }
        testee.viewState.test {
            assertEquals(CredentialModeState.Viewing(reset = false), this.awaitItem().credentialModeState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnEditCredentialsCalledThenShowCredentialEditingMode() = runTest {
        testee.onEditCredentials()

        testee.viewState.test {
            assertEquals(CredentialModeState.Editing, this.awaitItem().credentialModeState)
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
    fun whenUnlockCalledThenShowListMode() = runTest {
        testee.unlock()

        testee.commands.test {
            awaitItem().first().assertCommandType(ShowListMode::class)
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
            awaitItem().first().assertCommandType(ShowDisabledMode::class)
            cancelAndIgnoreRemainingEvents()
        }

        testee.viewState.test {
            assertTrue(this.awaitItem().isLocked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUpdateCredentialsCalledThenUpdateAutofillStore() = runTest {
        val someCredentials = someCredentials()
        testee.updateCredentials(someCredentials)

        verify(mockStore).updateCredentials(someCredentials)
    }

    @Test
    fun whenOnExitEditModeThenUpdateCredentialModeStateToViewing() = runTest {
        testee.onExitEditMode(true)

        testee.viewState.test {
            assertEquals(CredentialModeState.Viewing(reset = true), this.awaitItem().credentialModeState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnExitViewModeThenShowListMode() = runTest {
        testee.onExitViewMode()

        testee.viewState.test {
            assertEquals(CredentialModeState.NotInCredentialMode, this.awaitItem().credentialModeState)
            cancelAndIgnoreRemainingEvents()
        }
        testee.commands.test {
            awaitItem().first().assertCommandType(ShowListMode::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenInEditModeAndChangedToDisabledThenUpdateNotInCredentialModeAndShowDisabledMode() = runTest {
        testee.onEditCredentials()
        testee.disabled()

        testee.viewState.test {
            val finalResult = this.expectMostRecentItem()
            assertEquals(CredentialModeState.NotInCredentialMode, finalResult.credentialModeState)
            assertTrue(finalResult.isLocked)
            cancelAndIgnoreRemainingEvents()
        }
        testee.commands.test {
            expectMostRecentItem().first().assertCommandType(ShowDisabledMode::class)
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
