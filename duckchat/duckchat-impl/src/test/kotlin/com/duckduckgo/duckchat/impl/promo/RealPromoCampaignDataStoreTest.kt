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

package com.duckduckgo.duckchat.impl.promo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RealPromoCampaignDataStoreTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var context: Context
    private lateinit var moshi: Moshi
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var testee: PromoCampaignDataStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        moshi = Moshi.Builder().build()
        dataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("promo_campaign_${UUID.randomUUID()}") },
        )
        testee = RealPromoCampaignDataStore(dataStore, coroutineRule.testDispatcherProvider, moshi)
    }

    @Test
    fun `when no record stored, then get returns null`() = runTest {
        assertNull(testee.getPromoRecord())
    }

    @Test
    fun `when record updated, then get returns it`() = runTest {
        testee.updatePromoRecord { FULL_RECORD }

        assertEquals(FULL_RECORD, testee.getPromoRecord())
    }

    @Test
    fun `when record updated, then transform receives stored record`() = runTest {
        testee.updatePromoRecord { FULL_RECORD }

        testee.updatePromoRecord { current ->
            assertEquals(FULL_RECORD, current)
            current
        }
    }

    @Test
    fun `when no record stored, then transform receives null`() = runTest {
        testee.updatePromoRecord { current ->
            assertNull(current)
            null
        }
    }

    @Test
    fun `when transform returns null, then record is removed`() = runTest {
        testee.updatePromoRecord { FULL_RECORD }

        testee.updatePromoRecord { null }

        assertNull(testee.getPromoRecord())
        assertNull(dataStore.data.first()[KEY])
    }

    @Test
    fun `when record updated, then update returns stored record`() = runTest {
        assertEquals(FULL_RECORD, testee.updatePromoRecord { FULL_RECORD })
    }

    @Test
    fun `when transform returns null, then update returns null`() = runTest {
        testee.updatePromoRecord { FULL_RECORD }

        assertNull(testee.updatePromoRecord { null })
    }

    @Test
    fun `when stored JSON omits fields, then they are null`() = runTest {
        dataStore.edit { it[KEY] = """{"campaignId":"campaignA"}""" }

        assertEquals(
            PromoRecord(campaignId = "campaignA"),
            testee.getPromoRecord(),
        )
    }

    @Test
    fun `when transform returns the stored record, then stored JSON is not rewritten`() = runTest {
        val storedJson = """{ "campaignId": "campaignA" }"""
        dataStore.edit { it[KEY] = storedJson }

        testee.updatePromoRecord { current -> current }

        // If the JSON is re-serialized, it won't have the spaces.
        assertEquals(storedJson, dataStore.data.first()[KEY])
    }

    @Test
    fun `when record changes, then flow emits each distinct record`() = runTest {
        testee.promoRecord.test {
            assertNull(awaitItem())
            testee.updatePromoRecord { FULL_RECORD }
            assertEquals(FULL_RECORD, awaitItem())
            testee.updatePromoRecord { FULL_RECORD }
            testee.updatePromoRecord { ENDED_RECORD }
            assertEquals(ENDED_RECORD, awaitItem())
            testee.updatePromoRecord { null }
            assertNull(awaitItem())
        }
    }

    @Test
    fun `when another key in the store changes, then flow does not emit the same record again`() = runTest {
        testee.updatePromoRecord { FULL_RECORD }

        testee.promoRecord.test {
            assertEquals(FULL_RECORD, awaitItem())
            dataStore.edit { it[OTHER_KEY] = "other value" }
            expectNoEvents()
        }
    }

    @Test
    fun `when updates overlap, then each transform sees the previous result`() = runTest {
        val bonusIds = (1..20).map { "bonus-$it" }

        bonusIds.map { bonusId ->
            launch {
                testee.updatePromoRecord { current ->
                    val previous = current?.promoId?.let { "$it," }.orEmpty()
                    PromoRecord(null, "campaign", previous + bonusId, null, null)
                }
            }
        }.joinAll()

        assertEquals(bonusIds.toSet(), testee.getPromoRecord()!!.promoId!!.split(",").toSet())
    }

    @Test
    fun `when stored JSON is mal-formed, then get throws`() = runTest {
        dataStore.edit { it[KEY] = "not json" }
        assertFailsWith<PromoRecordCorruptedException> {
            testee.getPromoRecord()
        }

        dataStore.edit { it[KEY] = "null" }
        assertFailsWith<PromoRecordCorruptedException> {
            testee.getPromoRecord()
        }

        dataStore.edit { it[KEY] = "" }
        assertFailsWith<PromoRecordCorruptedException> {
            testee.getPromoRecord()
        }

        dataStore.edit { it[KEY] = """{"campaignId":"campaignA"""" }
        assertFailsWith<PromoRecordCorruptedException> {
            testee.getPromoRecord()
        }
    }

    @Test
    fun `when stored JSON has wrong shape, then get throws`() = runTest {
        dataStore.edit { it[KEY] = """{"terms":"not an object"}""" }

        assertFailsWith<PromoRecordCorruptedException> {
            testee.getPromoRecord()
        }
    }

    @Test
    fun `when stored terms miss a required field, then get throws`() = runTest {
        dataStore.edit { it[KEY] = """{"terms":{"multiplier":2.5}}""" }

        assertFailsWith<PromoRecordCorruptedException> {
            testee.getPromoRecord()
        }
    }

    @Test
    fun `when stored JSON is mal-formed, then flow throws`() = runTest {
        dataStore.edit { it[KEY] = "not json" }
        testee.promoRecord.test {
            assertEquals(PromoRecordCorruptedException::class.java, awaitError()::class.java)
        }

        dataStore.edit { it[KEY] = "null" }
        testee.promoRecord.test {
            assertEquals(PromoRecordCorruptedException::class.java, awaitError()::class.java)
        }
    }

    @Test
    fun `when stored JSON is mal-formed, then update throws without overwriting it`() = runTest {
        dataStore.edit { it[KEY] = "not json" }
        var updateCalled = false

        assertFailsWith<PromoRecordCorruptedException> {
            testee.updatePromoRecord {
                updateCalled = true
                null
            }
        }
        assertFalse(updateCalled)
        assertEquals("not json", dataStore.data.first()[KEY])
    }

    @Test
    fun `when storage read fails, then get throws`() = runTest {
        val failingDataStore: PromoCampaignDataStore = RealPromoCampaignDataStore(
            FailingPreferencesDataStore(),
            coroutineRule.testDispatcherProvider,
            moshi,
        )

        assertFailsWith<PromoRecordStorageException> {
            failingDataStore.getPromoRecord()
        }
    }

    @Test
    fun `when storage read fails, then flow throws`() = runTest {
        val failingDataStore: PromoCampaignDataStore = RealPromoCampaignDataStore(
            FailingPreferencesDataStore(),
            coroutineRule.testDispatcherProvider,
            moshi,
        )

        failingDataStore.promoRecord.test {
            assertEquals(PromoRecordStorageException::class.java, awaitError()::class.java)
        }
    }

    @Test
    fun `when storage write fails, then update throws`() = runTest {
        val failingDataStore: PromoCampaignDataStore = RealPromoCampaignDataStore(
            FailingPreferencesDataStore(),
            coroutineRule.testDispatcherProvider,
            moshi,
        )

        assertFailsWith<PromoRecordStorageException> {
            failingDataStore.updatePromoRecord { FULL_RECORD }
        }
    }

    @Test
    fun `when transform throws, then exception propagates unwrapped and record is kept`() = runTest {
        testee.updatePromoRecord { FULL_RECORD }

        assertFailsWith<IllegalStateException> {
            testee.updatePromoRecord { throw IllegalStateException("caller error") }
        }
        assertEquals(FULL_RECORD, testee.getPromoRecord())
    }

    @Test
    fun `when cleared, then record is removed`() = runTest {
        testee.updatePromoRecord { FULL_RECORD }

        testee.clear()

        assertNull(testee.getPromoRecord())
        assertNull(dataStore.data.first()[KEY])
    }

    @Test
    fun `when no record stored and cleared, then get returns null`() = runTest {
        testee.clear()

        assertNull(testee.getPromoRecord())
    }

    @Test
    fun `when stored JSON is mal-formed and cleared, then get and update recover`() = runTest {
        dataStore.edit { it[KEY] = "not json" }

        testee.clear()
        assertNull(testee.getPromoRecord())

        testee.updatePromoRecord { FULL_RECORD }
        assertEquals(FULL_RECORD, testee.getPromoRecord())
    }

    @Test
    fun `when cleared, then other keys in the store are kept`() = runTest {
        dataStore.edit { it[OTHER_KEY] = "other value" }
        testee.updatePromoRecord { FULL_RECORD }

        testee.clear()

        assertEquals("other value", dataStore.data.first()[OTHER_KEY])
    }

    @Test
    fun `when storage write fails, then clear throws`() = runTest {
        val failingDataStore: PromoCampaignDataStore = RealPromoCampaignDataStore(
            FailingPreferencesDataStore(),
            coroutineRule.testDispatcherProvider,
            moshi,
        )

        assertFailsWith<PromoRecordStorageException> {
            failingDataStore.clear()
        }
    }

    private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T {
        val error = runCatching { block() }.exceptionOrNull()
        assertEquals(T::class.java, error?.javaClass)
        return error as T
    }

    /** A preferences store whose disk is broken: every read and write fails with [IOException]. */
    private class FailingPreferencesDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("disk") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("disk")
    }

    private companion object {
        val KEY = stringPreferencesKey("DUCK_AI_PROMO_RECORD")
        val OTHER_KEY = stringPreferencesKey("OTHER_DUCK_CHAT_SETTING")

        val FULL_RECORD = PromoRecord(
            dismissedCampaignId = "dismissed-campaign",
            campaignId = "campaign",
            promoId = "bonus-id",
            terms = PromoTerms(multiplier = 2.5f, expiresAtMillis = 2_000L),
            lastRedeemAttemptMillis = 1_000L,
        )

        val ENDED_RECORD = PromoRecord(campaignId = "campaign")
    }
}
