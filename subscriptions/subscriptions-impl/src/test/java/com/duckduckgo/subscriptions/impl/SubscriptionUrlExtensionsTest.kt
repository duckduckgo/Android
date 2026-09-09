/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ORIGIN_APP_SETTINGS
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionUrlExtensionsTest {

    @Test
    fun whenOriginIsAllowlistedThenItIsAppended() {
        assertEquals(
            "$BUY_URL?origin=$ORIGIN_APP_SETTINGS",
            BUY_URL.appendFunnelOriginParam(ORIGIN_APP_SETTINGS),
        )
    }

    @Test
    fun whenOriginIsNotAllowlistedThenUrlIsUnchanged() {
        assertEquals(BUY_URL, BUY_URL.appendFunnelOriginParam("funnel_unique_user_identifier"))
    }

    @Test
    fun whenOriginIsNullThenUrlIsUnchanged() {
        assertEquals(BUY_URL, BUY_URL.appendFunnelOriginParam(null))
    }

    @Test
    fun whenOriginIsBlankThenUrlIsUnchanged() {
        assertEquals(BUY_URL, BUY_URL.appendFunnelOriginParam(" "))
    }

    @Test
    fun whenUrlAlreadyHasOriginThenItIsNotAppendedAgain() {
        val url = "$BUY_URL?origin=funnel_onboarding_android"

        assertEquals(url, url.appendFunnelOriginParam(ORIGIN_APP_SETTINGS))
    }

    @Test
    fun whenUrlAlreadyHasOtherQueryParamsThenOriginIsAddedAlongsideThem() {
        val result = "$BUY_URL/plans?tier=pro".appendFunnelOriginParam(ORIGIN_APP_SETTINGS).toUri()

        assertEquals("pro", result.getQueryParameter("tier"))
        assertEquals(ORIGIN_APP_SETTINGS, result.getQueryParameter("origin"))
    }

    @Test
    fun whenUrlHasFeaturePageThenBothParamsArePresent() {
        val result = "$BUY_URL?featurePage=duckai".appendFunnelOriginParam("funnel_duckai_android__modelpicker").toUri()

        assertEquals("duckai", result.getQueryParameter("featurePage"))
        assertEquals("funnel_duckai_android__modelpicker", result.getQueryParameter("origin"))
    }

    @Test
    fun whenUrlIsNotHierarchicalThenUrlIsUnchanged() {
        val url = "mailto:someone@example.com"

        assertEquals(url, url.appendFunnelOriginParam(ORIGIN_APP_SETTINGS))
    }

    companion object {
        private const val BUY_URL = "https://duckduckgo.com/pro"
    }
}
