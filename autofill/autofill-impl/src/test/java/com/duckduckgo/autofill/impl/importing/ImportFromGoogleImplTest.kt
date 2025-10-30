/*
 * Copyright (c) 2025 DuckDuckGo
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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportBookmarksViaGoogleTakeoutScreen
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import com.duckduckgo.autofill.api.ImportFromGoogle.ImportFromGoogleResult as PublicResult
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarkResult as InternalResult

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class ImportFromGoogleImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)

    private val globalActivityStarter: GlobalActivityStarter = mock()

    private val webViewCapabilityChecker: ImportGoogleBookmarksWebViewCapabilityChecker = mock()

    private val context: Context = mock()

    private lateinit var testee: ImportFromGoogleImpl

    @Before
    fun setUp() {
        testee = ImportFromGoogleImpl(
            autofillFeature = autofillFeature,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            globalActivityStarter = globalActivityStarter,
            context = context,
            webViewCapabilityChecker = webViewCapabilityChecker,
        )

        whenever(
            globalActivityStarter.startIntent(
                anyOrNull(),
                any<ImportBookmarksViaGoogleTakeoutScreen>(),
            ),
        ).thenReturn(Intent())
    }

    @Test
    fun `Launch intent is null when feature is disabled`() = runTest {
        configureFeatureState(isEnabled = false)
        assertNull(testee.getBookmarksImportLaunchIntent())
    }

    @Test
    fun `Launch intent is not null when feature is enabled and WebView is capable`() = runTest {
        configureFeatureState(isEnabled = true)
        whenever(webViewCapabilityChecker.webViewCapableOfImporting()).thenReturn(true)
        assertNotNull(testee.getBookmarksImportLaunchIntent())
    }

    @Test
    fun `Launch intent is null when feature is enabled but WebView is not capable`() = runTest {
        configureFeatureState(isEnabled = true)
        whenever(webViewCapabilityChecker.webViewCapableOfImporting()).thenReturn(false)
        assertNull(testee.getBookmarksImportLaunchIntent())
    }

    @Test
    fun `parseResult returns UserCancelled when intent is null`() = runTest {
        val result = testee.parseResult(null)
        assertEquals(PublicResult.UserCancelled, result)
    }

    @Test
    fun `parseResult returns UserCancelled when intent has no extras`() = runTest {
        val result = testee.parseResult(Intent())
        assertEquals(PublicResult.UserCancelled, result)
    }

    @Test
    fun `parseResult returns Success when internal result is Success`() = runTest {
        val intent = createIntentWithResult(InternalResult.Success(42))
        val result = testee.parseResult(intent)
        assertEquals(PublicResult.Success(42), result)
    }

    @Test
    fun `parseResult returns UserCancelled when internal result is UserCancelled`() = runTest {
        val intent = createIntentWithResult(InternalResult.UserCancelled("test-stage"))
        val result = testee.parseResult(intent)
        assertEquals(PublicResult.UserCancelled, result)
    }

    @Test
    fun `parseResult returns Error when internal result is Error`() = runTest {
        val intent = createIntentWithResult(InternalResult.Error(UserCannotImportReason.Unknown))
        val result = testee.parseResult(intent)
        assertEquals(PublicResult.Error, result)
    }

    @Test
    fun `parseResult returns UserCancelled when parcelable is null`() = runTest {
        val intent = Intent().apply {
            putExtras(Bundle())
        }
        val result = testee.parseResult(intent)
        assertEquals(PublicResult.UserCancelled, result)
    }

    private fun configureFeatureState(isEnabled: Boolean) {
        autofillFeature.canImportBookmarksFromGoogleTakeout().setRawStoredState(State(isEnabled))
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
}
