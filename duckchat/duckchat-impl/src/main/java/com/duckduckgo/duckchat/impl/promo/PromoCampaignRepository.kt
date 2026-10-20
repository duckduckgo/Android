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

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.transformLatest
import java.util.UUID
import javax.inject.Inject
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Stores the promo record and enforces its rules. Each write is a single atomic update that leaves the record unchanged
 * when its precondition isn't met. An ended record can only be modified by [clear].
 */
interface PromoCampaignRepository {
    /**
     * Emits the stored promo record, or null if none is stored, each time it changes.
     * Retries I/O failures. Fails with [PromoRecordCorruptedException] if the record can't be decoded.
     */
    val promoRecord: Flow<PromoRecord?>

    /**
     * Emits whether a promo is active, meaning its terms are stored and haven't expired, each time it changes.
     * Emits false once the terms expire. Retries I/O failures. Fails with [PromoRecordCorruptedException] if the record can't be decoded.
     */
    val isPromoActive: Flow<Boolean>

    /**
     * @return the stored promo record, or null if none is stored.
     *
     * @throws [PromoRecordCorruptedException] if the stored record can't be decoded.
     * @throws [PromoRecordStorageException] if the stored record can't be read.
     */
    suspend fun getPromoRecord(): PromoRecord?

    /**
     * Stores [campaignId] as the dismissed campaign.
     *
     * @throws [PromoRecordCorruptedException] if the stored record can't be decoded.
     * @throws [PromoRecordStorageException] if the stored record can't be read or written.
     */
    suspend fun dismissCampaign(campaignId: String)

    /**
     * Initializes the record with [campaignId] and [promoId]. No effect if a campaign ID is already set.
     *
     * @return the stored promo record.
     *
     * @throws [PromoRecordCorruptedException] if the stored record can't be decoded.
     * @throws [PromoRecordStorageException] if the stored record can't be read or written.
     */
    suspend fun initializePromo(
        campaignId: String,
        promoId: UUID,
    ): PromoRecord

    /**
     * Stores [terms], replacing any stored ones, and the redeem attempt time.
     * No effect unless [promoId] is the stored one.
     *
     * @throws [PromoRecordCorruptedException] if the stored record can't be decoded.
     * @throws [PromoRecordStorageException] if the stored record can't be read or written.
     */
    suspend fun startPromo(
        promoId: UUID,
        terms: PromoTerms,
    )

    /**
     * Stores the redeem attempt time. No effect unless [promoId] is the stored one.
     *
     * @throws [PromoRecordCorruptedException] if the stored record can't be decoded.
     * @throws [PromoRecordStorageException] if the stored record can't be read or written.
     */
    suspend fun recordRedeemAttempt(promoId: UUID)

    /**
     * Ends the promo if it had been redeemed. Otherwise, leaves it in an uninitialized state.
     * No effect unless [promoId] is the stored one.
     *
     * @throws [PromoRecordCorruptedException] if the stored record can't be decoded.
     * @throws [PromoRecordStorageException] if the stored record can't be read or written.
     */
    suspend fun invalidatePromo(promoId: UUID)

    /**
     * Clears everything but the campaign ID. No effect unless a promo is stored.
     * Also, if [checkExpiry] is true, it's only cleared when its terms have expired.
     *
     * @throws [PromoRecordCorruptedException] if the stored record can't be decoded.
     * @throws [PromoRecordStorageException] if the stored record can't be read or written.
     */
    suspend fun endPromo(checkExpiry: Boolean)

    /**
     * Removes the stored record, whatever its state.
     *
     * @throws PromoRecordStorageException if the record can't be written.
     */
    suspend fun clear()
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealPromoCampaignRepository @Inject constructor(
    private val dataStore: PromoCampaignDataStore,
    private val currentTimeProvider: CurrentTimeProvider,
) : PromoCampaignRepository {
    override val promoRecord: Flow<PromoRecord?> = dataStore.promoRecord
        .retryWhen { cause, attempt ->
            if (cause is PromoRecordStorageException) {
                delay(retryDelay(attempt))
                true
            } else {
                false
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val isPromoActive: Flow<Boolean> = promoRecord
        .transformLatest { record ->
            val remainingMillis = record?.remainingActiveMillis() ?: 0
            if (remainingMillis > 0) {
                emit(true)
                delay(remainingMillis.milliseconds)
            }
            emit(false)
        }
        .distinctUntilChanged()

    override suspend fun getPromoRecord(): PromoRecord? = dataStore.getPromoRecord()

    override suspend fun dismissCampaign(campaignId: String) {
        dataStore.updatePromoRecord { record ->
            if (record == null || !record.hasEnded()) {
                (record ?: PromoRecord()).copy(dismissedCampaignId = campaignId)
            } else {
                record
            }
        }
    }

    override suspend fun initializePromo(
        campaignId: String,
        promoId: UUID,
    ): PromoRecord {
        val storedRecord = dataStore.updatePromoRecord { record ->
            if (record?.campaignId == null) {
                (record ?: PromoRecord()).markAsPending(campaignId, promoId)
            } else {
                record
            }
        }
        return checkNotNull(storedRecord)
    }

    override suspend fun startPromo(
        promoId: UUID,
        terms: PromoTerms,
    ) {
        updatePromo(promoId) { record ->
            record.markAsActive(terms)
        }
    }

    override suspend fun recordRedeemAttempt(promoId: UUID) {
        updatePromo(promoId) { record ->
            record.copy(lastRedeemAttemptMillis = currentTimeProvider.currentTimeMillis())
        }
    }

    override suspend fun invalidatePromo(promoId: UUID) {
        updatePromo(promoId) { record ->
            if (record.terms != null) {
                record.markAsEnded()
            } else {
                record.markAsUninitialized()
            }
        }
    }

    override suspend fun endPromo(checkExpiry: Boolean) {
        dataStore.updatePromoRecord { record ->
            if (record?.promoId != null && (!checkExpiry || record.hasExpired())) {
                record.markAsEnded()
            } else {
                record
            }
        }
    }

    override suspend fun clear() {
        dataStore.clear()
    }

    private suspend fun updatePromo(
        promoId: UUID,
        transform: (PromoRecord) -> PromoRecord?,
    ) {
        dataStore.updatePromoRecord { record ->
            if (record?.promoId == promoId.toString()) transform(record) else record
        }
    }

    private fun PromoRecord.hasExpired(): Boolean = terms != null && terms.expiresAtMillis <= currentTimeProvider.currentTimeMillis()

    private fun PromoRecord.hasEnded(): Boolean = campaignId != null && promoId == null

    private fun PromoRecord.markAsPending(
        campaignId: String,
        promoId: UUID,
    ): PromoRecord = copy(campaignId = campaignId, promoId = promoId.toString())

    private fun PromoRecord.markAsActive(terms: PromoTerms): PromoRecord =
        copy(terms = terms, lastRedeemAttemptMillis = currentTimeProvider.currentTimeMillis())

    private fun PromoRecord.markAsEnded(): PromoRecord = PromoRecord(campaignId = campaignId)

    private fun PromoRecord.markAsUninitialized(): PromoRecord? = when (dismissedCampaignId) {
        null -> null
        else -> PromoRecord(dismissedCampaignId = dismissedCampaignId)
    }

    private fun retryDelay(attempt: Long): Duration = (FIRST_RETRY_DELAY * 2.0.pow(attempt.toDouble())).coerceAtMost(MAX_RETRY_DELAY)

    private fun PromoRecord.remainingActiveMillis(): Long {
        return if (campaignId != null && promoId != null && terms != null) {
            terms.expiresAtMillis - currentTimeProvider.currentTimeMillis()
        } else {
            0
        }
    }

    private companion object {
        val FIRST_RETRY_DELAY = 1.seconds
        val MAX_RETRY_DELAY = 1.minutes
    }
}
