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

package com.duckduckgo.app.browser.cookies

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.cookies.db.AllowedDomainsDao
import com.duckduckgo.app.browser.cookies.db.AllowedDomainsRepository
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppThirdPartyCookieManagerTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val cookieManager = CookieManager.getInstance()
    private lateinit var db: AppDatabase
    private lateinit var allowedDomainsDao: AllowedDomainsDao
    private lateinit var allowedDomainsRepository: AllowedDomainsRepository
    private lateinit var testee: AppThirdPartyCookieManager
    private lateinit var webView: WebView

    @UiThreadTest
    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        allowedDomainsDao = db.allowedDomainsDao()
        allowedDomainsRepository = AllowedDomainsRepository(allowedDomainsDao, coroutinesTestRule.testDispatcherProvider)
        webView = TestWebView(InstrumentationRegistry.getInstrumentation().targetContext)

        testee = AppThirdPartyCookieManager(cookieManager, allowedDomainsRepository)
    }

    @UiThreadTest
    @After
    fun after() {
        cookieManager.removeAllCookies { }
    }

    @UiThreadTest
    @Test
    fun whenProcessUriForThirdPartyCookiesIfDomainIsNotGoogleAndIsNotInTheListThenThirdPartyCookiesDisabled() = coroutinesTestRule.runBlocking {
        testee.processUriForThirdPartyCookies(webView, EXAMPLE_URI)

        assertFalse(cookieManager.acceptThirdPartyCookies(webView))
    }

    @UiThreadTest
    @Test
    fun whenProcessUriForThirdPartyCookiesIfDomainIsNotGoogleAndIsInTheListAndHasCookieThenThirdPartyCookiesEnabled() = coroutinesTestRule.runBlocking {
        givenDomainIsInTheThirdPartyCookieList(EXAMPLE_URI.host!!)
        givenUserIdCookieIsSet()

        testee.processUriForThirdPartyCookies(webView, EXAMPLE_URI)

        assertTrue(cookieManager.acceptThirdPartyCookies(webView))
    }

    @UiThreadTest
    @Test
    fun whenProcessUriForThirdPartyCookiesIfDomainIsNotGoogleAndIsInTheListAndDoesNotHaveCookieThenThirdPartyCookiesDisabled() = coroutinesTestRule.runBlocking {
        givenDomainIsInTheThirdPartyCookieList(EXAMPLE_URI.host!!)

        testee.processUriForThirdPartyCookies(webView, EXAMPLE_URI)

        assertFalse(cookieManager.acceptThirdPartyCookies(webView))
    }

    @UiThreadTest
    @Test
    fun whenProcessUriForThirdPartyCookiesIfDomainIsInTheListAndCookieIsSetThenDomainRemovedFromList() = coroutinesTestRule.runBlocking {
        givenUserIdCookieIsSet()
        givenDomainIsInTheThirdPartyCookieList(EXAMPLE_URI.host!!)

        testee.processUriForThirdPartyCookies(webView, EXAMPLE_URI)

        assertNull(allowedDomainsRepository.getDomain(EXAMPLE_URI.host!!))
    }

    @UiThreadTest
    @Test
    fun whenProcessUriForThirdPartyCookiesIfDomainIsInTheListAndCookieIsNotSetThenDomainRemovedFromList() = coroutinesTestRule.runBlocking {
        givenDomainIsInTheThirdPartyCookieList(EXAMPLE_URI.host!!)

        testee.processUriForThirdPartyCookies(webView, EXAMPLE_URI)

        assertNull(allowedDomainsRepository.getDomain(EXAMPLE_URI.host!!))
    }

    @UiThreadTest
    @Test
    fun whenProcessUriForThirdPartyCookiesIfDomainIsInTheListAndIsFromExceptionListThenDomainNotRemovedFromList() = coroutinesTestRule.runBlocking {
        givenDomainIsInTheThirdPartyCookieList(EXCLUDED_DOMAIN_URI.host!!)

        testee.processUriForThirdPartyCookies(webView, EXCLUDED_DOMAIN_URI)

        assertNotNull(allowedDomainsRepository.getDomain(EXCLUDED_DOMAIN_URI.host!!))
    }

    @UiThreadTest
    @Test
    fun whenProcessUriForThirdPartyCookiesIfUrlIsGoogleAuthAndIsTokenTypeThenDomainAddedToTheList() = coroutinesTestRule.runBlocking {
        testee.processUriForThirdPartyCookies(webView, THIRD_PARTY_AUTH_URI)

        assertNotNull(allowedDomainsRepository.getDomain(EXAMPLE_URI.host!!))
    }

    @UiThreadTest
    @Test
    fun whenProcessUriForThirdPartyCookiesIfUrlIsGoogleAuthAndIsNotTokenTypeThenDomainNotAddedToTheList() = coroutinesTestRule.runBlocking {
        testee.processUriForThirdPartyCookies(webView, NON_THIRD_PARTY_AUTH_URI)

        assertNull(allowedDomainsRepository.getDomain(EXAMPLE_URI.host!!))
    }

    @Test
    fun whenClearAllDataThenDomainDeletedFromDatabase() = coroutinesTestRule.runBlocking {
        givenDomainIsInTheThirdPartyCookieList(EXAMPLE_URI.host!!)

        testee.clearAllData()

        assertNull(allowedDomainsRepository.getDomain(EXAMPLE_URI.host!!))
    }

    @Test
    fun whenClearAllDataIfDomainIsInExclusionListThenDomainNotDeletedFromDatabase() = coroutinesTestRule.runBlocking {
        givenDomainIsInTheThirdPartyCookieList(EXCLUDED_DOMAIN_URI.host!!)

        testee.clearAllData()

        assertNotNull(allowedDomainsRepository.getDomain(EXCLUDED_DOMAIN_URI.host!!))
    }

    private suspend fun givenDomainIsInTheThirdPartyCookieList(domain: String) = coroutinesTestRule.runBlocking {
        withContext(coroutinesTestRule.testDispatcherProvider.io()) {
            allowedDomainsRepository.addDomain(domain)
        }
    }

    private suspend fun givenUserIdCookieIsSet() {
        withContext(coroutinesTestRule.testDispatcherProvider.main()) {
            cookieManager.setCookie("https://accounts.google.com", "user_id=test")
        }
    }

    private class TestWebView(context: Context) : WebView(context)

    companion object {
        val EXCLUDED_DOMAIN_URI = "http://home.nest.com".toUri()
        val EXAMPLE_URI = "http://example.com".toUri()
        val THIRD_PARTY_AUTH_URI = "https://accounts.google.com/o/oauth2/auth/identifier?response_type=permission%20id_token&ss_domain=https%3A%2F%2Fexample.com".toUri()
        val NON_THIRD_PARTY_AUTH_URI = "https://accounts.google.com/o/oauth2/auth/identifier?response_type=code&ss_domain=https%3A%2F%2Fexample.com".toUri()
    }
}
