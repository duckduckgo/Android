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

package com.duckduckgo.app.browser.webview

import com.duckduckgo.app.browser.DuckDuckGoWebView
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class WebViewInvocationHandler(val webView: DuckDuckGoWebView) : InvocationHandler {

    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
        if (webView.isDestroyed) {
            return null
        }
        return method.invoke(webView, *(args ?: emptyArray()))
    }
}

inline fun <reified T> createSafeWebViewProxy(webView: DuckDuckGoWebView): T {
    return Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
        WebViewInvocationHandler(webView),
    ) as T
}
