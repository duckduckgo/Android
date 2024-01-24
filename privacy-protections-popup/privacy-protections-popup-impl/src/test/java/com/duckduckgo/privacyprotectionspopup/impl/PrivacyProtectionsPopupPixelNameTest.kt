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

package com.duckduckgo.privacyprotectionspopup.impl

import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.COUNT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.DAILY
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.UNIQUE
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class PrivacyProtectionsPopupPixelNameTest(
    private val pixel: PrivacyProtectionsPopupPixelName,
) {

    @Test
    fun pixelNameShouldHaveCorrectPrefix() {
        val pixelName = pixel.pixelName
        val requiredPrefix = "m_privacy_protections_popup_"
        assertTrue(
            "Pixel name should start with '$requiredPrefix': $pixelName",
            pixelName.startsWith(requiredPrefix),
        )
    }

    @Test
    fun pixelNameSuffixShouldMatchPixelType() {
        val pixelName = pixel.pixelName
        val requiredSuffix = when (pixel.type) {
            COUNT -> "_c"
            DAILY -> "_d"
            UNIQUE -> "_u"
        }
        assertTrue(
            "Pixel name should end with '$requiredSuffix': $pixelName",
            pixelName.endsWith(requiredSuffix),
        )
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun data(): Collection<Array<PrivacyProtectionsPopupPixelName>> =
            PrivacyProtectionsPopupPixelName.entries.map { arrayOf(it) }
    }
}
