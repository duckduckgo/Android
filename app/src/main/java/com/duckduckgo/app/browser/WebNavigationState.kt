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
import com.duckduckgo.app.browser.WebNavigationStateChange.*
import com.duckduckgo.app.global.isHttpsVersionOfUri

interface WebNavigationState {
    val originalUrl: String?
    val currentUrl: String?
    val title: String?
    val stepsToPreviousPage: Int
    val canGoBack: Boolean
    val canGoForward: Boolean
    val hasNavigationHistory: Boolean
    val progress: Int?
}

sealed class WebNavigationStateChange {
    data class NewPage(val url: String, val title: String?) : WebNavigationStateChange()
    data class UrlUpdated(val url: String) : WebNavigationStateChange()
    object PageCleared : WebNavigationStateChange()
    object Unchanged : WebNavigationStateChange()
    object PageNavigationCleared : WebNavigationStateChange()
    object Other : WebNavigationStateChange()
}

fun WebNavigationState.compare(previous: WebNavigationState?): WebNavigationStateChange {
    if (this == previous) {
        return Unchanged
    }

    if (this is EmptyNavigationState)
        return PageNavigationCleared

    if (originalUrl == null && previous?.originalUrl != null) {
        return PageCleared
    }

    val latestUrl = currentUrl ?: return Other

    // A new page load is identified by the original url changing
    if (originalUrl != previous?.originalUrl) {
        return NewPage(latestUrl, title)
    }

    // The most up-to-date record of the url is the current one, this may change many times during a page load
    // If the host changes too, we class it as a new page load
    if (currentUrl != previous?.currentUrl) {

        if (currentUrl?.toUri()?.host != previous?.currentUrl?.toUri()?.host) {
            return NewPage(latestUrl, title)
        }

        return UrlUpdated(latestUrl)
    }

    return Other
}

data class WebViewNavigationState(private val stack: WebBackForwardList, override val progress: Int? = null) : WebNavigationState {

    override val originalUrl: String? = stack.originalUrl

    override val currentUrl: String? = stack.currentUrl

    override val title: String? = stack.currentItem?.title

    override val stepsToPreviousPage: Int = if (stack.isHttpsUpgrade) 2 else 1

    override val canGoBack: Boolean = stack.currentIndex >= stepsToPreviousPage

    override val canGoForward: Boolean = stack.currentIndex + 1 < stack.size

    override val hasNavigationHistory = stack.size != 0

    /**
     * Auto generated equality method. We create this manually to omit the privately stored system stack property as
     * we are only interested in our properties and the stacks are never equal unless the same instances are compared.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WebViewNavigationState
        if (originalUrl != other.originalUrl) return false
        if (currentUrl != other.currentUrl) return false
        if (title != other.title) return false
        if (stepsToPreviousPage != other.stepsToPreviousPage) return false
        if (canGoBack != other.canGoBack) return false
        if (canGoForward != other.canGoForward) return false
        if (hasNavigationHistory != other.hasNavigationHistory) return false
        if (progress != other.progress) return false

        return true
    }

    /**
     * Auto generated hash method to support equality method
     */
    override fun hashCode(): Int {
        var result = originalUrl?.hashCode() ?: 0
        result = 31 * result + (currentUrl?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + stepsToPreviousPage
        result = 31 * result + canGoBack.hashCode()
        result = 31 * result + canGoForward.hashCode()
        result = 31 * result + hasNavigationHistory.hashCode()
        result = 31 * result + (progress?.hashCode() ?: 0)
        return result
    }
}

@Suppress("DataClassPrivateConstructor")
data class EmptyNavigationState private constructor(
    override val originalUrl: String?,
    override val currentUrl: String?,
    override val title: String?
) : WebNavigationState {
    companion object {
        operator fun invoke(webNavigationState: WebNavigationState): EmptyNavigationState {
            return EmptyNavigationState(
                webNavigationState.originalUrl,
                webNavigationState.currentUrl,
                webNavigationState.title
            )
        }
    }

    override val stepsToPreviousPage: Int = 0
    override val canGoBack: Boolean = false
    override val canGoForward: Boolean = false
    override val hasNavigationHistory: Boolean = false
    override val progress: Int? = null
}

private val WebBackForwardList.originalUrl: String?
    get() = currentItem?.originalUrl

private val WebBackForwardList.currentUrl: String?
    get() = currentItem?.url

private val WebBackForwardList.isHttpsUpgrade: Boolean
    get() {
        if (currentIndex < 1) return false
        val current = originalUrl?.toUri() ?: return false
        val previous = getItemAtIndex(currentIndex - 1)?.originalUrl?.toUri() ?: return false
        return current.isHttpsVersionOfUri(previous)
    }
