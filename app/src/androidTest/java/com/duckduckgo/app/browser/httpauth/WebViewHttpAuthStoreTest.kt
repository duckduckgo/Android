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
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.fire.AuthDatabaseLocator
import com.duckduckgo.app.fire.DatabaseCleaner
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class WebViewHttpAuthStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val webViewDatabase: WebViewDatabase = mock()
    private val mockDatabaseCleaner: DatabaseCleaner = mock()
    private val webView: WebView = mock()
    private val databaseLocator = AuthDatabaseLocator(context)

    private val webViewHttpAuthStore = RealWebViewHttpAuthStore(webViewDatabase, mockDatabaseCleaner, databaseLocator, coroutineRule.testDispatcherProvider, TestCoroutineScope())

    @Test
    @SdkSuppress(minSdkVersion = android.os.Build.VERSION_CODES.O)
    fun whenSetHttpAuthUsernamePasswordApi26AndAboveThenInsertHttpAuthEntity() {
        webViewHttpAuthStore.setHttpAuthUsernamePassword(
            webView = webView,
            host = "host",
            realm = "realm",
            username = "name",
            password = "pass",
        )

        verify(webViewDatabase).setHttpAuthUsernamePassword("host", "realm", "name", "pass")
    }

    @Test
    @SdkSuppress(minSdkVersion = android.os.Build.VERSION_CODES.O)
    fun whenGetHttpAuthUsernamePasswordApi26AndAboveThenReturnWebViewHttpAuthCredentials() {
        whenever(webViewDatabase.getHttpAuthUsernamePassword("host", "realm"))
            .thenReturn(arrayOf("name", "pass"))
        val credentials = webViewHttpAuthStore.getHttpAuthUsernamePassword(webView, "host", "realm")

        assertEquals(WebViewHttpAuthCredentials("name", "pass"), credentials)
    }

    @Test
    @SdkSuppress(maxSdkVersion = android.os.Build.VERSION_CODES.N_MR1)
    @Suppress("DEPRECATION")
    fun whenSetHttpAuthUsernamePasswordApiBelow26ThenInsertHttpAuthEntity() {
        webViewHttpAuthStore.setHttpAuthUsernamePassword(
            webView = webView,
            host = "host",
            realm = "realm",
            username = "name",
            password = "pass",
        )

        verify(webView).setHttpAuthUsernamePassword("host", "realm", "name", "pass")
    }

    @Test
    @SdkSuppress(maxSdkVersion = android.os.Build.VERSION_CODES.N_MR1)
    @Suppress("DEPRECATION")
    fun whenGetHttpAuthUsernamePasswordApiBelow26ThenReturnWebViewHttpAuthCredentials() {
        whenever(webView.getHttpAuthUsernamePassword("host", "realm"))
            .thenReturn(arrayOf("name", "pass"))
        val credentials = webViewHttpAuthStore.getHttpAuthUsernamePassword(webView, "host", "realm")

        assertEquals(WebViewHttpAuthCredentials("name", "pass"), credentials)
    }

    @Test
    fun whenCleanHttpAuthDatabaseThenCleanDatabaseCalled() = coroutineRule.runBlocking {
        webViewHttpAuthStore.cleanHttpAuthDatabase()
        verify(mockDatabaseCleaner).cleanDatabase(databaseLocator.getDatabasePath())
    }

    @Test
    @SdkSuppress(minSdkVersion = android.os.Build.VERSION_CODES.LOLLIPOP, maxSdkVersion = android.os.Build.VERSION_CODES.O_MR1)
    fun whenAppCreatedAndApiBetween21And27ThenJournalModeChangedToDelete() = coroutineRule.runBlocking {
        webViewHttpAuthStore.onAppCreated()
        verify(mockDatabaseCleaner).changeJournalModeToDelete(databaseLocator.getDatabasePath())
    }

    @Test
    @SdkSuppress(minSdkVersion = android.os.Build.VERSION_CODES.Q)
    fun whenAppCreatedAndApiGreaterThan28ThenJournalModeChangedToDelete() = coroutineRule.runBlocking {
        webViewHttpAuthStore.onAppCreated()
        verify(mockDatabaseCleaner).changeJournalModeToDelete(databaseLocator.getDatabasePath())
    }

    @Test
    @SdkSuppress(minSdkVersion = android.os.Build.VERSION_CODES.P, maxSdkVersion = android.os.Build.VERSION_CODES.P)
    fun whenAppCreatedAndApiIs28ThenJournalModeChangedToDeleteNotCalled() = coroutineRule.runBlocking {
        webViewHttpAuthStore.onAppCreated()
        verify(mockDatabaseCleaner, never()).changeJournalModeToDelete(databaseLocator.getDatabasePath())
    }
}
