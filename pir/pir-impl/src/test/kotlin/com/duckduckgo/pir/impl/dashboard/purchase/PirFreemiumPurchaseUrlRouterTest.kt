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

package com.duckduckgo.pir.impl.dashboard.purchase

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.subscriptions.api.Subscriptions
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PirFreemiumPurchaseUrlRouterTest {

    private val subscriptions: Subscriptions = mock()

    private val testee = RealPirFreemiumPurchaseUrlRouter(subscriptions)

    @Test
    fun `test routes all four purchase paths to native purchase flow`() {
        listOf(
            "https://duckduckgo.com/subscriptions",
            "https://duckduckgo.com/subscriptions/plans",
            "https://duckduckgo.com/pro",
            "https://duckduckgo.com/pro/plans",
        ).forEach { url ->
            assertEquals(
                "expected $url to route to the native purchase flow",
                PirPurchaseRoute.NativePurchaseFlow(origin = "funnel_freescan_android", featurePage = "pir"),
                testee.route(url.toUri()),
            )
        }
    }

    @Test
    fun `test routes purchase path with query to native purchase flow`() {
        val url = "https://duckduckgo.com/subscriptions?origin=funnel_freescan_unknown&featurePage=pir".toUri()

        assertEquals(
            PirPurchaseRoute.NativePurchaseFlow(origin = "funnel_freescan_android", featurePage = "pir"),
            testee.route(url),
        )
    }

    @Test
    fun `test routes non duckduckgo host to not handled`() {
        assertEquals(PirPurchaseRoute.NotHandled, testee.route("https://evil.com/subscriptions".toUri()))
        assertEquals(PirPurchaseRoute.NotHandled, testee.route("https://duckduckgo.com.evil.com/pro".toUri()))
    }

    @Test
    fun `test routes dashboard url to not handled`() {
        assertEquals(PirPurchaseRoute.NotHandled, testee.route("https://duckduckgo.com/dbp".toUri()))
    }

    @Test
    fun `test routes opaque and malformed uri to not handled`() {
        assertEquals(PirPurchaseRoute.NotHandled, testee.route("mailto:someone@example.com".toUri()))
        assertEquals(PirPurchaseRoute.NotHandled, testee.route("".toUri()))
    }

    @Test
    fun `test routes a remote config paywall path to native purchase flow`() {
        val url = "https://duckduckgo.com/subscriptions/new/mobile/pir".toUri()
        whenever(subscriptions.isSubscriptionUrl(url)).thenReturn(true)

        assertEquals(
            PirPurchaseRoute.NativePurchaseFlow(origin = "funnel_freescan_android", featurePage = "pir"),
            testee.route(url),
        )
    }

    @Test
    fun `test ignores isSubscriptionUrl for a non duckduckgo host`() {
        val url = "https://evil.com/subscriptions/new/mobile/pir".toUri()
        whenever(subscriptions.isSubscriptionUrl(url)).thenReturn(true)

        assertEquals(PirPurchaseRoute.NotHandled, testee.route(url))
    }
}
