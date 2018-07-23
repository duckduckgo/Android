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

package com.duckduckgo.app.browser.session

import android.os.Bundle
import android.os.Parcel
import android.util.LruCache
import android.webkit.WebView
import timber.log.Timber


interface WebViewSessionStorage {
    fun saveSession(webView: WebView?, tabId: String)
    fun restoreSession(webView: WebView?, tabId: String): Boolean
    fun deleteSession(tabId: String)
    fun deleteAllSessions()
}

class WebViewSessionInMemoryStorage : WebViewSessionStorage {

    private val map = object: LruCache<String, Bundle>(CACHE_SIZE) {

        /**
         * We can calculate this however we choose, but it should match up with the value we used for cache size.
         * i.e., if we specify max cache size in bytes, we should calculate an approximate size of the cache entry in bytes.
         */
        override fun sizeOf(key: String, bundle: Bundle) = bundle.sizeInBytes()

        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bundle?, newValue: Bundle?) {
            if (evicted) {
                Timber.v("Evicted $key from WebView session storage")
            }
        }
    }

    override fun saveSession(webView: WebView?, tabId: String) {
        if (webView == null) {
            Timber.w("WebView is null; cannot save session")
            return
        }

        Timber.i("Saving WebView session for $tabId")

        val webViewBundle = createWebViewBundle(webView)

        val bundle = Bundle()
        bundle.putBundle(CACHE_KEY_WEBVIEW, webViewBundle)
        bundle.putInt(CACHE_KEY_SCROLL_POSITION, webView.scrollY)
        map.put(tabId, bundle)

        Timber.d("Stored ${bundle.sizeInBytes()} bytes for WebView $webView")
        logCacheSize()
    }

    private fun createWebViewBundle(webView: WebView): Bundle {
        return Bundle().also {
            webView.saveState(it)
        }
    }

    override fun restoreSession(webView: WebView?, tabId: String): Boolean {
        if (webView == null) {
            Timber.w("WebView is null; cannot restore session")
            return false
        }

        Timber.i("Restoring WebView session for $tabId")

        val bundle = map[tabId]
        if (bundle == null) {
            Timber.v("No saved bundle for tab $tabId")
            return false
        }

        val webViewBundle = bundle.getBundle(CACHE_KEY_WEBVIEW)
        webView.restoreState(webViewBundle)
        webView.scrollY = bundle.getInt(CACHE_KEY_SCROLL_POSITION)
        map.remove(tabId)

        logCacheSize()

        return true
    }

    override fun deleteSession(tabId: String) {
        map.remove(tabId)
        Timber.i("Deleted web session for $tabId")
        logCacheSize()
    }

    override fun deleteAllSessions() = map.evictAll()

    private fun logCacheSize() {
        Timber.v("Cache size is now ~${map.size()} bytes out of a max size of ${map.maxSize()} bytes")
    }

    private fun Bundle.sizeInBytes(): Int {
        val parcel = Parcel.obtain()
        parcel.writeValue(this)

        val bytes = parcel.marshall()
        parcel.recycle()

        return bytes.size
    }

    companion object {
        private const val CACHE_SIZE = 10 * 1024 * 1024 // 10 MB

        private const val CACHE_KEY_WEBVIEW = "webview"
        private const val CACHE_KEY_SCROLL_POSITION = "scroll-position"

    }

}