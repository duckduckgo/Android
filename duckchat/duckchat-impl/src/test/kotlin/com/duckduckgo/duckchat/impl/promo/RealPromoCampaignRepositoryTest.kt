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

import app.cash.turbine.test
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class RealPromoCampaignRepositoryTest {
    private val dataStore = FakePromoCampaignDataStore()
    private val currentTimeProvider = FakeCurrentTimeProvider(NOW)
    private val testee = RealPromoCampaignRepository(dataStore, currentTimeProvider)

    //region Get Promo Record
    @Test
    fun `when no record stored, then get returns null`() = runTest {
        assertNull(testee.getPromoRecord())
    }

    @Test
    fun `when record stored, then get returns it`() = runTest {
        dataStore.record.value = ACTIVE_RECORD

        assertEquals(ACTIVE_RECORD, testee.getPromoRecord())
    }
    //endregion

    //region Campaign Dismissal
    @Test
    fun `when no record stored and campaign dismissed, then record holds only the dismissal`() = runTest {
        testee.dismissCampaign(CAMPAIGN_ID)

        assertEquals(PromoRecord(dismissedCampaignId = CAMPAIGN_ID), dataStore.record.value)
    }

    @Test
    fun `when promo pending and campaign dismissed, then dismissal is added to the record`() = runTest {
        dataStore.record.value = PENDING_RECORD.copy(dismissedCampaignId = null)

        testee.dismissCampaign(CAMPAIGN_ID)

        assertEquals(PENDING_RECORD, dataStore.record.value)
    }

    @Test
    fun `when promo active and campaign dismissed, then dismissal is added to the record`() = runTest {
        dataStore.record.value = ACTIVE_RECORD.copy(dismissedCampaignId = null)

        testee.dismissCampaign(CAMPAIGN_ID)

        assertEquals(ACTIVE_RECORD, dataStore.record.value)
    }

    @Test
    fun `when promo has ended and campaign dismissed, then record is unchanged`() = runTest {
        dataStore.record.value = ENDED_RECORD

        testee.dismissCampaign(CAMPAIGN_ID)

        assertEquals(ENDED_RECORD, dataStore.record.value)
    }
    //endregion

    //region Promo Initialization
    @Test
    fun `when no record stored and promo initialized, then record is marked as pending and returned`() = runTest {
        val expected = PromoRecord(campaignId = CAMPAIGN_ID, promoId = PROMO_ID.toString())

        val returned = testee.initializePromo(CAMPAIGN_ID, PROMO_ID)

        assertEquals(expected, dataStore.record.value)
        assertEquals(expected, returned)
    }

    @Test
    fun `when campaign dismissed and promo initialized, then record is marked as pending, keeping the dismissal, and returned`() = runTest {
        dataStore.record.value = PromoRecord(dismissedCampaignId = CAMPAIGN_ID)
        val expected = PromoRecord(dismissedCampaignId = CAMPAIGN_ID, campaignId = CAMPAIGN_ID, promoId = PROMO_ID.toString())

        val returned = testee.initializePromo(CAMPAIGN_ID, PROMO_ID)

        assertEquals(expected, dataStore.record.value)
        assertEquals(expected, returned)
    }

    @Test
    fun `when promo pending and initialized again, then record is unchanged and returned`() = runTest {
        dataStore.record.value = PENDING_RECORD

        val returned = testee.initializePromo(CAMPAIGN_ID, OTHER_PROMO_ID)

        assertEquals(PENDING_RECORD, dataStore.record.value)
        assertEquals(PENDING_RECORD, returned)
    }

    @Test
    fun `when promo has ended and initialized again, then record is unchanged and returned`() = runTest {
        dataStore.record.value = ENDED_RECORD

        val returned = testee.initializePromo(CAMPAIGN_ID, PROMO_ID)

        assertEquals(ENDED_RECORD, dataStore.record.value)
        assertEquals(ENDED_RECORD, returned)
    }

    @Test
    fun `when pending promo invalidated and initialized again, then record is marked as pending with the new promo and returned`() = runTest {
        dataStore.record.value = PENDING_RECORD
        testee.invalidatePromo(PROMO_ID)
        val expected = PromoRecord(dismissedCampaignId = CAMPAIGN_ID, campaignId = CAMPAIGN_ID, promoId = OTHER_PROMO_ID.toString())

        val returned = testee.initializePromo(CAMPAIGN_ID, OTHER_PROMO_ID)

        assertEquals(expected, dataStore.record.value)
        assertEquals(expected, returned)
    }
    //endregion

    //region Promo Activation
    @Test
    fun `when promo initialized again and invalidated promo started, then record is unchanged`() = runTest {
        dataStore.record.value = PENDING_RECORD
        testee.invalidatePromo(PROMO_ID)
        val reinitialized = testee.initializePromo(CAMPAIGN_ID, OTHER_PROMO_ID)

        testee.startPromo(PROMO_ID, TERMS)

        assertEquals(reinitialized, dataStore.record.value)
    }

    @Test
    fun `when promo pending and started, then record is marked as active`() = runTest {
        dataStore.record.value = PENDING_RECORD

        testee.startPromo(PROMO_ID, TERMS)

        assertEquals(
            PENDING_RECORD.copy(terms = TERMS, lastRedeemAttemptMillis = NOW),
            dataStore.record.value,
        )
    }

    @Test
    fun `when promo active and started again, then record is marked as active with the new terms`() = runTest {
        val newTerms = PromoTerms(multiplier = 3f, expiresAtMillis = NOW + 5_000)
        dataStore.record.value = ACTIVE_RECORD

        testee.startPromo(PROMO_ID, newTerms)

        assertEquals(
            ACTIVE_RECORD.copy(terms = newTerms, lastRedeemAttemptMillis = NOW),
            dataStore.record.value,
        )
    }

    @Test
    fun `when promo pending and started with upper case promo ID, then record is marked as active`() = runTest {
        dataStore.record.value = PENDING_RECORD

        testee.startPromo(UUID.fromString(PROMO_ID.toString().uppercase()), TERMS)

        assertEquals(
            PENDING_RECORD.copy(terms = TERMS, lastRedeemAttemptMillis = NOW),
            dataStore.record.value,
        )
    }

    @Test
    fun `when other promo started, then record is unchanged`() = runTest {
        dataStore.record.value = PENDING_RECORD

        testee.startPromo(OTHER_PROMO_ID, TERMS)

        assertEquals(PENDING_RECORD, dataStore.record.value)
    }

    @Test
    fun `when no record stored and promo started, then no record is stored`() = runTest {
        testee.startPromo(PROMO_ID, TERMS)

        assertNull(dataStore.record.value)
    }
    //endregion

    //region Redeem Attempt Recording
    @Test
    fun `when promo pending and redeem attempt recorded, then redeem attempt time is stored`() = runTest {
        dataStore.record.value = PENDING_RECORD

        testee.recordRedeemAttempt(PROMO_ID)

        assertEquals(
            PENDING_RECORD.copy(lastRedeemAttemptMillis = NOW),
            dataStore.record.value,
        )
    }

    @Test
    fun `when redeem attempt recorded for other promo, then record is unchanged`() = runTest {
        dataStore.record.value = PENDING_RECORD

        testee.recordRedeemAttempt(OTHER_PROMO_ID)

        assertEquals(PENDING_RECORD, dataStore.record.value)
    }

    @Test
    fun `when no record stored and redeem attempt recorded, then no record is stored`() = runTest {
        testee.recordRedeemAttempt(PROMO_ID)

        assertNull(dataStore.record.value)
    }
    //endregion

    //region Promo Invalidation
    @Test
    fun `when promo pending and invalidated, then record is marked as uninitialized`() = runTest {
        dataStore.record.value = PENDING_RECORD

        testee.invalidatePromo(PROMO_ID)

        assertEquals(PromoRecord(dismissedCampaignId = CAMPAIGN_ID), dataStore.record.value)
    }

    @Test
    fun `when promo pending without dismissal and invalidated, then record is removed`() = runTest {
        dataStore.record.value = PENDING_RECORD.copy(dismissedCampaignId = null)

        testee.invalidatePromo(PROMO_ID)

        assertNull(dataStore.record.value)
    }

    @Test
    fun `when promo active and invalidated, then record is marked as ended`() = runTest {
        dataStore.record.value = ACTIVE_RECORD

        testee.invalidatePromo(PROMO_ID)

        assertEquals(ENDED_RECORD, dataStore.record.value)
    }

    @Test
    fun `when other promo invalidated, then record is unchanged`() = runTest {
        dataStore.record.value = ACTIVE_RECORD

        testee.invalidatePromo(OTHER_PROMO_ID)

        assertEquals(ACTIVE_RECORD, dataStore.record.value)
    }

    @Test
    fun `when no record stored and promo invalidated, then no record is stored`() = runTest {
        testee.invalidatePromo(PROMO_ID)

        assertNull(dataStore.record.value)
    }
    //endregion

    //region Promo End
    @Test
    fun `when promo pending and ended without checking expiry, then record is marked as ended`() = runTest {
        dataStore.record.value = PENDING_RECORD

        testee.endPromo(checkExpiry = false)

        assertEquals(ENDED_RECORD, dataStore.record.value)
    }

    @Test
    fun `when promo active and ended without checking expiry, then record is marked as ended`() = runTest {
        dataStore.record.value = ACTIVE_RECORD

        testee.endPromo(checkExpiry = false)

        assertEquals(ENDED_RECORD, dataStore.record.value)
    }

    @Test
    fun `when no promo stored and ended, then record is unchanged`() = runTest {
        dataStore.record.value = PromoRecord(dismissedCampaignId = CAMPAIGN_ID)

        testee.endPromo(checkExpiry = false)

        assertEquals(PromoRecord(dismissedCampaignId = CAMPAIGN_ID), dataStore.record.value)
    }

    @Test
    fun `when terms expire now and ended checking expiry, then record is marked as ended`() = runTest {
        dataStore.record.value = ACTIVE_RECORD.copy(terms = TERMS.copy(expiresAtMillis = NOW))

        testee.endPromo(checkExpiry = true)

        assertEquals(ENDED_RECORD, dataStore.record.value)
    }

    @Test
    fun `when terms not expired and ended checking expiry, then record is unchanged`() = runTest {
        dataStore.record.value = ACTIVE_RECORD

        testee.endPromo(checkExpiry = true)

        assertEquals(ACTIVE_RECORD, dataStore.record.value)
    }

    @Test
    fun `when promo pending and ended checking expiry, then record is unchanged`() = runTest {
        dataStore.record.value = PENDING_RECORD

        testee.endPromo(checkExpiry = true)

        assertEquals(PENDING_RECORD, dataStore.record.value)
    }
    //endregion

    //region Promo Clear
    @Test
    fun `when promo has ended and record is cleared, then record is removed`() = runTest {
        dataStore.record.value = ENDED_RECORD

        testee.clear()

        assertNull(dataStore.record.value)
    }
    //endregion

    //region IsPromoActive Flow
    @Test
    fun `when no record stored, then promo is not active`() = runTest {
        testee.isPromoActive.test {
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `when promo pending, then promo is not active`() = runTest {
        dataStore.record.value = PENDING_RECORD

        testee.isPromoActive.test {
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `when promo has ended, then promo is not active`() = runTest {
        dataStore.record.value = ENDED_RECORD

        testee.isPromoActive.test {
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `when terms not expired, then promo is active`() = runTest {
        dataStore.record.value = ACTIVE_RECORD

        testee.isPromoActive.test {
            assertTrue(awaitItem())
        }
    }

    @Test
    fun `when terms expire now, then promo is not active`() = runTest {
        dataStore.record.value = ACTIVE_RECORD.copy(terms = TERMS.copy(expiresAtMillis = NOW))

        testee.isPromoActive.test {
            assertFalse(awaitItem())
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `when terms expire while collecting, then promo stops being active at expiry`() = runTest {
        dataStore.record.value = ACTIVE_RECORD

        testee.isPromoActive.test {
            assertTrue(awaitItem())

            advanceTimeBy((TERMS.expiresAtMillis - NOW - 1).milliseconds)
            expectNoEvents()

            advanceTimeBy(1.milliseconds)
            assertFalse(awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `when pending promo started, then promo becomes active`() = runTest {
        dataStore.record.value = PENDING_RECORD

        testee.isPromoActive.test {
            assertFalse(awaitItem())

            testee.startPromo(PROMO_ID, TERMS)

            assertTrue(awaitItem())
        }
    }

    @Test
    fun `when active promo ended, then promo stops being active`() = runTest {
        dataStore.record.value = ACTIVE_RECORD

        testee.isPromoActive.test {
            assertTrue(awaitItem())

            testee.endPromo(checkExpiry = false)

            assertFalse(awaitItem())
        }
    }

    @Test
    fun `when record changes without affecting active state, then promo active state is not emitted again`() = runTest {
        dataStore.record.value = PENDING_RECORD

        testee.isPromoActive.test {
            assertFalse(awaitItem())

            testee.recordRedeemAttempt(PROMO_ID)

            expectNoEvents()
        }
    }
    //endregion

    //region PromoRecord Flow
    @Test
    fun `when reading the record fails, then flow retries until it reads it`() = runTest {
        dataStore.record.value = ACTIVE_RECORD
        repeat(2) { dataStore.readFailures += PromoRecordStorageException(IOException("disk")) }

        testee.promoRecord.test {
            assertEquals(ACTIVE_RECORD, awaitItem())
        }
        assertEquals(3, dataStore.reads)
    }

    @Test
    fun `when record is corrupted, then flow fails without retrying`() = runTest {
        dataStore.readFailures += PromoRecordCorruptedException(JsonDataException("corrupted"))

        testee.promoRecord.test {
            assertEquals(PromoRecordCorruptedException::class.java, awaitError()::class.java)
        }
        assertEquals(1, dataStore.reads)
    }
    //endregion

    private companion object {
        const val NOW = 10_000L
        const val CAMPAIGN_ID = "campaign"
        val PROMO_ID: UUID = UUID.fromString("6f1c2a1e-3b4d-4c5e-8f90-1a2b3c4d5e6f")
        val OTHER_PROMO_ID: UUID = UUID.fromString("0a9b8c7d-6e5f-4a3b-9c2d-1e0f9a8b7c6d")
        val TERMS = PromoTerms(multiplier = 2f, expiresAtMillis = NOW + 1_000)
        val PENDING_RECORD = PromoRecord(
            dismissedCampaignId = CAMPAIGN_ID,
            campaignId = CAMPAIGN_ID,
            promoId = PROMO_ID.toString(),
            lastRedeemAttemptMillis = 5_000L,
        )
        val ACTIVE_RECORD = PENDING_RECORD.copy(terms = TERMS)
        val ENDED_RECORD = PromoRecord(campaignId = CAMPAIGN_ID)
    }
}
