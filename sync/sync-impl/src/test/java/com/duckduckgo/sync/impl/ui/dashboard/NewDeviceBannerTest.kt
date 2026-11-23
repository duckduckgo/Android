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

import com.duckduckgo.sync.impl.auditlog.PendingLogin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class NewDeviceBannerTest {

    @Test
    fun whenNoPendingLoginsThenNoBanner() {
        assertNull(NewDeviceBanner.from(emptyList()))
    }

    @Test
    fun whenOneLoginThenOneName() {
        val banner = NewDeviceBanner.from(listOf(login(1, "Laptop")))!!

        assertEquals(listOf("Laptop"), banner.deviceNames)
        assertEquals(0, banner.moreCount)
    }

    @Test
    fun whenSeveralLoginsThenNamesAreNewestFirst() {
        val banner = NewDeviceBanner.from(listOf(login(1, "Laptop"), login(2, "Phone"), login(3, "Tablet")))!!

        assertEquals(listOf("Tablet", "Phone", "Laptop"), banner.deviceNames)
    }

    @Test
    fun whenSameNameLogsInTwiceThenNameShownOnce() {
        val banner = NewDeviceBanner.from(listOf(login(1, "Laptop"), login(2, "Phone"), login(3, "Laptop")))!!

        assertEquals(listOf("Laptop", "Phone"), banner.deviceNames)
        assertEquals(0, banner.moreCount)
    }

    @Test
    fun whenEightNamesThenAllShownAndNoMore() {
        val banner = NewDeviceBanner.from((1..8).map { login(it, "Device $it") })!!

        assertEquals((8 downTo 1).map { "Device $it" }, banner.deviceNames)
        assertEquals(0, banner.moreCount)
    }

    @Test
    fun whenMoreThanEightNamesThenNewestEightShownAndRestCounted() {
        val banner = NewDeviceBanner.from((1..11).map { login(it, "Device $it") })!!

        assertEquals((11 downTo 4).map { "Device $it" }, banner.deviceNames)
        assertEquals(3, banner.moreCount)
    }

    @Test
    fun whenDuplicatesBeyondEightThenMoreCountsDistinctNames() {
        val logins = (1..9).map { login(it, "Device $it") } + login(10, "Device 1") + login(11, "Device 2")

        val banner = NewDeviceBanner.from(logins)!!

        assertEquals(1, banner.moreCount)
    }

    @Test
    fun whenBuiltThenKeepsEveryEntryIdIncludingDuplicateNames() {
        val banner = NewDeviceBanner.from(listOf(login(1, "Laptop"), login(2, "Laptop")))!!

        assertEquals(setOf("entry-1", "entry-2"), banner.entryIds)
    }

    private fun login(n: Int, name: String) = PendingLogin(
        entryId = "entry-$n",
        deviceId = "device-$n",
        deviceName = name,
        occurredAt = Instant.parse("2026-11-19T10:00:00Z").plusSeconds(n.toLong()),
    )
}
