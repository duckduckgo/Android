/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.httpauth

import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.lifecycle.LifecycleOwner
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.WebViewDatabaseProvider
import com.duckduckgo.app.fire.AuthDatabaseLocator
import com.duckduckgo.app.fire.DatabaseCleaner
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WebViewHttpAuthStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val webViewDatabaseProvider: WebViewDatabaseProvider = mock()
    private val webViewDatabase: WebViewDatabase = mock()
    private val mockDatabaseCleaner: DatabaseCleaner = mock()
    private val webView: WebView = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val databaseLocator = AuthDatabaseLocator(context)
    private val mockOwner: LifecycleOwner = mock()

    private val webViewHttpAuthStore =
        RealWebViewHttpAuthStore(
            webViewDatabaseProvider,
            mockDatabaseCleaner,
            databaseLocator,
            coroutineRule.testDispatcherProvider,
            TestScope(),
            appBuildConfig,
        )

    @Before
    fun before() {
        whenever(webViewDatabaseProvider.get()).thenReturn(webViewDatabase)
    }

    @Test
    @SdkSuppress(minSdkVersion = android.os.Build.VERSION_CODES.O, maxSdkVersion = android.os.Build.VERSION_CODES.S)
    fun whenSetHttpAuthUsernamePasswordApi26AndAboveThenInsertHttpAuthEntity() {
        for (i in android.os.Build.VERSION_CODES.O..android.os.Build.VERSION_CODES.S) {
            whenever(appBuildConfig.sdkInt).thenReturn(i)
            webViewHttpAuthStore.setHttpAuthUsernamePassword(
                webView = webView,
                host = "host",
                realm = "realm",
                username = "name",
                password = "pass",
            )
        }
        val times = (android.os.Build.VERSION_CODES.O..android.os.Build.VERSION_CODES.S).toList().size
        verify(webViewDatabase, times(times)).setHttpAuthUsernamePassword("host", "realm", "name", "pass")
    }

    @Test
    @SdkSuppress(minSdkVersion = android.os.Build.VERSION_CODES.O, maxSdkVersion = android.os.Build.VERSION_CODES.S)
    fun whenGetHttpAuthUsernamePasswordApi26AndAboveThenReturnWebViewHttpAuthCredentials() {
        for (i in android.os.Build.VERSION_CODES.O..android.os.Build.VERSION_CODES.S) {
            whenever(appBuildConfig.sdkInt).thenReturn(i)
            whenever(webViewDatabase.getHttpAuthUsernamePassword("host", "realm"))
                .thenReturn(arrayOf("name", "pass"))
            val credentials = webViewHttpAuthStore.getHttpAuthUsernamePassword(webView, "host", "realm")
            assertEquals(WebViewHttpAuthCredentials("name", "pass"), credentials)
        }
    }

    @Test
    fun whenCleanHttpAuthDatabaseThenCleanDatabaseCalled() = runTest {
        webViewHttpAuthStore.cleanHttpAuthDatabase()
        verify(mockDatabaseCleaner).cleanDatabase(databaseLocator.getDatabasePath())
    }

    @Test
    @SdkSuppress(minSdkVersion = android.os.Build.VERSION_CODES.O, maxSdkVersion = android.os.Build.VERSION_CODES.O_MR1)
    fun whenAppCreatedAndApiBetween26And27ThenJournalModeChangedToDelete() = runTest {
        for (i in android.os.Build.VERSION_CODES.O..android.os.Build.VERSION_CODES.O_MR1) {
            whenever(appBuildConfig.sdkInt).thenReturn(i)
            webViewHttpAuthStore.onCreate(mockOwner)
        }
        val times = (android.os.Build.VERSION_CODES.O..android.os.Build.VERSION_CODES.O_MR1).toList().size
        verify(mockDatabaseCleaner, times(times)).changeJournalModeToDelete(databaseLocator.getDatabasePath())
    }

    @Test
    @SdkSuppress(minSdkVersion = android.os.Build.VERSION_CODES.R, maxSdkVersion = android.os.Build.VERSION_CODES.S)
    fun whenAppCreatedAndApiGreaterThan28ThenJournalModeChangedToDelete() = runTest {
        for (i in android.os.Build.VERSION_CODES.R..android.os.Build.VERSION_CODES.S) {
            whenever(appBuildConfig.sdkInt).thenReturn(i)
            webViewHttpAuthStore.onCreate(mockOwner)
        }
        val times = (android.os.Build.VERSION_CODES.R..android.os.Build.VERSION_CODES.S).toList().size
        verify(mockDatabaseCleaner, times(times)).changeJournalModeToDelete(databaseLocator.getDatabasePath())
    }

    @Test
    fun whenAppCreatedAndApiIs28ThenJournalModeChangedToDeleteNotCalled() = runTest {
        whenever(appBuildConfig.sdkInt).thenReturn(android.os.Build.VERSION_CODES.P)
        webViewHttpAuthStore.onCreate(mockOwner)
        verify(mockDatabaseCleaner, never()).changeJournalModeToDelete(databaseLocator.getDatabasePath())
    }
}
