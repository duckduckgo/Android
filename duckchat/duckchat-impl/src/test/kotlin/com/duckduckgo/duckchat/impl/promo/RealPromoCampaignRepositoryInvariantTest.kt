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

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.UUID

/**
 * Duck.ai rejects a record holding terms without a promo, or a promo without a campaign,
 * so every write from every reachable record must keep them nested.
 */
@RunWith(Parameterized::class)
class RealPromoCampaignRepositoryInvariantTest(private val testCase: TestCase) {
    private val dataStore = FakePromoCampaignDataStore()
    private val currentTimeProvider = FakeCurrentTimeProvider(NOW)
    private val testee = RealPromoCampaignRepository(dataStore, currentTimeProvider)

    @Test
    fun `when record written, then terms imply a promo and a promo implies a campaign`() = runTest {
        dataStore.record.value = testCase.start.record

        testCase.write.action(testee)

        val record = dataStore.record.value ?: return@runTest
        assertTrue("terms stored without a promo: $record", record.terms == null || record.promoId != null)
        assertTrue("promo stored without a campaign: $record", record.promoId == null || record.campaignId != null)
    }

    data class StartState(
        val name: String,
        val record: PromoRecord?,
    )

    data class Write(
        val name: String,
        val action: suspend PromoCampaignRepository.() -> Unit,
    )

    data class TestCase(
        val start: StartState,
        val write: Write,
    ) {
        override fun toString(): String = "${write.name} on ${start.name}"
    }

    companion object {
        private const val NOW = 10_000L
        private const val CAMPAIGN_ID = "campaign"
        private const val OTHER_CAMPAIGN_ID = "otherCampaign"
        private val PROMO_ID: UUID = UUID.fromString("6f1c2a1e-3b4d-4c5e-8f90-1a2b3c4d5e6f")
        private val OTHER_PROMO_ID: UUID = UUID.fromString("0a9b8c7d-6e5f-4a3b-9c2d-1e0f9a8b7c6d")
        private val TERMS = PromoTerms(multiplier = 2f, expiresAtMillis = NOW + 1_000)

        private val DISMISSED_RECORD = PromoRecord(dismissedCampaignId = CAMPAIGN_ID)
        private val PENDING_RECORD = PromoRecord(
            dismissedCampaignId = CAMPAIGN_ID,
            campaignId = CAMPAIGN_ID,
            promoId = PROMO_ID.toString(),
            lastRedeemAttemptMillis = 5_000L,
        )
        private val ACTIVE_RECORD = PENDING_RECORD.copy(terms = TERMS)
        private val ENDED_RECORD = PromoRecord(campaignId = CAMPAIGN_ID)

        private val START_STATES = listOf(
            StartState("no record", null),
            StartState("dismissed", DISMISSED_RECORD),
            StartState("pending", PENDING_RECORD),
            StartState("active", ACTIVE_RECORD),
            StartState("expired", ACTIVE_RECORD.copy(terms = TERMS.copy(expiresAtMillis = NOW))),
            StartState("ended", ENDED_RECORD),
        )

        private val WRITES = listOf(
            Write("dismiss campaign") { dismissCampaign(CAMPAIGN_ID) },
            Write("dismiss other campaign") { dismissCampaign(OTHER_CAMPAIGN_ID) },
            Write("initialize promo") { initializePromo(CAMPAIGN_ID, PROMO_ID) },
            Write("initialize promo for other campaign") { initializePromo(OTHER_CAMPAIGN_ID, OTHER_PROMO_ID) },
            Write("start promo") { startPromo(PROMO_ID, TERMS) },
            Write("start other promo") { startPromo(OTHER_PROMO_ID, TERMS) },
            Write("record redeem attempt") { recordRedeemAttempt(PROMO_ID) },
            Write("invalidate promo") { invalidatePromo(PROMO_ID) },
            Write("invalidate other promo") { invalidatePromo(OTHER_PROMO_ID) },
            Write("end promo") { endPromo(checkExpiry = false) },
            Write("end promo if expired") { endPromo(checkExpiry = true) },
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters(): List<TestCase> = START_STATES.flatMap { start -> WRITES.map { write -> TestCase(start, write) } }
    }
}
