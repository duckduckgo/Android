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

import android.os.Build
import android.webkit.WebView
import androidx.test.filters.SdkSuppress
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.httpauth.db.HttpAuthDao
import com.duckduckgo.app.browser.httpauth.db.HttpAuthEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class WebViewHttpAuthStoreTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val fireproofWebsiteDao: FireproofWebsiteDao = mock()
    private val httpAuthDao: HttpAuthDao = mock()
    private val webView: WebView = mock()

    private val webViewHttpAuthStore = RealWebViewHttpAuthStore(coroutineRule.testDispatcherProvider, fireproofWebsiteDao, httpAuthDao)
    private val webViewHttpAuthStoreWithNullHttpAuthDao = RealWebViewHttpAuthStore(coroutineRule.testDispatcherProvider, fireproofWebsiteDao, null)

    @Test
    fun whenSetHttpAuthUsernamePasswordThenInsertHttpAuthEntity() {
        webViewHttpAuthStore.setHttpAuthUsernamePassword(
            webView = webView,
            host = "host",
            realm = "realm",
            username = "name",
            password = "pass",
        )

        verify(httpAuthDao).insert(
            HttpAuthEntity(
                host = "host",
                realm = "realm",
                username = "name",
                password = "pass"
            )
        )
    }

    @Test
    @Suppress("DEPRECATION")
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.N_MR1)
    fun whenSetHttpAuthUsernamePasswordAndNullHttpAuthDaoThenCallWebViewSetHttpAuth() {
        webViewHttpAuthStoreWithNullHttpAuthDao.setHttpAuthUsernamePassword(
            webView = webView,
            host = "host",
            realm = "realm",
            username = "name",
            password = "pass",
        )

        verify(webView).setHttpAuthUsernamePassword("host", "realm", "name", "pass")
    }

    @Test
    fun whenGetHttpAuthUsernamePasswordThenReturnWebViewHttpAuthCredentials() {
        whenever(httpAuthDao.getAuthCredentials("host", "realm"))
            .thenReturn(HttpAuthEntity(1, "host", "realm", "name", "pass"))
        val credentials = webViewHttpAuthStore.getHttpAuthUsernamePassword(webView, "host", "realm")

        assertEquals(WebViewHttpAuthCredentials("name", "pass"), credentials)
    }

    @Test
    @Suppress("DEPRECATION")
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.N_MR1)
    fun whenGetHttpAuthUsernamePasswordAndNullHttpAuthDaoReturnWebViewHttpAuthCredentials() {
        whenever(webView.getHttpAuthUsernamePassword("host", "realm"))
            .thenReturn(arrayOf("name", "pass"))
        val credentials = webViewHttpAuthStoreWithNullHttpAuthDao.getHttpAuthUsernamePassword(webView, "host", "realm")

        assertEquals(WebViewHttpAuthCredentials("name", "pass"), credentials)
    }

    @Test
    fun whenClearHttpAuthUsernamePasswordThenDeleteAllCredentialsExceptExclusions() {
        whenever(fireproofWebsiteDao.fireproofWebsitesSync())
            .thenReturn(listOf(FireproofWebsiteEntity("http://fireproofed.me")))

        webViewHttpAuthStore.clearHttpAuthUsernamePassword(webView)

        verify(httpAuthDao).deleteAll(listOf("http://fireproofed.me"))
    }

    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.N_MR1)
    fun whenClearHttpAuthUsernamePasswordAndNullHttpAuthDaoThenClearWebViewAuthCredentials() {
        webViewHttpAuthStoreWithNullHttpAuthDao.clearHttpAuthUsernamePassword(webView)

        verify(webView).clearAuthentication()
    }
}
