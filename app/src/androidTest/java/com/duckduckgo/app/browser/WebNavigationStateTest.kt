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

import com.duckduckgo.app.browser.WebNavigationStateChange.*
import org.junit.Assert.assertEquals
import org.junit.Test

class WebNavigationStateComparisonTest {

    @Test
    fun whenPreviousStateAndLatestStateSameThenCompareReturnsUnchanged() {
        val state = buildState("foo.com", "subdomain.foo.com")
        assertEquals(Unchanged, state.compare(state))
    }

    @Test
    fun whenPreviousStateAndLatestStateEqualThenCompareReturnsUnchanged() {
        val previousState = buildState("foo.com", "subdomain.foo.com")
        val latestState = buildState("foo.com", "subdomain.foo.com")
        assertEquals(Unchanged, latestState.compare(previousState))
    }

    @Test
    fun whenPreviousStateIsNullAndLatestContainsAnOriginalUrlACurrentUrlAndTitleThenCompareReturnsNewPageWithTitle() {
        val previousState = null
        val latestState = buildState("latest.com", "subdomain.latest.com", "Title")
        assertEquals(NewPage("subdomain.latest.com", "Title"), latestState.compare(previousState))
    }

    @Test
    fun whenPreviousStateIsNullAndLatestContainsAnOriginalUrlACurrentUrlAndNoTitleThenCompareReturnsNewPageWithoutTitle() {
        val previousState = null
        val latestState = buildState("latest.com", "subdomain.latest.com")
        assertEquals(NewPage("subdomain.latest.com", null), latestState.compare(previousState))
    }

    @Test
    fun whenPreviousContainsNoOriginalOrCurrentUrlAndLatestContainsAnOriginalAndCurrentUrlThenCompareReturnsNewPage() {
        val previousState = buildState(null, null)
        val latestState = buildState("latest.com", "subdomain.latest.com")
        assertEquals(NewPage("subdomain.latest.com", null), latestState.compare(previousState))
    }

    @Test
    fun whenPreviousContainsNoOriginalUrlAndACurrentUrlAndLatestContainsAnOriginalAndCurrentUrlThenCompareReturnsNewPage() {
        val previousState = buildState(null, "subdomain.previous.com")
        val latestState = buildState("latest.com", "subdomain.latest.com")
        assertEquals(NewPage("subdomain.latest.com", null), latestState.compare(previousState))
    }

    @Test
    fun whenPreviousContainsAnOriginalUrlAndNoCurrentUrlAndLatestContainsAnOriginalAndCurrentUrlThenCompareReturnsNewPage() {
        val previousState = buildState("previous.com", null)
        val latestState = buildState("latest.com", "subdomain.latest.com")
        assertEquals(NewPage("subdomain.latest.com", null), latestState.compare(previousState))
    }

    @Test
    fun whenPreviousContainsAnOriginalUrlAndCurrentUrlAndLatestContainsADifferentOriginalUrlThenCompareReturnsNewPage() {
        val previousState = buildState("previous.com", "subdomain.previous.com")
        val latestState = buildState("latest.com", "subdomain.latest.com")
        assertEquals(NewPage("subdomain.latest.com", null), latestState.compare(previousState))
    }

    @Test
    fun whenPreviousContainsAnOriginalUrlAndCurrentUrlAndLatestContainsSameOriginalUrlAndDifferentCurrentUrlThenCompareReturnsUrlUpdated() {
        val previousState = buildState("same.com", "subdomain.previous.com")
        val latestState = buildState("same.com", "subdomain.latest.com")
        assertEquals(UrlUpdated("subdomain.latest.com"), latestState.compare(previousState))
    }

    @Test
    fun whenPreviousContainsAnOriginalUrlAndCurrentUrlAndLatestContainsSameOriginalUrlAndNoCurrentUrlThenCompareReturnsOther() {
        val previousState = buildState("same.com", "subdomain.previous.com")
        val latestState = buildState("same.com", null)
        assertEquals(Other, latestState.compare(previousState))
    }

    @Test
    fun whenPreviousContainsAnOriginalUrlAndCurrentUrlAndLatestStateContainsNoOriginalUrlAndNoCurrentUrlThenCompareReturnsPageCleared() {
        val previousState = buildState("previous.com", "subdomain.previous.com")
        val latestState = buildState(null, null)
        assertEquals(PageCleared, latestState.compare(previousState))
    }

    @Test
    fun whenPreviousContainsAnOriginalUrlAndCurrentUrlAndLatestStateContainsNoOriginalUrlAndACurrentUrlThenCompareReturnsPageCleared() {
        val previousState = buildState("previous.com", "subdomain.previous.com")
        val latestState = buildState(null, "subdomain.latest.com")
        assertEquals(PageCleared, latestState.compare(previousState))
    }

    @Test
    fun whenPreviousContainsAnOriginalUrlAndCurrentUrlAndLatestStateContainsDifferentOriginalUrlAndNoCurrentUrlThenCompareReturnsOther() {
        val previousState = buildState("previous.com", "subdomain.previous.com")
        val latestState = buildState("latest.com", null)
        assertEquals(Other, latestState.compare(previousState))
    }

    private fun buildState(originalUrl: String?, currentUrl: String?, title: String? = null): WebNavigationState {
        return TestNavigationState(
            originalUrl = originalUrl,
            currentUrl = currentUrl,
            title = title,
            stepsToPreviousPage = 1,
            canGoBack = true,
            canGoForward = true,
            hasNavigationHistory = true
        )
    }
}

data class TestNavigationState(
    override val originalUrl: String?,
    override val currentUrl: String?,
    override val title: String?,
    override val stepsToPreviousPage: Int,
    override val canGoBack: Boolean,
    override val canGoForward: Boolean,
    override val hasNavigationHistory: Boolean
) : WebNavigationState
