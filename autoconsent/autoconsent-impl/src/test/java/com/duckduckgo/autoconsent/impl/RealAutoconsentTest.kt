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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class RealAutoconsentTest {

    private val pluginPoint = FakePluginPoint()
    private val repository = FakeRepository()
    private val webView: WebView = WebView(InstrumentationRegistry.getInstrumentation().targetContext)

    lateinit var autoconsent: RealAutoconsent

    @Before
    fun setup() {
        autoconsent = RealAutoconsent(pluginPoint, repository)
    }

    @Test
    fun whenInjectAutoconsentIfNeverHandledThenCallEvaluate() {
        repository.userSetting = false
        repository.firstPopupHandled = false

        autoconsent.injectAutoconsent(webView)

        assertNotNull(shadowOf(webView).lastEvaluatedJavascript)

        repository.userSetting = true
        autoconsent.injectAutoconsent(webView)

        assertNotNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenInjectAutoconsentIfPreviouslyHandledAndSettingFalseThenDoNotCallEvaluate() {
        repository.userSetting = false
        repository.firstPopupHandled = true

        autoconsent.injectAutoconsent(webView)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenInjectAutoconsentIfPreviouslyHandledAndSettingTrueThenCallEvaluate() {
        repository.userSetting = true
        repository.firstPopupHandled = true

        autoconsent.injectAutoconsent(webView)

        assertNotNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenChangeSettingChangedThenRepoSetValueChanged() {
        autoconsent.changeSetting(false)
        assertFalse(repository.userSetting)

        autoconsent.changeSetting(true)
        assertTrue(repository.userSetting)
    }

    @Test
    fun whenSettingEnabledCalledThenReturnValueFromRepo() {
        repository.userSetting = false
        assertFalse(autoconsent.isSettingEnabled())

        repository.userSetting = true
        assertTrue(autoconsent.isSettingEnabled())
    }

    @Test
    fun whenSetAutoconsentOptOutThenEvaluateJavascriptCalled() {
        val expected = """
        javascript:(function() {
            window.autoconsentMessageCallback({ "type": "optOut" }, window.origin);
        })();
        """.trimIndent()

        autoconsent.setAutoconsentOptOut(webView)
        assertEquals(expected, shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenSetAutoconsentOptOutThenTrueValueStored() {
        autoconsent.setAutoconsentOptOut(webView)
        assertTrue(repository.userSetting)
    }

    @Test
    fun whenSetAutoconsentOptInThenFalseValueStored() {
        autoconsent.setAutoconsentOptIn()
        assertFalse(repository.userSetting)
    }

    @Test
    fun whenFirstPopUpHandledThenFalseValueStored() {
        autoconsent.firstPopUpHandled()
        assertTrue(repository.firstPopupHandled)
    }
}
