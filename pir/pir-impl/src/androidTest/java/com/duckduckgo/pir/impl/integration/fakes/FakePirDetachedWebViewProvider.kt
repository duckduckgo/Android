/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.integration.fakes

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.pir.impl.common.PirDetachedWebViewProvider
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A fake WebView provider for integration tests that creates real WebViews on the main thread (required by WebView).
 */
class FakePirDetachedWebViewProvider : PirDetachedWebViewProvider {

    @SuppressLint("SetJavaScriptEnabled")
    override fun createInstance(
        context: Context,
        scriptToLoad: String,
        onPageLoaded: (String?) -> Unit,
        onPageLoadFailed: (String?) -> Unit,
    ): WebView {
        val webViewRef = AtomicReference<WebView>()
        val latch = CountDownLatch(1)

        // WebView must be created on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Already on main thread
            webViewRef.set(createWebViewInternal(context, onPageLoaded))
            latch.countDown()
        } else {
            // Need to create on main thread
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                webViewRef.set(createWebViewInternal(context, onPageLoaded))
                latch.countDown()
            }
        }

        latch.await(5, TimeUnit.SECONDS)
        return webViewRef.get()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun setupWebView(
        webView: WebView,
        scriptToLoad: String,
        onPageLoaded: (String?) -> Unit,
        onPageLoadFailed: (String?) -> Unit,
    ): WebView {
        val latch = CountDownLatch(1)

        if (Looper.myLooper() == Looper.getMainLooper()) {
            configureWebView(webView, onPageLoaded)
            latch.countDown()
        } else {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                configureWebView(webView, onPageLoaded)
                latch.countDown()
            }
        }

        latch.await(5, TimeUnit.SECONDS)
        return webView
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebViewInternal(
        context: Context,
        onPageLoaded: (String?) -> Unit,
    ): WebView {
        return WebView(context).apply {
            configureWebView(this, onPageLoaded)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(
        webView: WebView,
        onPageLoaded: (String?) -> Unit,
    ) {
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                // Return empty response for all requests to avoid network calls
                return WebResourceResponse(
                    "text/html",
                    "UTF-8",
                    ByteArrayInputStream("<html></html>".toByteArray()),
                )
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?,
            ) {
                super.onPageFinished(view, url)
                onPageLoaded(url)
            }
        }
    }
}
