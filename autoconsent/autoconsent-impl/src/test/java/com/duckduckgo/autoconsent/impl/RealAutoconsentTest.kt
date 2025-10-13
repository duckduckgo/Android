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

package com.duckduckgo.autoconsent.impl

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.autoconsent.impl.cache.RealAutoconsentSettingsCache
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentExceptionsRepository
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class RealAutoconsentTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val pluginPoint = FakePluginPoint()
    private val settingsRepository = FakeSettingsRepository()
    private val settingsCache = RealAutoconsentSettingsCache()
    private val unprotected = FakeUnprotected(listOf("unprotected.com"))
    private val userAllowlist = FakeUserAllowlist(listOf("userallowed.com"))
    private val mockAutoconsentExceptionsRepository: AutoconsentExceptionsRepository = mock()
    private val mockAutoconsentFeature: AutoconsentFeature = mock()
    private val mockToggle: Toggle = mock()
    private val webView: WebView = WebView(InstrumentationRegistry.getInstrumentation().targetContext)

    lateinit var autoconsent: RealAutoconsent

    @Before
    fun setup() {
        whenever(mockAutoconsentFeature.self()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(mockAutoconsentExceptionsRepository.exceptions)
            .thenReturn(CopyOnWriteArrayList<FeatureException>().apply { add(FeatureException("exception.com", "reason")) })
        autoconsent = RealAutoconsent(
            pluginPoint,
            settingsRepository,
            mockAutoconsentExceptionsRepository,
            mockAutoconsentFeature,
            userAllowlist,
            unprotected,
            settingsCache,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            isMainProcess = true,
        )
    }

    @Test
    fun whenInjectAutoconsentIfNeverHandledThenDoNotCallEvaluate() {
        settingsRepository.userSetting = false
        settingsRepository.firstPopupHandled = false

        autoconsent.injectAutoconsent(webView, URL)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)

        settingsRepository.userSetting = true
        autoconsent.injectAutoconsent(webView, URL)

        assertNotNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenInjectAutoconsentIfPreviouslyHandledAndSettingFalseThenDoNotCallEvaluate() {
        settingsRepository.userSetting = false
        settingsRepository.firstPopupHandled = true

        autoconsent.injectAutoconsent(webView, URL)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenInjectAutoconsentIfPreviouslyHandledAndSettingTrueThenCallEvaluate() {
        settingsRepository.userSetting = true
        settingsRepository.firstPopupHandled = true

        autoconsent.injectAutoconsent(webView, URL)

        assertNotNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenChangeSettingChangedThenRepoSetValueChanged() {
        autoconsent.changeSetting(false)
        assertFalse(settingsRepository.userSetting)

        autoconsent.changeSetting(true)
        assertTrue(settingsRepository.userSetting)
    }

    @Test
    fun whenSettingEnabledCalledThenReturnValueFromRepo() {
        settingsRepository.userSetting = false
        assertFalse(autoconsent.isSettingEnabled())

        settingsRepository.userSetting = true
        assertTrue(autoconsent.isSettingEnabled())
    }

    @Test
    fun whenSetAutoconsentOptOutThenEvaluateJavascriptCalled() {
        val expected = """javascript:(function() {window.autoconsentMessageCallback({ "type": "optOut" }, window.origin);})();"""

        autoconsent.setAutoconsentOptOut(webView)
        assertEquals(expected, shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenSetAutoconsentOptOutThenTrueValueStored() {
        autoconsent.setAutoconsentOptOut(webView)
        assertTrue(settingsRepository.userSetting)
    }

    @Test
    fun whenSetAutoconsentOptInThenFalseValueStored() {
        autoconsent.setAutoconsentOptIn()
        assertFalse(settingsRepository.userSetting)
    }

    @Test
    fun whenFirstPopUpHandledThenFalseValueStored() {
        autoconsent.firstPopUpHandled()
        assertTrue(settingsRepository.firstPopupHandled)
    }

    @Test
    fun whenInjectAutoconsentIfUrlIsExceptionThenDoNothing() {
        givenSettingsRepositoryAllowsInjection()

        autoconsent.injectAutoconsent(webView, EXCEPTION_URL)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenInjectAutoconsentIfUrlContainsDomainThatIsExceptionThenDoNothing() {
        givenSettingsRepositoryAllowsInjection()

        autoconsent.injectAutoconsent(webView, EXCEPTION_SUBDOMAIN_URL)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenInjectAutoconsentIfUrlIsInUserAllowListThenDoNothing() {
        givenSettingsRepositoryAllowsInjection()

        autoconsent.injectAutoconsent(webView, USER_ALLOWED_URL)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenInjectAutoconsentIfUrlIsInUnprotectedListThenDoNothing() {
        givenSettingsRepositoryAllowsInjection()

        autoconsent.injectAutoconsent(webView, UNPROTECTED_URL)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenInjectAutoconsentIfFeatureIsDisabledThenDoNothing() {
        givenSettingsRepositoryAllowsInjection()
        whenever(mockToggle.isEnabled()).thenReturn(false)

        autoconsent.injectAutoconsent(webView, URL)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    private fun givenSettingsRepositoryAllowsInjection() {
        settingsRepository.userSetting = true
        settingsRepository.firstPopupHandled = true
    }

    companion object {
        private const val URL = "http://example.com"
        private const val UNPROTECTED_URL = "http://unprotected.com"
        private const val USER_ALLOWED_URL = "http://userallowed.com"
        private const val EXCEPTION_URL = "http://exception.com"
        private const val EXCEPTION_SUBDOMAIN_URL = "http://test.exception.com"
    }
}
