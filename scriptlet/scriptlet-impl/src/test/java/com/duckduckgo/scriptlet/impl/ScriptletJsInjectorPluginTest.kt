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

package com.duckduckgo.scriptlet.impl

import android.webkit.WebView
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ScriptletJsInjectorPluginTest {

    private val mockWebView: WebView = mock()
    private val mockFeature: ScriptletFeature = mock()
    private val mockToggle: Toggle = mock()

    private lateinit var plugin: ScriptletJsInjectorPlugin

    @Before
    fun setup() {
        whenever(mockFeature.self()).thenReturn(mockToggle)
        plugin = ScriptletJsInjectorPlugin(mockFeature)
    }

    @Test
    fun whenFeatureDisabledThenNoScriptsInjected() {
        whenever(mockToggle.isEnabled()).thenReturn(false)

        plugin.onPageStarted(mockWebView, "https://www.youtube.com/watch?v=123", false)

        verify(mockWebView, never()).evaluateJavascript(any(), eq(null))
    }

    @Test
    fun whenUrlIsNullThenNoScriptsInjected() {
        whenever(mockToggle.isEnabled()).thenReturn(true)

        plugin.onPageStarted(mockWebView, null, false)

        verify(mockWebView, never()).evaluateJavascript(any(), eq(null))
    }

    @Test
    fun whenUrlIsNotYouTubeThenNoScriptsInjected() {
        whenever(mockToggle.isEnabled()).thenReturn(true)

        plugin.onPageStarted(mockWebView, "https://www.example.com", false)

        verify(mockWebView, never()).evaluateJavascript(any(), eq(null))
    }

    @Test
    fun whenFeatureEnabledAndYouTubeUrlThenBothScriptsInjected() {
        whenever(mockToggle.isEnabled()).thenReturn(true)

        plugin.onPageStarted(mockWebView, "https://www.youtube.com/watch?v=123", false)

        verify(mockWebView, times(2)).evaluateJavascript(any(), eq(null))
    }

    @Test
    fun whenFeatureEnabledAndYouTubeSubdomainThenBothScriptsInjected() {
        whenever(mockToggle.isEnabled()).thenReturn(true)

        plugin.onPageStarted(mockWebView, "https://m.youtube.com/watch?v=123", false)

        verify(mockWebView, times(2)).evaluateJavascript(any(), eq(null))
    }

    @Test
    fun whenFeatureEnabledAndYoutuBeThenBothScriptsInjected() {
        whenever(mockToggle.isEnabled()).thenReturn(true)

        plugin.onPageStarted(mockWebView, "https://youtu.be/123", false)

        verify(mockWebView, times(2)).evaluateJavascript(any(), eq(null))
    }

    @Test
    fun whenFeatureEnabledAndNonYouTubeSiteThenNoScriptsInjected() {
        whenever(mockToggle.isEnabled()).thenReturn(true)

        plugin.onPageStarted(mockWebView, "https://www.notyoutube.com", false)

        verify(mockWebView, never()).evaluateJavascript(any(), eq(null))
    }

    @Test
    fun whenFeatureEnabledAndMaliciousDomainThenNoScriptsInjected() {
        whenever(mockToggle.isEnabled()).thenReturn(true)

        plugin.onPageStarted(mockWebView, "https://www.youtube.com.evil.com", false)

        verify(mockWebView, never()).evaluateJavascript(any(), eq(null))
    }
}
