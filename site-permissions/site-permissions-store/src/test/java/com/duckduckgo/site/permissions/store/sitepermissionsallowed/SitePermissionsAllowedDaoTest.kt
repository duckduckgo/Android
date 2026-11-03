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

package com.duckduckgo.site.permissions.store.sitepermissionsallowed

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.site.permissions.store.SitePermissionsDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SitePermissionsAllowedDaoTest {

    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        SitePermissionsDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val dao = db.sitePermissionsAllowedDao()

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenDeleteAllowedBeforeThenOnlyGrantsOlderThanCutoffAreDeleted() {
        val now = System.currentTimeMillis()
        val cutoff = now - SitePermissionAllowedEntity.EXPIRY_MILLIS
        dao.insert(SitePermissionAllowedEntity("expired.com", "tab", "camera", cutoff - 1))
        dao.insert(SitePermissionAllowedEntity("boundary.com", "tab", "camera", cutoff))
        dao.insert(SitePermissionAllowedEntity("live.com", "tab", "camera", now))

        dao.deleteAllowedBefore(cutoff)

        assertEquals(setOf("boundary.com", "live.com"), dao.getAllowedDomains().toSet())
    }

    @Test
    fun whenDomainHasSeveralGrantsThenGetAllowedDomainsReturnsItOnce() {
        val now = System.currentTimeMillis()
        dao.insert(SitePermissionAllowedEntity("example.com", "tab1", "camera", now))
        dao.insert(SitePermissionAllowedEntity("example.com", "tab2", "mic", now))

        assertEquals(listOf("example.com"), dao.getAllowedDomains())
    }
}
