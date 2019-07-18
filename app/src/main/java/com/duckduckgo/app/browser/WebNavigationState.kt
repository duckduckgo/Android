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

sealed class WebNavigationStateChange
data class NewPage(val url: String) : WebNavigationStateChange()
data class UrlUpdated(val url: String) : WebNavigationStateChange()
object PageCleared : WebNavigationStateChange()
object Unchanged : WebNavigationStateChange()
object Other : WebNavigationStateChange()

fun WebNavigationState.compare(previous: WebNavigationState?): WebNavigationStateChange {

    if (this == previous) {
        return Unchanged
    }

    if (originalUrl == null && previous?.originalUrl != null) {
        return PageCleared
    }

    val latestUrl = currentUrl ?: return Other

    // A new page load is identified by the original url changing
    if (originalUrl != previous?.originalUrl) {
        return NewPage(latestUrl)
    }

    // The most up-to-date record of the url is the current one, this may change during a page load
    if (currentUrl != previous?.currentUrl) {
        return UrlUpdated(latestUrl)
    }

    return Other
}

data class WebViewNavigationState(private val stack: WebBackForwardList) : WebNavigationState {

    override val originalUrl: String? = stack.originalUrl

    override val currentUrl: String? = stack.currentUrl

    override val stepsToPreviousPage: Int = if (stack.isHttpsUpgrade) 2 else 1

    override val canGoBack: Boolean = stack.currentIndex >= stepsToPreviousPage

    override val canGoForward: Boolean = stack.currentIndex + 1 < stack.size

    override val hasNavigationHistory = stack.size != 0

    override fun equals(other: Any?): Boolean {

        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WebViewNavigationState
        if (stack != other.stack) return false

        return true
    }

    override fun hashCode(): Int {
        return stack.hashCode()
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