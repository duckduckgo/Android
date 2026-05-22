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

package com.duckduckgo.adblocking.impl

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.adblocking.impl.store.AdBlockingExtensionDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdBlockingExtensionRepositoryTest {

    private lateinit var database: AdBlockingExtensionDatabase
    private lateinit var repository: AdBlockingExtensionRepository

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AdBlockingExtensionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RealAdBlockingExtensionRepository(database.adBlockingExtensionDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun whenStoreIsEmptyThenGetStoredVersionReturnsNull() = runTest {
        assertNull(repository.getStoredVersion())
    }

    @Test
    fun whenStoreScriptletsCalledThenPersistsVersionAndScriptlets() = runTest {
        val scriptlets = mapOf(
            "scriptlets/isolated/ublock-filters.js" to "isolated".toByteArray(),
            "scriptlets/main/ublock-filters.js" to "main".toByteArray(),
        )

        repository.storeScriptlets("2026.3.9", scriptlets)

        assertEquals("2026.3.9", repository.getStoredVersion())
        val stored = database.adBlockingExtensionDao().scriptletsFlow().first().associateBy { it.name }
        assertEquals(scriptlets.keys, stored.keys)
        assertArrayEquals("isolated".toByteArray(), stored.getValue("scriptlets/isolated/ublock-filters.js").content)
        assertArrayEquals("main".toByteArray(), stored.getValue("scriptlets/main/ublock-filters.js").content)
    }

    @Test
    fun whenStoreScriptletsCalledThenDeletesPreviousVersionAtomically() = runTest {
        repository.storeScriptlets(
            version = "1.0.0",
            scriptlets = mapOf("old/path.js" to "old".toByteArray()),
        )

        repository.storeScriptlets(
            version = "2.0.0",
            scriptlets = mapOf("new/path.js" to "new".toByteArray()),
        )

        assertEquals("2.0.0", repository.getStoredVersion())
        val stored = database.adBlockingExtensionDao().scriptletsFlow().first()
        assertEquals(setOf("new/path.js"), stored.map { it.name }.toSet())
    }

    @Test
    fun whenStoreScriptletsCalledWithEmptyMapThenVersionIsStillPersisted() = runTest {
        repository.storeScriptlets(version = "2026.3.9", scriptlets = emptyMap())

        assertEquals("2026.3.9", repository.getStoredVersion())
        assertTrue(database.adBlockingExtensionDao().scriptletsFlow().first().isEmpty())
    }
}
