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

package com.duckduckgo.sync.impl.ui.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.sync.impl.auditlog.PendingLogin
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class NewDeviceBannerTextTest {

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun whenOneDeviceThenSingularTitleAndOneName() {
        val banner = bannerFor("Laptop")

        assertEquals("New synced device", banner.title(resources))
        assertEquals("This phone has synced with Laptop.", banner.body(resources))
    }

    @Test
    fun whenTwoDevicesThenPluralTitleAndNoComma() {
        val banner = bannerFor("Laptop", "Phone")

        assertEquals("New synced devices", banner.title(resources))
        assertEquals("This phone has synced with Laptop and Phone.", banner.body(resources))
    }

    @Test
    fun whenThreeDevicesThenCommaBeforeAnd() {
        assertEquals(
            "This phone has synced with Laptop, Phone, and Tablet.",
            bannerFor("Laptop", "Phone", "Tablet").body(resources),
        )
    }

    @Test
    fun whenEightDevicesThenAllNamesAndCommaBeforeAnd() {
        assertEquals(
            "This phone has synced with A, B, C, D, E, F, G, and H.",
            bannerFor("A", "B", "C", "D", "E", "F", "G", "H").body(resources),
        )
    }

    @Test
    fun whenElevenDevicesThenEightNamesThenCommaBeforeMoreCount() {
        val banner = bannerFor("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K")

        assertEquals("New synced devices", banner.title(resources))
        assertEquals("This phone has synced with A, B, C, D, E, F, G, H, and 3 more.", banner.body(resources))
    }

    @Test
    fun whenNineDevicesThenOneMore() {
        assertEquals(
            "This phone has synced with A, B, C, D, E, F, G, H, and 1 more.",
            bannerFor("A", "B", "C", "D", "E", "F", "G", "H", "I").body(resources),
        )
    }

    private fun bannerFor(vararg namesNewestFirst: String): NewDeviceBanner {
        val oldestFirst = namesNewestFirst.reversed().mapIndexed { index, name ->
            PendingLogin("entry-$index", "device-$index", name, Instant.ofEpochSecond(index.toLong()))
        }
        return NewDeviceBanner.from(oldestFirst)!!
    }
}
