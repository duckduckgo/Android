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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewDeviceBannerTextTest {

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun whenOneDeviceThenSingularTitleAndOneName() {
        val banner = banner("Laptop")

        assertEquals("New synced device", banner.title(resources))
        assertEquals("This phone has synced with Laptop.", banner.body(resources))
    }

    @Test
    fun whenTwoDevicesThenPluralTitleAndBothNames() {
        val banner = banner("Laptop", "Phone")

        assertEquals("New synced devices", banner.title(resources))
        assertEquals("This phone has synced with Laptop and Phone.", banner.body(resources))
    }

    @Test
    fun whenSeveralDevicesThenCommasThenAndBeforeLastName() {
        assertEquals(
            "This phone has synced with Laptop, Phone, Tablet and Desktop.",
            banner("Laptop", "Phone", "Tablet", "Desktop").body(resources),
        )
    }

    @Test
    fun whenMoreThanShownThenAllNamesThenMoreCount() {
        val banner = banner("A", "B", "C", "D", "E", "F", "G", "H", moreCount = 3)

        assertEquals("New synced devices", banner.title(resources))
        assertEquals("This phone has synced with A, B, C, D, E, F, G, H and 3 more.", banner.body(resources))
    }

    @Test
    fun whenOneMoreThanShownThenOneMore() {
        assertEquals(
            "This phone has synced with A, B, C, D, E, F, G, H and 1 more.",
            banner("A", "B", "C", "D", "E", "F", "G", "H", moreCount = 1).body(resources),
        )
    }

    private fun banner(vararg names: String, moreCount: Int = 0) = NewDeviceBanner(names.toList(), moreCount, emptySet())
}
