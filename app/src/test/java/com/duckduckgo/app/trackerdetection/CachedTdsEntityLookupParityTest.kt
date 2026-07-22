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
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class CachedTdsEntityLookupParityTest {

    private val entities = listOf(
        TdsEntity(name = "Acme", displayName = "Acme", prevalence = 0.5),
        TdsEntity(name = "Beta", displayName = "Beta", prevalence = 0.1),
    )

    private val domainEntities = listOf(
        TdsDomainEntity(domain = "tracker.com", entityName = "Acme"),
        TdsDomainEntity(domain = "deep.beta.com", entityName = "Beta"),
    )

    private val entitiesByName: Map<String, TdsEntity> = entities.associateBy { it.name }
    private val domainsByName: Map<String, TdsDomainEntity> = domainEntities.associateBy { it.domain }

    private val entityDao: TdsEntityDao = mock<TdsEntityDao>().also { dao ->
        whenever(dao.getAll()).thenReturn(entities)
        whenever(dao.get(any())).thenAnswer { invocation -> entitiesByName[invocation.getArgument<String>(0)] }
    }

    private val domainEntityDao: TdsDomainEntityDao = mock<TdsDomainEntityDao>().also { dao ->
        whenever(dao.getAll()).thenReturn(domainEntities)
        whenever(dao.get(any())).thenAnswer { invocation -> domainsByName[invocation.getArgument<String>(0)] }
    }

    private val legacy = TdsEntityLookup(entityDao, domainEntityDao)
    private val cached = CachedTdsEntityLookup(entityDao, domainEntityDao).also { it.refresh() }

    private val goldenHosts = listOf(
        "http://tracker.com",
        "http://www.tracker.com",
        "http://api.tracker.com",
        "http://a.b.c.tracker.com",
        "http://deep.beta.com",
        "http://x.deep.beta.com",
        "http://unknown.com",
        "http://nested.unknown.com",
        "http://localhost",
    )

    @Test
    fun stringOverloadParity() {
        for (url in goldenHosts) {
            val expected = legacy.entityForUrl(url)
            val actual = cached.entityForUrl(url)
            assertEquals("string parity for $url", expected?.name, actual?.name)
        }
    }

    @Test
    fun uriOverloadParity() {
        for (url in goldenHosts) {
            val expected = legacy.entityForUrl(url.toUri())
            val actual = cached.entityForUrl(url.toUri())
            assertEquals("uri parity for $url", expected?.name, actual?.name)
        }
    }

    @Test
    fun entityForNameParity() {
        for (name in listOf("Acme", "Beta", "Unknown")) {
            val expected = legacy.entityForName(name)
            val actual = cached.entityForName(name)
            assertEquals("name parity for $name", expected?.name, actual?.name)
        }
    }
}
