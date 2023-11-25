/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.browser.applinks

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuckDuckGoSamsungStoreLinkHandlerTest {

    private val handler = DuckDuckGoSamsungStoreLinkHandler()

    @Test
    fun whenValidSamsungStoreUrlThenAppLinkTypeIsReturned() {
        val samsungStoreUrl = "https://galaxystore.samsung.com/detail/com.example.app"
        val result = handler.handleSamsungStoreLink(samsungStoreUrl)

        assertTrue(result is UrlType.AppLink)
        assertEquals("samsungapps://ProductDetail/com.example.app", (result as UrlType.AppLink).appIntent?.data?.toString())
        assertEquals("com.sec.android.app.samsungapps", result.storePackage)
        assertEquals(samsungStoreUrl, result.uriString)
    }

    @Test
    fun whenNonSamsungStoreUrlThenNullIsReturned() {
        val nonSamsungStoreUrl = "https://example.com"
        val result = handler.handleSamsungStoreLink(nonSamsungStoreUrl)

        assertNull(result)
    }

    @Test
    fun whenMalformedUrlThenNullIsReturned() {
        val malformedUrl = "https://galaxystore.samsung.com/detail/something/com.example.app"
        val result = handler.handleSamsungStoreLink(malformedUrl)

        assertNull(result)
    }

    @Test
    fun whenPackageIsMissingThenNullIsReturned() {
        val malformedUrl = "https://galaxystore.samsung.com/detail/"
        val result = handler.handleSamsungStoreLink(malformedUrl)

        assertNull(result)
    }
}
