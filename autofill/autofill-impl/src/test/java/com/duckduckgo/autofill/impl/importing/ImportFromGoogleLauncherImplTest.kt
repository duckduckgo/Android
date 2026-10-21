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
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.ImportFromGoogleAvailability
import com.duckduckgo.autofill.api.ImportFromGoogleLauncher.BookmarksImportRequest
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportBookmarksViaGoogleTakeoutScreen
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import com.duckduckgo.autofill.api.ImportFromGoogleLauncher.ImportFromGoogleResult as PublicResult
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarkResult as InternalResult

@RunWith(AndroidJUnit4::class)
class ImportFromGoogleLauncherImplTest {

    private val context: Context = mock()

    private val importFromGoogleAvailability: ImportFromGoogleAvailability = mock()

    private val globalActivityStarter: GlobalActivityStarter = mock()

    private val launchIntent = Intent()

    private lateinit var testee: ImportFromGoogleLauncherImpl

    private lateinit var contract: ActivityResultContract<BookmarksImportRequest, PublicResult>

    @Before
    fun setUp() {
        testee = ImportFromGoogleLauncherImpl(
            importFromGoogleAvailability = importFromGoogleAvailability,
            globalActivityStarter = globalActivityStarter,
        )
        contract = testee.bookmarksImportContract()

        whenever(globalActivityStarter.startIntent(any(), any<ImportBookmarksViaGoogleTakeoutScreen>())).thenReturn(launchIntent)
    }

    @Test
    fun `Synchronous result is null when import is available`() = runTest {
        val request = launchedRequest(isAvailable = true)
        assertNull(contract.getSynchronousResult(context, request))
    }

    @Test
    fun `Synchronous result is Unavailable when import is unavailable`() = runTest {
        val request = launchedRequest(isAvailable = false)
        val result = contract.getSynchronousResult(context, request)
        assertEquals(PublicResult.Unavailable, result?.value)
    }

    @Test
    fun `Synchronous result is Unavailable when request was not created by the launcher`() {
        val result = contract.getSynchronousResult(context, object : BookmarksImportRequest {})
        assertEquals(PublicResult.Unavailable, result?.value)
    }

    @Test
    fun `Synchronous result is Unavailable when there is no intent to launch`() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<ImportBookmarksViaGoogleTakeoutScreen>())).thenReturn(null)
        val request = launchedRequest(isAvailable = true)

        val result = contract.getSynchronousResult(context, request)

        assertEquals(PublicResult.Unavailable, result?.value)
    }

    @Test
    fun `launchBookmarksImport launches exactly one request`() = runTest {
        whenever(importFromGoogleAvailability.isBookmarksImportAvailable()).thenReturn(true)
        val launcher = FakeActivityResultLauncher()

        testee.launchBookmarksImport(launcher)

        assertEquals(1, launcher.launchedInputs.size)
    }

    @Test
    fun `createIntent returns the import screen intent`() = runTest {
        val request = launchedRequest(isAvailable = true)
        assertSame(launchIntent, contract.createIntent(context, request))
    }

    @Test
    fun `parseResult returns UserCancelled when intent is null`() {
        assertEquals(PublicResult.UserCancelled, contract.parseResult(Activity.RESULT_CANCELED, null))
    }

    @Test
    fun `parseResult returns UserCancelled when intent has no extras`() {
        assertEquals(PublicResult.UserCancelled, contract.parseResult(Activity.RESULT_OK, Intent()))
    }

    @Test
    fun `parseResult returns Success when internal result is Success`() {
        val intent = createIntentWithResult(InternalResult.Success(42))
        assertEquals(PublicResult.Success(42), contract.parseResult(Activity.RESULT_OK, intent))
    }

    @Test
    fun `parseResult returns UserCancelled when internal result is UserCancelled`() {
        val intent = createIntentWithResult(InternalResult.UserCancelled("test-stage"))
        assertEquals(PublicResult.UserCancelled, contract.parseResult(Activity.RESULT_OK, intent))
    }

    @Test
    fun `parseResult returns Error when internal result is Error`() {
        val intent = createIntentWithResult(InternalResult.Error(UserCannotImportReason.Unknown))
        assertEquals(PublicResult.Error, contract.parseResult(Activity.RESULT_OK, intent))
    }

    @Test
    fun `parseResult returns UserCancelled when parcelable is null`() {
        val intent = Intent().apply {
            putExtras(Bundle())
        }
        assertEquals(PublicResult.UserCancelled, contract.parseResult(Activity.RESULT_OK, intent))
    }

    private suspend fun launchedRequest(isAvailable: Boolean): BookmarksImportRequest {
        whenever(importFromGoogleAvailability.isBookmarksImportAvailable()).thenReturn(isAvailable)
        val launcher = FakeActivityResultLauncher()
        testee.launchBookmarksImport(launcher)
        return launcher.launchedInputs.single()
    }

    private fun createIntentWithResult(internalResult: InternalResult): Intent {
        return Intent().apply {
            putExtras(
                Bundle().apply {
                    putParcelable(InternalResult.RESULT_KEY_DETAILS, internalResult)
                },
            )
        }
    }

    private class FakeActivityResultLauncher : ActivityResultLauncher<BookmarksImportRequest>() {
        val launchedInputs = mutableListOf<BookmarksImportRequest>()

        override fun getContract(): ActivityResultContract<BookmarksImportRequest, *> = throw UnsupportedOperationException()

        override fun launch(input: BookmarksImportRequest, options: ActivityOptionsCompat?) {
            launchedInputs += input
        }

        override fun unregister() = Unit
    }
}
