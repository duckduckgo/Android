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

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.duckchat.impl.promo.data.PromoCampaignRepository
import com.duckduckgo.duckchat.impl.promo.data.PromoRecord
import com.duckduckgo.duckchat.impl.promo.data.PromoRecordCorruptedException
import com.duckduckgo.duckchat.impl.promo.data.PromoRecordStorageException
import com.duckduckgo.duckchat.impl.promo.data.PromoTerms
import com.duckduckgo.duckchat.internal.DuckAiPromoCampaignDevViewModel.Command
import com.duckduckgo.duckchat.internal.DuckAiPromoCampaignDevViewModel.ViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

class DuckAiPromoCampaignDevViewModelTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val promoRecord = MutableStateFlow<PromoRecord?>(null)
    private val promoCampaignRepository: PromoCampaignRepository = mock<PromoCampaignRepository>().also {
        whenever(it.promoRecord).thenReturn(promoRecord)
    }
    private val currentTimeProvider: CurrentTimeProvider = mock<CurrentTimeProvider>().also {
        whenever(it.currentTimeMillis()).thenReturn(NOW)
    }

    private val testee by lazy { DuckAiPromoCampaignDevViewModel(promoCampaignRepository, currentTimeProvider) }

    @Test
    fun whenNoRecordStoredThenStateHasNoRecord() = runTest {
        testee.viewState.test {
            assertEquals(ViewState(), expectMostRecentItem())
        }
    }

    @Test
    fun whenRecordStoredThenStateCarriesIt() = runTest {
        promoRecord.value = ACTIVE_RECORD

        testee.viewState.test {
            assertEquals(ViewState(promoRecord = ACTIVE_RECORD), expectMostRecentItem())
        }
    }

    @Test
    fun whenRecordChangesThenStateUpdates() = runTest {
        testee.viewState.test {
            assertEquals(ViewState(), expectMostRecentItem())

            promoRecord.value = ACTIVE_RECORD

            assertEquals(ViewState(promoRecord = ACTIVE_RECORD), awaitItem())
        }
    }

    @Test
    fun whenRecordCorruptedThenStateFlagsIt() = runTest {
        whenever(promoCampaignRepository.promoRecord).thenReturn(
            flow { throw PromoRecordCorruptedException(IllegalStateException("corrupted")) },
        )

        testee.viewState.test {
            assertEquals(ViewState(isPromoRecordCorrupted = true), expectMostRecentItem())
        }
    }

    @Test
    fun whenSeedPendingPromoClickedThenRecordIsReplacedWithPendingPromoAndMessageIsShown() = runTest {
        testee.commands.test {
            testee.onSeedPendingPromoClicked(CAMPAIGN_ID)

            val inOrder = inOrder(promoCampaignRepository)
            inOrder.verify(promoCampaignRepository).clear()
            inOrder.verify(promoCampaignRepository).initializePromo(eq(CAMPAIGN_ID), any())
            verify(promoCampaignRepository, never()).startPromo(any(), any())
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignPendingPromoSeeded), awaitItem())
        }
    }

    @Test
    fun whenSeedExpiredPromoClickedThenRecordIsReplacedWithBoostExpiringNowAndMessageIsShown() = runTest {
        testee.commands.test {
            testee.onSeedExpiredPromoClicked(CAMPAIGN_ID)

            assertEquals(NOW, verifyPromoSeeded().expiresAtMillis)
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignExpiredPromoSeeded), awaitItem())
        }
    }

    @Test
    fun whenSeedPendingPromoClickedAndCampaignIdIsBlankThenNothingIsStoredAndMessageIsShown() = runTest {
        testee.commands.test {
            testee.onSeedPendingPromoClicked(" ")

            verifyNothingSeeded()
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignCampaignIdRequired), awaitItem())
        }
    }

    @Test
    fun whenSeedExpiredPromoClickedAndCampaignIdIsBlankThenNothingIsStoredAndMessageIsShown() = runTest {
        testee.commands.test {
            testee.onSeedExpiredPromoClicked(" ")

            verifyNothingSeeded()
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignCampaignIdRequired), awaitItem())
        }
    }

    @Test
    fun whenSeedPendingPromoClickedAndCampaignIdHasSurroundingWhitespaceThenTrimmedCampaignIdIsStored() = runTest {
        testee.commands.test {
            testee.onSeedPendingPromoClicked(" $CAMPAIGN_ID ")

            verify(promoCampaignRepository).initializePromo(eq(CAMPAIGN_ID), any())
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignPendingPromoSeeded), awaitItem())
        }
    }

    @Test
    fun whenSeedExpiredPromoClickedAndCampaignIdHasSurroundingWhitespaceThenTrimmedCampaignIdIsStored() = runTest {
        testee.commands.test {
            testee.onSeedExpiredPromoClicked(" $CAMPAIGN_ID ")

            verifyPromoSeeded()
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignExpiredPromoSeeded), awaitItem())
        }
    }

    @Test
    fun whenDismissOfferClickedThenCampaignIsDismissedAndMessageIsShown() = runTest {
        whenever(promoCampaignRepository.getPromoRecord()).thenReturn(ACTIVE_RECORD.copy(dismissedCampaignId = CAMPAIGN_ID))

        testee.commands.test {
            testee.onDismissOfferClicked(CAMPAIGN_ID)

            verify(promoCampaignRepository).dismissCampaign(CAMPAIGN_ID)
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignOfferDismissed), awaitItem())
        }
    }

    @Test
    fun whenDismissOfferClickedAndPromoHasEndedThenNotDismissedMessageIsShown() = runTest {
        whenever(promoCampaignRepository.getPromoRecord()).thenReturn(ENDED_RECORD)

        testee.commands.test {
            testee.onDismissOfferClicked(CAMPAIGN_ID)

            verify(promoCampaignRepository).dismissCampaign(CAMPAIGN_ID)
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignOfferNotDismissed), awaitItem())
        }
    }

    @Test
    fun whenDismissOfferClickedAndCampaignIdIsBlankThenNothingIsDismissedAndMessageIsShown() = runTest {
        testee.commands.test {
            testee.onDismissOfferClicked(" ")

            verify(promoCampaignRepository, never()).dismissCampaign(any())
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignCampaignIdRequired), awaitItem())
        }
    }

    @Test
    fun whenDismissOfferClickedAndCampaignIdHasSurroundingWhitespaceThenTrimmedCampaignIdIsDismissed() = runTest {
        whenever(promoCampaignRepository.getPromoRecord()).thenReturn(ACTIVE_RECORD.copy(dismissedCampaignId = CAMPAIGN_ID))

        testee.commands.test {
            testee.onDismissOfferClicked(" $CAMPAIGN_ID ")

            verify(promoCampaignRepository).dismissCampaign(CAMPAIGN_ID)
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignOfferDismissed), awaitItem())
        }
    }

    @Test
    fun whenActivatePendingPromoClickedThenStoredPromoIsActivatedForFiveMinutesAndMessageIsShown() = runTest {
        whenever(promoCampaignRepository.getPromoRecord()).thenReturn(PENDING_RECORD)

        testee.commands.test {
            testee.onActivatePendingPromoClicked()

            verify(promoCampaignRepository).startPromo(PROMO_ID, PromoTerms(multiplier = 2f, expiresAtMillis = NOW + 5.minutes.inWholeMilliseconds))
            verifyNothingSeeded()
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignPromoActivated), awaitItem())
        }
    }

    @Test
    fun whenActivatePendingPromoClickedAndNoPromoIsStoredThenNothingIsActivatedAndMessageIsShown() = runTest {
        whenever(promoCampaignRepository.getPromoRecord()).thenReturn(ENDED_RECORD)

        testee.commands.test {
            testee.onActivatePendingPromoClicked()

            verify(promoCampaignRepository, never()).startPromo(any(), any())
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignNoPromoStored), awaitItem())
        }
    }

    @Test
    fun whenEndPromoClickedAndPromoHasExpiredThenPromoIsEndedAndMessageIsShown() = runTest {
        whenever(promoCampaignRepository.getPromoRecord()).thenReturn(EXPIRED_RECORD, ENDED_RECORD)

        testee.commands.test {
            testee.onEndPromoClicked()

            verify(promoCampaignRepository).endPromo(checkExpiry = true)
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignPromoEnded), awaitItem())
        }
    }

    @Test
    fun whenEndPromoClickedAndPromoHasNotExpiredThenNotExpiredMessageIsShown() = runTest {
        whenever(promoCampaignRepository.getPromoRecord()).thenReturn(ACTIVE_RECORD)

        testee.commands.test {
            testee.onEndPromoClicked()

            verify(promoCampaignRepository).endPromo(checkExpiry = true)
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignPromoNotExpired), awaitItem())
        }
    }

    @Test
    fun whenEndPromoClickedAndNoPromoIsStoredThenNothingIsEndedAndMessageIsShown() = runTest {
        whenever(promoCampaignRepository.getPromoRecord()).thenReturn(ENDED_RECORD)

        testee.commands.test {
            testee.onEndPromoClicked()

            verify(promoCampaignRepository, never()).endPromo(any())
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignNoPromoStored), awaitItem())
        }
    }

    @Test
    fun whenForceEndPromoClickedThenPromoIsEndedWithoutCheckingExpiryAndMessageIsShown() = runTest {
        whenever(promoCampaignRepository.getPromoRecord()).thenReturn(ACTIVE_RECORD)

        testee.commands.test {
            testee.onForceEndPromoClicked()

            verify(promoCampaignRepository).endPromo(checkExpiry = false)
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignPromoEnded), awaitItem())
        }
    }

    @Test
    fun whenForceEndPromoClickedAndNoPromoIsStoredThenNothingIsEndedAndMessageIsShown() = runTest {
        whenever(promoCampaignRepository.getPromoRecord()).thenReturn(ENDED_RECORD)

        testee.commands.test {
            testee.onForceEndPromoClicked()

            verify(promoCampaignRepository, never()).endPromo(any())
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignNoPromoStored), awaitItem())
        }
    }

    @Test
    fun whenInvalidatePromoClickedThenStoredPromoIsInvalidatedAndMessageIsShown() = runTest {
        whenever(promoCampaignRepository.getPromoRecord()).thenReturn(ACTIVE_RECORD)

        testee.commands.test {
            testee.onInvalidatePromoClicked()

            verify(promoCampaignRepository).invalidatePromo(PROMO_ID)
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignPromoInvalidated), awaitItem())
        }
    }

    @Test
    fun whenInvalidatePromoClickedAndNoPromoIsStoredThenNothingIsInvalidatedAndMessageIsShown() = runTest {
        whenever(promoCampaignRepository.getPromoRecord()).thenReturn(ENDED_RECORD)

        testee.commands.test {
            testee.onInvalidatePromoClicked()

            verify(promoCampaignRepository, never()).invalidatePromo(any())
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignNoPromoStored), awaitItem())
        }
    }

    @Test
    fun whenClearClickedThenRecordIsClearedAndMessageIsShown() = runTest {
        testee.commands.test {
            testee.onClearPromoRecordClicked()

            verify(promoCampaignRepository).clear()
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignRecordCleared), awaitItem())
        }
    }

    @Test
    fun whenRecordCannotBeReadOrWrittenThenStorageFailureMessageIsShown() = runTest {
        whenever(promoCampaignRepository.clear()).thenAnswer { throw PromoRecordStorageException(IOException("disk")) }

        testee.commands.test {
            testee.onSeedExpiredPromoClicked(CAMPAIGN_ID)

            verify(promoCampaignRepository, never()).startPromo(any(), any())
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignStorageFailed), awaitItem())
        }
    }

    @Test
    fun whenRecordIsCorruptedThenCorruptedFailureMessageIsShown() = runTest {
        whenever(promoCampaignRepository.getPromoRecord()).thenAnswer { throw PromoRecordCorruptedException(IllegalStateException("corrupted")) }

        testee.commands.test {
            testee.onEndPromoClicked()

            verify(promoCampaignRepository, never()).endPromo(any())
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiPromoCampaignRecordCorruptedFailed), awaitItem())
        }
    }

    private suspend fun verifyNothingSeeded() {
        verify(promoCampaignRepository, never()).clear()
        verify(promoCampaignRepository, never()).initializePromo(any(), any())
    }

    private suspend fun verifyPromoSeeded(): PromoTerms {
        val promoId = argumentCaptor<UUID>()
        val terms = argumentCaptor<PromoTerms>()
        val inOrder = inOrder(promoCampaignRepository)
        inOrder.verify(promoCampaignRepository).clear()
        inOrder.verify(promoCampaignRepository).initializePromo(eq(CAMPAIGN_ID), promoId.capture())
        inOrder.verify(promoCampaignRepository).startPromo(promoId.capture(), terms.capture())
        assertEquals(promoId.firstValue, promoId.secondValue)
        return terms.firstValue
    }

    private companion object {
        const val NOW = 1_000_000L
        const val CAMPAIGN_ID = "campaign"
        val PROMO_ID: UUID = UUID.fromString("6f1c2a1e-3b4d-4c5e-8f90-1a2b3c4d5e6f")
        val PENDING_RECORD = PromoRecord(campaignId = CAMPAIGN_ID, promoId = PROMO_ID.toString())
        val ACTIVE_RECORD = PENDING_RECORD.copy(terms = PromoTerms(multiplier = 2f, expiresAtMillis = NOW + 1_000))
        val EXPIRED_RECORD = ACTIVE_RECORD.copy(terms = PromoTerms(multiplier = 2f, expiresAtMillis = NOW))
        val ENDED_RECORD = PromoRecord(campaignId = CAMPAIGN_ID)
    }
}
