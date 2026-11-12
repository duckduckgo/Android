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

package com.duckduckgo.pir.impl.common

import androidx.webkit.UserAgentMetadata
import androidx.webkit.UserAgentMetadata.BrandVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PirClientHintsTest {

    @Test
    fun whenChromeOnWindowsThenReturnsDesktopWindowsHints() {
        val hints = PirClientHintsParser.parse(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36",
        )

        assertEquals(PirClientHints(chromeMajorVersion = "142", platform = "Windows", mobile = false), hints)
    }

    @Test
    fun whenChromeOnMacThenReturnsMacOsPlatform() {
        val hints = PirClientHintsParser.parse(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36",
        )

        assertEquals("macOS", hints?.platform)
        assertEquals(false, hints?.mobile)
    }

    @Test
    fun whenChromeOnLinuxThenReturnsLinuxPlatform() {
        val hints = PirClientHintsParser.parse(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36",
        )

        assertEquals("Linux", hints?.platform)
    }

    @Test
    fun whenChromeOnChromeOsThenReturnsChromeOsPlatform() {
        val hints = PirClientHintsParser.parse(
            "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36",
        )

        assertEquals("Chrome OS", hints?.platform)
    }

    @Test
    fun whenChromeOnAndroidPhoneThenReturnsMobileAndroidHints() {
        val hints = PirClientHintsParser.parse(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36",
        )

        assertEquals("Android", hints?.platform)
        assertEquals(true, hints?.mobile)
    }

    @Test
    fun whenChromeOnAndroidTabletThenReturnsNonMobileAndroidHints() {
        val hints = PirClientHintsParser.parse(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36",
        )

        assertEquals("Android", hints?.platform)
        assertEquals(false, hints?.mobile)
    }

    @Test
    fun whenChromeUserAgentHasExtraTokensThenReturnsNull() {
        listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 Extra/1.0",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Extra/1.0 Chrome/142.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 14; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/142.0.0.0 Mobile Safari/537.36",
        ).forEach { assertNull(it, PirClientHintsParser.parse(it)) }
    }

    @Test
    fun whenChromeUserAgentHasFullVersionThenReturnsNull() {
        assertNull(
            PirClientHintsParser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.7444.60 Safari/537.36",
            ),
        )
    }

    @Test
    fun whenFirefoxThenReturnsNull() {
        assertNull(PirClientHintsParser.parse("Mozilla/5.0 (X11; Linux x86_64; rv:142.0) Gecko/20100101 Firefox/142.0"))
    }

    @Test
    fun whenSafariThenReturnsNull() {
        assertNull(
            PirClientHintsParser.parse(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
            ),
        )
    }

    @Test
    fun whenChromeOnIosThenReturnsNull() {
        assertNull(
            PirClientHintsParser.parse(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/142.0.0.0 " +
                    "Mobile/15E148 Safari/604.1",
            ),
        )
    }

    @Test
    fun whenChromeOnUnknownPlatformThenReturnsNull() {
        assertNull(PirClientHintsParser.parse("Mozilla/5.0 (Fuchsia) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"))
    }

    @Test
    fun whenNotAUserAgentThenReturnsNull() {
        assertNull(PirClientHintsParser.parse("not a user agent"))
    }

    @Test
    fun whenBuildingMetadataThenUsesHintsAndKeepsDefaultGreaseBrand() {
        val hints = PirClientHints(chromeMajorVersion = "142", platform = "Windows", mobile = false)

        val metadata = hints.toUserAgentMetadata(defaultMetadata(), supportsFormFactors = false)

        assertEquals(
            listOf(
                Triple("Chromium", "142", "142.0.0.0"),
                Triple("Google Chrome", "142", "142.0.0.0"),
                Triple("Not-A.Brand", "99", "99.0.0.0"),
            ),
            metadata.brandVersionList.map { Triple(it.brand, it.majorVersion, it.fullVersion) },
        )
        assertEquals("142.0.0.0", metadata.fullVersion)
        assertEquals("Windows", metadata.platform)
        assertFalse(metadata.isMobile)
    }

    @Test
    fun whenBuildingMetadataThenBlanksDeviceDetails() {
        val hints = PirClientHints(chromeMajorVersion = "142", platform = "Windows", mobile = false)

        val metadata = hints.toUserAgentMetadata(defaultMetadata(), supportsFormFactors = false)

        assertDeviceDetailsBlank(metadata)
    }

    @Test
    fun whenRemovingDeviceDetailsThenKeepsDefaultBrandsPlatformAndMobile() {
        val default = defaultMetadata()

        val metadata = default.withoutDeviceDetails()

        assertEquals(default.brandVersionList, metadata.brandVersionList)
        assertEquals("Android", metadata.platform)
        assertTrue(metadata.isMobile)
        assertDeviceDetailsBlank(metadata)
    }

    private fun assertDeviceDetailsBlank(metadata: UserAgentMetadata) {
        assertEquals("", metadata.model)
        assertEquals("", metadata.platformVersion)
        assertEquals("", metadata.architecture)
        assertEquals(UserAgentMetadata.BITNESS_DEFAULT, metadata.bitness)
        assertFalse(metadata.isWow64)
    }

    private fun defaultMetadata(): UserAgentMetadata = UserAgentMetadata.Builder()
        .setBrandVersionList(
            listOf(
                brandVersion("Not-A.Brand", "99"),
                brandVersion("Android WebView", "157"),
                brandVersion("Chromium", "157"),
            ),
        )
        .setFullVersion("157.0.7000.10")
        .setPlatform("Android")
        .setPlatformVersion("15.0.0")
        .setArchitecture("arm")
        .setModel("Pixel 8")
        .setBitness(64)
        .setMobile(true)
        .build()

    private fun brandVersion(brand: String, majorVersion: String) = BrandVersion.Builder()
        .setBrand(brand)
        .setMajorVersion(majorVersion)
        .setFullVersion("$majorVersion.0.0.0")
        .build()
}
