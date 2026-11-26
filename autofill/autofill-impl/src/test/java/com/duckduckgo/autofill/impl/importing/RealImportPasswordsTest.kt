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

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.api.ImportPasswords.ImportPasswordsParams
import com.duckduckgo.autofill.api.ImportPasswords.ImportPasswordsRequest
import com.duckduckgo.autofill.api.ImportPasswords.ImportPasswordsResult
import com.duckduckgo.autofill.api.ImportPasswords.ImportPasswordsStatus
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult
import com.duckduckgo.autofill.impl.importing.capability.PasswordImportAvailability
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealImportPasswordsTest {

    private val context: Context = mock()
    private val passwordImportAvailability: PasswordImportAvailability = mock()
    private val credentialImporter: CredentialImporter = mock()
    private val globalActivityStarter: GlobalActivityStarter = mock()
    private val launchIntent = Intent()

    private lateinit var testee: RealImportPasswords
    private lateinit var contract: ActivityResultContract<ImportPasswordsRequest, ImportPasswordsResult>

    @Before
    fun setUp() {
        testee = RealImportPasswords(
            passwordImportAvailability = passwordImportAvailability,
            credentialImporter = credentialImporter,
            globalActivityStarter = globalActivityStarter,
        )
        contract = testee.importContract()

        whenever(globalActivityStarter.startIntent(any(), any<ImportPasswordsHostScreen>())).thenReturn(launchIntent)
    }

    @Test
    fun whenEitherImportMethodAvailableThenSupported() = runTest {
        whenever(passwordImportAvailability.canImport()).thenReturn(true)
        assertTrue(testee.isSupported())
    }

    @Test
    fun whenNoImportMethodAvailableThenNotSupported() = runTest {
        whenever(passwordImportAvailability.canImport()).thenReturn(false)
        assertFalse(testee.isSupported())
    }

    @Test
    fun whenTheImporterReportsProgressThenImportStatusReportsItWithTheCounts() = runTest {
        whenever(credentialImporter.getImportStatus()).thenReturn(
            flowOf(ImportResult.InProgress, ImportResult.Finished(savedCredentials = 3, numberSkipped = 1, source = SOURCE)),
        )

        assertEquals(
            listOf(ImportPasswordsStatus.InProgress, ImportPasswordsStatus.Finished(imported = 3, skipped = 1)),
            testee.importStatus().toList(),
        )
    }

    @Test
    fun whenLaunchingThenExactlyOneRequestIsLaunched() = runTest {
        whenever(passwordImportAvailability.canImport()).thenReturn(true)
        val launcher = FakeActivityResultLauncher()

        testee.launchImport(PARAMS, launcher)

        assertEquals(1, launcher.launchedInputs.size)
    }

    @Test
    fun whenSupportedThenThereIsNoSynchronousResult() = runTest {
        assertNull(contract.getSynchronousResult(context, launchedRequest(isSupported = true)))
    }

    @Test
    fun whenNotSupportedThenSynchronousResultIsUnavailable() = runTest {
        val result = contract.getSynchronousResult(context, launchedRequest(isSupported = false))
        assertEquals(ImportPasswordsResult.Unavailable, result?.value)
    }

    @Test
    fun whenRequestWasNotCreatedByLaunchImportThenSynchronousResultIsUnavailable() {
        val request = object : ImportPasswordsRequest {
            override val params = PARAMS
        }
        assertEquals(ImportPasswordsResult.Unavailable, contract.getSynchronousResult(context, request)?.value)
    }

    @Test
    fun whenThereIsNoScreenToStartThenSynchronousResultIsUnavailable() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<ImportPasswordsHostScreen>())).thenReturn(null)

        val result = contract.getSynchronousResult(context, launchedRequest(isSupported = true))

        assertEquals(ImportPasswordsResult.Unavailable, result?.value)
    }

    @Test
    fun whenCreatingTheIntentThenItOpensTheImportScreenWithTheLaunchSource() = runTest {
        val intent = contract.createIntent(context, launchedRequest(isSupported = true))

        assertSame(launchIntent, intent)
        verify(globalActivityStarter).startIntent(context, ImportPasswordsHostScreen(SOURCE))
    }

    @Test
    fun whenNoResultIsReturnedThenUserCancelled() {
        assertEquals(ImportPasswordsResult.UserCancelled, contract.parseResult(Activity.RESULT_CANCELED, null))
        assertEquals(ImportPasswordsResult.UserCancelled, contract.parseResult(Activity.RESULT_OK, Intent()))
    }

    @Test
    fun whenTheHostReturnsAResultThenItIsMappedToThePublicResult() {
        assertEquals(ImportPasswordsResult.Success, parse(ImportPasswordsHostResult.Success))
        assertEquals(ImportPasswordsResult.UserCancelled, parse(ImportPasswordsHostResult.UserCancelled))
        assertEquals(ImportPasswordsResult.Error.Transient, parse(ImportPasswordsHostResult.TransientError))
        assertEquals(ImportPasswordsResult.Error.Permanent, parse(ImportPasswordsHostResult.PermanentError))
    }

    private suspend fun launchedRequest(isSupported: Boolean): ImportPasswordsRequest {
        whenever(passwordImportAvailability.canImport()).thenReturn(isSupported)
        val launcher = FakeActivityResultLauncher()
        testee.launchImport(PARAMS, launcher)
        return launcher.launchedInputs.single()
    }

    private fun parse(result: ImportPasswordsHostResult): ImportPasswordsResult {
        val intent = Intent().putExtra(ImportPasswordsHostResult.RESULT_KEY, result)
        return contract.parseResult(Activity.RESULT_OK, intent)
    }

    private class FakeActivityResultLauncher : ActivityResultLauncher<ImportPasswordsRequest>() {
        val launchedInputs = mutableListOf<ImportPasswordsRequest>()

        override fun getContract(): ActivityResultContract<ImportPasswordsRequest, *> = throw UnsupportedOperationException()

        override fun launch(input: ImportPasswordsRequest, options: ActivityOptionsCompat?) {
            launchedInputs += input
        }

        override fun unregister() = Unit
    }

    private companion object {
        val SOURCE = AutofillImportLaunchSource.Onboarding
        val PARAMS = ImportPasswordsParams(launchSource = SOURCE)
    }
}
