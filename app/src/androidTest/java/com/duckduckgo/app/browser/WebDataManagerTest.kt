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

import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebStorage
import android.webkit.WebView
import com.nhaarman.mockito_kotlin.*
import org.junit.Test
import org.mockito.ArgumentCaptor

class WebDataManagerTest {

    private inline fun <reified T : Any> argumentCaptor() = ArgumentCaptor.forClass(T::class.java)

    private val mockCookieManager: CookieManager = mock()

    private val mockWebView: WebView = mock()

    private val mockStorage: WebStorage = mock()

    private val testee = WebDataManager(host)


    @Test
    fun whenDataClearedThenCacheHistoryAndStorageDataCleared() {
        testee.clearData(mockWebView, mockStorage)
        verify(mockWebView).clearHistory()
        verify(mockWebView).clearCache(true)
        verify(mockStorage).deleteAllData()
    }

    @Test
    fun whenExternalCookiesClearedThenCookiesRemoved() {
        testee.clearExternalCookies(mockCookieManager, {})
        verify(mockCookieManager).removeAllCookies(any())
    }

    @Test
    fun whenExternalCookiesClearedThenThenInternalCookiesRecreated() {

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

    companion object {
        val host = "duckduckgo.com"
        val externalHost = "example.com"
    }

}