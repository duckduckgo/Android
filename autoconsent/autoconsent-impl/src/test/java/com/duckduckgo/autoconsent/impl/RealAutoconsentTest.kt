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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class RealAutoconsentTest {

    private val pluginPoint = FakePluginPoint()
    private val repository = FakeRepository()
    private val mockWebView: WebView = mock()

    lateinit var autoconsent: RealAutoconsent

    @Before
    fun setup() {
        autoconsent = RealAutoconsent(pluginPoint, repository)
    }

    @Test
    fun whenInjectAutoconsentIfNeverHandledThenCallEvaluate() {
        repository.userSetting = false
        repository.firstPopupHandled = false

        autoconsent.injectAutoconsent(mockWebView)

        repository.userSetting = true
        autoconsent.injectAutoconsent(mockWebView)

        verify(mockWebView, times(2)).evaluateJavascript(any(), anyOrNull())
    }

    @Test
    fun whenInjectAutoconsentIfPreviouslyHandledAndSettingFalseThenDoNotCallEvaluate() {
        repository.userSetting = false
        repository.firstPopupHandled = true

        autoconsent.injectAutoconsent(mockWebView)

        verify(mockWebView, never()).evaluateJavascript(any(), anyOrNull())
    }

    @Test
    fun whenInjectAutoconsentIfPreviouslyHandledAndSettingTrueThenCallEvaluate() {
        repository.userSetting = true
        repository.firstPopupHandled = true

        autoconsent.injectAutoconsent(mockWebView)

        verify(mockWebView).evaluateJavascript(any(), anyOrNull())
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

}
