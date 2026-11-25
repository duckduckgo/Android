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

package com.duckduckgo.app.trackerdetection

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.trackerdetection.db.TdsDomainEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsEntityDao
import com.duckduckgo.app.trackerdetection.model.TdsDomainEntity
import com.duckduckgo.app.trackerdetection.model.TdsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class CachedTdsEntityLookupTest {

    private val mockEntityDao: TdsEntityDao = mock()
    private val mockDomainEntityDao: TdsDomainEntityDao = mock()

    private val testee = CachedTdsEntityLookup(mockEntityDao, mockDomainEntityDao)

    @Test
    fun whenExactDomainHitThenReturnsMatchingEntity() {
        whenever(mockEntityDao.getAll()).thenReturn(
            listOf(TdsEntity(name = "Acme", displayName = "Acme", prevalence = 0.5)),
        )
        whenever(mockDomainEntityDao.getAll()).thenReturn(
            listOf(TdsDomainEntity(domain = "tracker.com", entityName = "Acme")),
        )

        val entity = testee.entityForUrl("http://tracker.com")

        assertEquals("Acme", entity?.name)
    }

    @Test
    fun whenSubdomainHostThenLabelWalkFindsParentEntity() {
        whenever(mockEntityDao.getAll()).thenReturn(
            listOf(TdsEntity(name = "Acme", displayName = "Acme", prevalence = 0.5)),
        )
        whenever(mockDomainEntityDao.getAll()).thenReturn(
            listOf(TdsDomainEntity(domain = "tracker.com", entityName = "Acme")),
        )

        val entity = testee.entityForUrl("http://a.b.c.tracker.com")

        assertEquals("Acme", entity?.name)
    }

    @Test
    fun whenStringOverloadWithWwwPrefixThenWwwStripped() {
        whenever(mockEntityDao.getAll()).thenReturn(
            listOf(TdsEntity(name = "Acme", displayName = "Acme", prevalence = 0.5)),
        )
        whenever(mockDomainEntityDao.getAll()).thenReturn(
            listOf(TdsDomainEntity(domain = "tracker.com", entityName = "Acme")),
        )

        val entity = testee.entityForUrl("http://www.tracker.com")

        assertEquals("Acme", entity?.name)
    }

    @Test
    fun whenUriOverloadThenUsesRawHostWithoutWwwStrip() {
        whenever(mockEntityDao.getAll()).thenReturn(
            listOf(TdsEntity(name = "Acme", displayName = "Acme", prevalence = 0.5)),
        )
        whenever(mockDomainEntityDao.getAll()).thenReturn(
            listOf(TdsDomainEntity(domain = "tracker.com", entityName = "Acme")),
        )

        val entity = testee.entityForUrl("http://tracker.com".toUri())

        assertEquals("Acme", entity?.name)
    }

    @Test
    fun whenEntityForNameThenReturnsDirectHit() {
        whenever(mockEntityDao.getAll()).thenReturn(
            listOf(
                TdsEntity(name = "Acme", displayName = "Acme", prevalence = 0.5),
                TdsEntity(name = "Beta", displayName = "Beta", prevalence = 0.1),
            ),
        )
        whenever(mockDomainEntityDao.getAll()).thenReturn(emptyList())

        val entity = testee.entityForName("Beta")

        assertEquals("Beta", entity?.name)
    }

    @Test
    fun whenNoMatchThenReturnsNull() {
        whenever(mockEntityDao.getAll()).thenReturn(emptyList())
        whenever(mockDomainEntityDao.getAll()).thenReturn(emptyList())

        assertNull(testee.entityForUrl("http://unknown.com"))
        assertNull(testee.entityForUrl("http://unknown.com".toUri()))
        assertNull(testee.entityForName("Unknown"))
    }

    @Test
    fun whenRefreshThenSnapshotReplaced() {
        whenever(mockEntityDao.getAll()).thenReturn(
            listOf(TdsEntity(name = "First", displayName = "First", prevalence = 0.1)),
        )
        whenever(mockDomainEntityDao.getAll()).thenReturn(
            listOf(TdsDomainEntity(domain = "tracker.com", entityName = "First")),
        )

        assertEquals("First", testee.entityForUrl("http://tracker.com")?.name)

        whenever(mockEntityDao.getAll()).thenReturn(
            listOf(TdsEntity(name = "Second", displayName = "Second", prevalence = 0.2)),
        )
        whenever(mockDomainEntityDao.getAll()).thenReturn(
            listOf(TdsDomainEntity(domain = "tracker.com", entityName = "Second")),
        )
        testee.refresh()

        assertEquals("Second", testee.entityForUrl("http://tracker.com")?.name)
    }

    @Test
    fun whenFirstCallBeforeRefreshThenLazyLoadsFromDao() {
        whenever(mockEntityDao.getAll()).thenReturn(
            listOf(TdsEntity(name = "Lazy", displayName = "Lazy", prevalence = 0.5)),
        )
        whenever(mockDomainEntityDao.getAll()).thenReturn(
            listOf(TdsDomainEntity(domain = "tracker.com", entityName = "Lazy")),
        )

        val entity = testee.entityForUrl("http://tracker.com")

        assertEquals("Lazy", entity?.name)
    }
}
