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

package com.duckduckgo.browsermode.impl.profile

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.Profile
import com.duckduckgo.app.fire.FireproofRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealWebViewProfileMigrationManagerTest {

    @get:Rule val coroutineRule = CoroutineTestRule()

    private val fireproofRepository: FireproofRepository = mock()
    private val duckAiHostProvider: DuckAiHostProvider = mock()
    private val oldCookies: CookieManager = mock()
    private val newCookies: CookieManager = mock()
    private val old: Profile = mock()
    private val new: Profile = mock()

    private val testee = RealWebViewProfileMigrationManager(
        fireproofRepository,
        duckAiHostProvider,
        coroutineRule.testDispatcherProvider,
    )

    @Before
    fun setUp() {
        whenever(old.cookieManager).thenReturn(oldCookies)
        whenever(new.cookieManager).thenReturn(newCookies)
        whenever(duckAiHostProvider.getHost()).thenReturn("duck.ai")
        fireproofRepository.stub { onBlocking { fireproofWebsites() }.thenReturn(emptyList()) }
    }

    @Test
    fun `migrates fireproofed cookies prepending https for bare domains`() = runTest {
        fireproofRepository.stub { onBlocking { fireproofWebsites() }.thenReturn(listOf("example.com")) }
        whenever(oldCookies.getCookie("https://example.com")).thenReturn("a=1")

        testee.migrate(old, new)

        verify(newCookies).setCookie("https://example.com", "a=1")
    }

    @Test
    fun `migrates ddg and duck ai cookies using the injected duck ai host`() = runTest {
        whenever(duckAiHostProvider.getHost()).thenReturn("custom.duck.ai")
        whenever(oldCookies.getCookie(AppUrl.Url.COOKIES)).thenReturn("b=2")
        whenever(oldCookies.getCookie(AppUrl.Url.SURVEY_COOKIES)).thenReturn("c=3")
        whenever(oldCookies.getCookie("https://custom.duck.ai")).thenReturn("d=4")

        testee.migrate(old, new)

        verify(newCookies).setCookie(AppUrl.Url.COOKIES, "b=2")
        verify(newCookies).setCookie(AppUrl.Url.SURVEY_COOKIES, "c=3")
        verify(newCookies).setCookie("https://custom.duck.ai", "d=4")
        verify(newCookies, never()).setCookie(eq("https://duck.ai"), any())
    }

    @Test
    fun `splits multi-cookie strings and skips empty entries`() = runTest {
        fireproofRepository.stub { onBlocking { fireproofWebsites() }.thenReturn(listOf("example.com")) }
        whenever(oldCookies.getCookie("https://example.com")).thenReturn("a=1; b=2; ; c=3")

        testee.migrate(old, new)

        verify(newCookies).setCookie("https://example.com", "a=1")
        verify(newCookies).setCookie("https://example.com", "b=2")
        verify(newCookies).setCookie("https://example.com", "c=3")
        verify(newCookies, never()).setCookie("https://example.com", "")
    }

    @Test
    fun `does nothing when source profile has no cookies for any domain`() = runTest {
        fireproofRepository.stub { onBlocking { fireproofWebsites() }.thenReturn(listOf("example.com")) }
        whenever(oldCookies.getCookie(any())).thenReturn(null)

        testee.migrate(old, new)

        verify(newCookies, never()).setCookie(any(), any())
    }
}
