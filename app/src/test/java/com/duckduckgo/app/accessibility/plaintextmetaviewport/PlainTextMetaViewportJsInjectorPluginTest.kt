/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.accessibility.plaintextmetaviewport

import android.content.Context
import android.content.res.Resources
import android.webkit.WebView
import com.duckduckgo.feature.toggles.api.Toggle
import java.io.ByteArrayInputStream
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class PlainTextMetaViewportJsInjectorPluginTest {

    private val mockWebView: WebView = mock()
    private val plainTextMetaViewportFeature: PlainTextMetaViewportFeature = mock()
    private val toggle: Toggle = mock()

    private lateinit var plainTextMetaViewportJsInjectorPlugin: PlainTextMetaViewportJsInjectorPlugin

    @Before
    fun setUp() {
        plainTextMetaViewportJsInjectorPlugin = PlainTextMetaViewportJsInjectorPlugin(plainTextMetaViewportFeature)
        whenever(plainTextMetaViewportFeature.self()).thenReturn(toggle)
        val context = mock<Context>()
        val resources = mock<Resources>()
        val inputStream = ByteArrayInputStream(JS.toByteArray())
        whenever(context.resources).thenReturn(resources)
        whenever(resources.openRawResource(anyInt())).thenReturn(inputStream)
        whenever(mockWebView.context).thenReturn(context)
    }

    @Test
    fun whenDisabledAndInjectPlainTextMetaViewportThenDoNothing() {
        whenever(toggle.isEnabled()).thenReturn(false)

        plainTextMetaViewportJsInjectorPlugin.onPageStarted(mockWebView, null, null)

        verifyNoInteractions(mockWebView)
    }

    @Test
    fun whenEnabledAndInjectPlainTextMetaViewportThenInjectJs() {
        whenever(toggle.isEnabled()).thenReturn(true)

        plainTextMetaViewportJsInjectorPlugin.onPageStarted(mockWebView, null, null)

        verify(mockWebView).evaluateJavascript("javascript:$JS", null)
    }

    companion object {
        private const val JS = "plain text meta viewport js"
    }
}
