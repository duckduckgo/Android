/*
 * Copyright (c) 2019 DuckDuckGo
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

import android.webkit.WebBackForwardList
import androidx.core.net.toUri
import com.duckduckgo.app.global.isHttpsVersionOfUri


interface WebNavigationState {
    val originalUrl: String?
    val currentUrl: String?
    val stepsToPreviousPage: Int
    val canGoBack: Boolean
    val canGoForward: Boolean
    val hasNavigationHistory: Boolean
}


data class WebViewNavigationState(private val stack: WebBackForwardList) : WebNavigationState {

    override val originalUrl: String? = stack.originalUrl

    override val currentUrl: String? = stack.currentUrl

    override val stepsToPreviousPage: Int = if (stack.isHttpsUpgrade) 2 else 1

    override val canGoBack: Boolean = stack.currentIndex >= stepsToPreviousPage

    override val canGoForward: Boolean = stack.currentIndex + 1 < stack.size

    override val hasNavigationHistory = stack.size != 0
}

class WebNavigationStateChange(val previous: WebNavigationState?, val new: WebNavigationState) {

    fun newPage(): String? {
        // A new page load is identified by the original url changing
        // The most up-to-date record of the url is the current one as a url may change during loading
        if (previous?.originalUrl != new.originalUrl) {
            return new.currentUrl
        }
        return null
    }

    fun updatedPage(): String? {
        // If this is not a new page AND the currentUrl is different then the url has just been updated
        if (newPage() == null && previous?.currentUrl != new.currentUrl) {
            return new.currentUrl
        }
        return null
    }

    fun isClear(): Boolean {
        return previous?.originalUrl != null && new.originalUrl == null
    }
}

private val WebBackForwardList.originalUrl: String?
    get() = currentItem?.originalUrl

private val WebBackForwardList.currentUrl: String?
    get() = currentItem?.url

private val WebBackForwardList.isHttpsUpgrade: Boolean
    get() {
        if (currentIndex < 1) return false
        val current = originalUrl?.toUri() ?: return false
        val previous = getItemAtIndex(currentIndex - 1).originalUrl?.toUri() ?: return false
        return current.isHttpsVersionOfUri(previous)
    }