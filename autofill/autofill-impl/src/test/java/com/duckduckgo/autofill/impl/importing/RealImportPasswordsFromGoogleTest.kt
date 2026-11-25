/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.api.ImportPasswordsFromGoogle.ImportPasswordsEvent
import com.duckduckgo.autofill.impl.importing.capability.PasswordImportAvailability
import com.duckduckgo.autofill.impl.importing.gpm.ImportPasswordsFromGoogleScreen
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealImportPasswordsFromGoogleTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val context: Context = mock()
    private val passwordImportAvailability: PasswordImportAvailability = mock()
    private val passwordImportEvents = PasswordImportEvents()
    private val globalActivityStarter: GlobalActivityStarter = mock()
    private val launchIntent = Intent()

    private val testee = RealImportPasswordsFromGoogle(
        passwordImportAvailability = passwordImportAvailability,
        passwordImportEvents = passwordImportEvents,
        globalActivityStarter = globalActivityStarter,
    )

    @Before
    fun setUp() = runTest {
        whenever(passwordImportAvailability.canImport()).thenReturn(true)
        whenever(globalActivityStarter.startIntent(any(), any<ImportPasswordsFromGoogleScreen>())).thenReturn(launchIntent)
    }

    @Test
    fun whenEitherImportMethodAvailableThenSupported() = runTest {
        assertTrue(testee.isSupported())
    }

    @Test
    fun whenNoImportMethodAvailableThenNotSupported() = runTest {
        whenever(passwordImportAvailability.canImport()).thenReturn(false)

        assertFalse(testee.isSupported())
    }

    @Test
    fun whenSupportedThenLaunchStartsTheImportScreenWithTheLaunchSource() = runTest {
        launch()

        verify(globalActivityStarter).startIntent(context, ImportPasswordsFromGoogleScreen(SOURCE))
        verify(context).startActivity(launchIntent)
    }

    @Test
    fun whenNotSupportedThenLaunchStartsNothingAndEmitsPermanentError() = runTest {
        whenever(passwordImportAvailability.canImport()).thenReturn(false)

        launch().test {
            assertEquals(ImportPasswordsEvent.Error.Permanent, awaitItem())
            awaitComplete()
        }
        verify(context, never()).startActivity(any())
    }

    @Test
    fun whenThereIsNoScreenToStartThenLaunchEmitsPermanentError() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<ImportPasswordsFromGoogleScreen>())).thenReturn(null)

        launch().test {
            assertEquals(ImportPasswordsEvent.Error.Permanent, awaitItem())
            awaitComplete()
        }
        verify(context, never()).startActivity(any())
    }

    @Test
    fun whenPasswordsAreSavedAfterLaunchThenEmitsProgressThenTheCountsAndCompletes() = runTest {
        launch().test {
            passwordImportEvents.emit(ImportPasswordsEvent.InProgress)
            passwordImportEvents.emit(FINISHED)

            assertEquals(ImportPasswordsEvent.InProgress, awaitItem())
            assertEquals(FINISHED, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenFinishedWithoutProgressThenEmitsTheCountsAndCompletes() = runTest {
        launch().test {
            passwordImportEvents.emit(FINISHED)

            assertEquals(FINISHED, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenTheFlowEndsWithoutImportingThenEmitsThatOutcomeAndCompletes() = runTest {
        launch().test {
            passwordImportEvents.emit(ImportPasswordsEvent.UserCancelled)

            assertEquals(ImportPasswordsEvent.UserCancelled, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenSavingFailsAfterProgressThenEmitsProgressThenTheErrorAndCompletes() = runTest {
        launch().test {
            passwordImportEvents.emit(ImportPasswordsEvent.InProgress)
            passwordImportEvents.emit(ImportPasswordsEvent.Error.Transient)

            assertEquals(ImportPasswordsEvent.InProgress, awaitItem())
            assertEquals(ImportPasswordsEvent.Error.Transient, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenAnEarlierImportFinishedBeforeLaunchThenItsResultIsNotEmitted() = runTest {
        passwordImportEvents.emit(ImportPasswordsEvent.Finished(imported = 9, skipped = 9))

        launch().test {
            expectNoEvents()

            passwordImportEvents.emit(FINISHED)

            assertEquals(FINISHED, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenALaunchEndedThenTheNextLaunchReportsOnlyItsOwnOutcome() = runTest {
        launch().test {
            passwordImportEvents.emit(ImportPasswordsEvent.UserCancelled)
            assertEquals(ImportPasswordsEvent.UserCancelled, awaitItem())
            awaitComplete()
        }

        launch().test {
            passwordImportEvents.emit(FINISHED)

            assertEquals(FINISHED, awaitItem())
            awaitComplete()
        }
    }

    private suspend fun launch(): Flow<ImportPasswordsEvent> = testee.launchImport(context, SOURCE)

    private companion object {
        val SOURCE = AutofillImportLaunchSource.Onboarding
        val FINISHED = ImportPasswordsEvent.Finished(imported = 3, skipped = 1)
    }
}
