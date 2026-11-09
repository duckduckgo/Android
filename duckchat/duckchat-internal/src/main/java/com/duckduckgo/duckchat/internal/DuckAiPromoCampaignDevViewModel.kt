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

package com.duckduckgo.duckchat.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.promo.data.PromoCampaignRepository
import com.duckduckgo.duckchat.impl.promo.data.PromoRecord
import com.duckduckgo.duckchat.impl.promo.data.PromoRecordCorruptedException
import com.duckduckgo.duckchat.impl.promo.data.PromoRecordStorageException
import com.duckduckgo.duckchat.impl.promo.data.PromoTerms
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@ContributesViewModel(ActivityScope::class)
class DuckAiPromoCampaignDevViewModel @Inject constructor(
    private val promoCampaignRepository: PromoCampaignRepository,
    private val currentTimeProvider: CurrentTimeProvider,
) : ViewModel() {
    data class ViewState(
        val promoRecord: PromoRecord? = null,
        val isPromoRecordCorrupted: Boolean = false,
    )

    sealed class Command {
        data class ShowMessage(val messageResId: Int) : Command()
    }

    val viewState: StateFlow<ViewState> = promoCampaignRepository.promoRecord
        .map { promoRecord -> ViewState(promoRecord = promoRecord) }
        .catch { cause ->
            if (cause !is PromoRecordCorruptedException) throw cause
            emit(ViewState(isPromoRecordCorrupted = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    fun onSeedPendingPromoClicked(campaignId: String) {
        updatePromoRecordForCampaign(campaignId) { trimmedCampaignId ->
            initializePromo(trimmedCampaignId)
            R.string.devSettingsDuckAiPromoCampaignPendingPromoSeeded
        }
    }

    fun onSeedExpiredPromoClicked(campaignId: String) {
        updatePromoRecordForCampaign(campaignId) { trimmedCampaignId ->
            val promoId = initializePromo(trimmedCampaignId)
            promoCampaignRepository.startPromo(promoId, devPromoTerms(expiresAtMillis = currentTimeProvider.currentTimeMillis()))
            R.string.devSettingsDuckAiPromoCampaignExpiredPromoSeeded
        }
    }

    fun onDismissOfferClicked(campaignId: String) {
        updatePromoRecordForCampaign(campaignId) { trimmedCampaignId ->
            promoCampaignRepository.dismissCampaign(trimmedCampaignId)
            // Dismissing has no effect once the promo has ended
            if (promoCampaignRepository.getPromoRecord()?.dismissedCampaignId == trimmedCampaignId) {
                R.string.devSettingsDuckAiPromoCampaignOfferDismissed
            } else {
                R.string.devSettingsDuckAiPromoCampaignOfferNotDismissed
            }
        }
    }

    fun onActivatePendingPromoClicked() {
        updatePromoRecord {
            val promoId = promoCampaignRepository.getPromoRecord()?.promoId
            if (promoId == null) {
                R.string.devSettingsDuckAiPromoCampaignNoPromoStored
            } else {
                promoCampaignRepository.startPromo(
                    UUID.fromString(promoId),
                    devPromoTerms(expiresAtMillis = currentTimeProvider.currentTimeMillis() + ACTIVE_PROMO_DURATION.inWholeMilliseconds),
                )
                R.string.devSettingsDuckAiPromoCampaignPromoActivated
            }
        }
    }

    fun onEndPromoClicked() {
        updatePromoRecord {
            if (!isPromoStored()) {
                R.string.devSettingsDuckAiPromoCampaignNoPromoStored
            } else {
                promoCampaignRepository.endPromo(checkExpiry = true)
                // Ending checks expiry, so the promo is only gone if its terms had expired
                if (isPromoStored()) R.string.devSettingsDuckAiPromoCampaignPromoNotExpired else R.string.devSettingsDuckAiPromoCampaignPromoEnded
            }
        }
    }

    fun onForceEndPromoClicked() {
        updatePromoRecord {
            if (!isPromoStored()) {
                R.string.devSettingsDuckAiPromoCampaignNoPromoStored
            } else {
                promoCampaignRepository.endPromo(checkExpiry = false)
                R.string.devSettingsDuckAiPromoCampaignPromoEnded
            }
        }
    }

    fun onInvalidatePromoClicked() {
        updatePromoRecord {
            val promoId = promoCampaignRepository.getPromoRecord()?.promoId
            if (promoId == null) {
                R.string.devSettingsDuckAiPromoCampaignNoPromoStored
            } else {
                promoCampaignRepository.invalidatePromo(UUID.fromString(promoId))
                R.string.devSettingsDuckAiPromoCampaignPromoInvalidated
            }
        }
    }

    fun onClearPromoRecordClicked() {
        updatePromoRecord {
            promoCampaignRepository.clear()
            R.string.devSettingsDuckAiPromoCampaignRecordCleared
        }
    }

    private suspend fun isPromoStored(): Boolean = promoCampaignRepository.getPromoRecord()?.promoId != null

    private suspend fun initializePromo(campaignId: String): UUID {
        val promoId = UUID.randomUUID()
        // Initializing has no effect once a campaign is stored, and an ended record can only be modified by clearing it
        promoCampaignRepository.clear()
        promoCampaignRepository.initializePromo(campaignId, promoId)
        return promoId
    }

    private fun devPromoTerms(expiresAtMillis: Long): PromoTerms = PromoTerms(multiplier = DEV_PROMO_MULTIPLIER, expiresAtMillis = expiresAtMillis)

    private fun updatePromoRecordForCampaign(
        campaignId: String,
        updateAndGetMessageResId: suspend (trimmedCampaignId: String) -> Int,
    ) {
        updatePromoRecord {
            if (campaignId.isBlank()) R.string.devSettingsDuckAiPromoCampaignCampaignIdRequired else updateAndGetMessageResId(campaignId.trim())
        }
    }

    private fun updatePromoRecord(updateAndGetMessageResId: suspend () -> Int) {
        viewModelScope.launch {
            val messageResId = try {
                updateAndGetMessageResId()
            } catch (_: PromoRecordStorageException) {
                R.string.devSettingsDuckAiPromoCampaignStorageFailed
            } catch (_: PromoRecordCorruptedException) {
                R.string.devSettingsDuckAiPromoCampaignRecordCorruptedFailed
            }
            _commands.send(Command.ShowMessage(messageResId))
        }
    }

    private companion object {
        val ACTIVE_PROMO_DURATION = 5.minutes
        const val DEV_PROMO_MULTIPLIER = 2f
    }
}
