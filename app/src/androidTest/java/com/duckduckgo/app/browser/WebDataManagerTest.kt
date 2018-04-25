/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.content.Context
import android.support.test.InstrumentationRegistry
import android.support.test.annotation.UiThreadTest
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebStorage
import android.webkit.WebView
import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor

class WebDataManagerTest {

    private inline fun <reified T : Any> argumentCaptor() = ArgumentCaptor.forClass(T::class.java)

    private val mockCookieManager: CookieManager = mock()

    private val mockStorage: WebStorage = mock()

    private val testee = WebDataManager(host)

    @UiThreadTest
    @Test
    fun whenDataClearedThenCacheHistoryAndStorageDataCleared() {
        val context = InstrumentationRegistry.getTargetContext()
        val webView = TestWebView(context)
        testee.clearData(webView, mockStorage, context)
        assertTrue(webView.historyCleared)
        assertTrue(webView.cacheCleared)
        verify(mockStorage).deleteAllData()
    }

    @Test
    fun whenExternalCookiesClearedThenCookiesRemoved() {
        testee.clearExternalCookies(mockCookieManager, {})
        verify(mockCookieManager).removeAllCookies(any())
    }

    @Test
    fun whenExternalCookiesClearedThenInternalCookiesRecreated() {

        whenever(mockCookieManager.getCookie(host)).thenReturn("da=abc; dz=zyx")
        whenever(mockCookieManager.getCookie(externalHost)).thenReturn("ea=abc; ez=zyx")
        testee.clearExternalCookies(mockCookieManager, {})

        val captor = argumentCaptor<ValueCallback<Boolean>>()
        verify(mockCookieManager).removeAllCookies(captor.capture())
        captor.value.onReceiveValue(true)

        verify(mockCookieManager).setCookie(host, "da=abc")
        verify(mockCookieManager).setCookie(host, "dz=zyx")
    }

    @Test
    fun whenExternalCookiesClearedThenExternalCookiesAreNotRecreated() {
        whenever(mockCookieManager.getCookie(host)).thenReturn("da=abc; dz=zyx")
        whenever(mockCookieManager.getCookie(externalHost)).thenReturn("ea=abc; ez=zyx")
        testee.clearExternalCookies(mockCookieManager, {})

        val captor = argumentCaptor<ValueCallback<Boolean>>()
        verify(mockCookieManager).removeAllCookies(captor.capture())
        captor.value.onReceiveValue(true)

        verify(mockCookieManager, never()).setCookie(host, "ea=abc")
        verify(mockCookieManager, never()).setCookie(host, "ez=zyx")
    }

    private class TestWebView(context: Context) : WebView(context) {

        var historyCleared: Boolean = false
        var cacheCleared: Boolean = false

        override fun clearHistory() {
            super.clearHistory()

            historyCleared = true
        }

        override fun clearCache(includeDiskFiles: Boolean) {
            super.clearCache(includeDiskFiles)

            if(includeDiskFiles) {
                cacheCleared = true
            }
        }
    }


    companion object {
        private const val host = "duckduckgo.com"
        private const val externalHost = "example.com"
    }

}