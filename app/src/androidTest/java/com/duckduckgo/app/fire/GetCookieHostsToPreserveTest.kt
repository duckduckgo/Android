/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.fire

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.global.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetCookieHostsToPreserveTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val fireproofWebsiteDao = db.fireproofWebsiteDao()
    private val getHostsToPreserve = GetCookieHostsToPreserve(fireproofWebsiteDao)

    @Test
    fun whenSubDomainFireproofWebsiteThenExpectedListReturned() {
        givenFireproofWebsitesStored(FireproofWebsiteEntity("mobile.twitter.com"))
        val expectedList = listOf(
            ".mobile.twitter.com",
            "mobile.twitter.com",
            ".twitter.com",
            ".com"
        )

        val hostsToPreserve = getHostsToPreserve()

        assertTrue(expectedList.all { hostsToPreserve.contains(it) })
    }

    @Test
    fun whenFireproofWebsiteThenExpectedListReturned() {
        givenFireproofWebsitesStored(FireproofWebsiteEntity("twitter.com"))
        val expectedList = listOf("twitter.com", ".twitter.com", ".com")

        val hostsToPreserve = getHostsToPreserve()

        assertTrue(expectedList.all { hostsToPreserve.contains(it) })
    }

    @Test
    fun whenMultipleFireproofWebsiteWithSameTopLevelThenExpectedListReturned() {
        givenFireproofWebsitesStored(FireproofWebsiteEntity("twitter.com"))
        givenFireproofWebsitesStored(FireproofWebsiteEntity("example.com"))
        val expectedList = listOf(
            ".example.com",
            "example.com",
            "twitter.com",
            ".twitter.com",
            ".com"
        )

        val hostsToPreserve = getHostsToPreserve()

        assertEquals(expectedList.size, hostsToPreserve.size)
        assertTrue(expectedList.all { hostsToPreserve.contains(it) })
    }

    private fun givenFireproofWebsitesStored(fireproofWebsiteEntity: FireproofWebsiteEntity) {
        fireproofWebsiteDao.insert(fireproofWebsiteEntity)
    }
}
