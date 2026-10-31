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

package com.duckduckgo.duckchat.impl.promo.messaging

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.promo.PromoNewInstallChecker
import com.duckduckgo.duckchat.impl.promo.data.PromoCampaignRepository
import com.duckduckgo.duckchat.impl.promo.data.PromoRecord
import com.duckduckgo.duckchat.impl.promo.data.PromoRecordCorruptedException
import com.duckduckgo.duckchat.impl.promo.data.PromoRecordStorageException
import com.duckduckgo.duckchat.impl.promo.data.PromoTerms
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RealPromoCampaignJSHelperTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val promoRecordFlow = MutableStateFlow<PromoRecord?>(PENDING_RECORD)
    private val mockPromoCampaignRepository: PromoCampaignRepository = mock {
        on { promoRecord } doReturn promoRecordFlow
    }
    private val mockPromoNewInstallChecker: PromoNewInstallChecker = mock {
        onBlocking { isNewInstall(any()) } doReturn false
    }
    private val mockDuckChat: DuckChat = mock()
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)

    private val testee by lazy {
        RealPromoCampaignJSHelper(
            promoCampaignRepository = mockPromoCampaignRepository,
            promoNewInstallChecker = mockPromoNewInstallChecker,
            duckChat = mockDuckChat,
            duckChatFeature = duckChatFeature,
            moshi = Moshi.Builder().build(),
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    init {
        duckChatFeature.duckAiPromoCampaigns().setRawStoredState(Toggle.State(enable = true))
    }

    //region getAIChatBonusRecord
    @Test
    fun `when promo record requested, then responds with the stored record and install facts`() = runTest {
        givenStoredRecord(ACTIVE_RECORD.copy(dismissedCampaignId = CAMPAIGN_NAME))
        whenever(mockPromoNewInstallChecker.isNewInstall(NEW_INSTALL_WINDOW_DAYS))
            .thenReturn(true)
        whenever(mockDuckChat.isEnabled())
            .thenReturn(true)

        val response = process(METHOD_GET_BONUS_RECORD)!!

        assertEquals(JsCallbackData(response.params, FEATURE_NAME, METHOD_GET_BONUS_RECORD, REQUEST_ID), response)
        val record = response.params.getJSONObject("record")
        assertEquals(CAMPAIGN_NAME, record.getString("dismissedCampaignName"))
        assertEquals(CAMPAIGN_NAME, record.getString("campaignName"))
        assertEquals(PROMO_ID.toString(), record.getString("bonusId"))
        assertEquals(MULTIPLIER.toDouble(), record.getJSONObject("terms").getDouble("multiplier"), 0.0)
        assertEquals(EXPIRES_AT, record.getJSONObject("terms").getLong("expiresAt"))
        assertEquals(LAST_REDEEM_ATTEMPT_AT, record.getLong("lastRedeemAttemptAt"))
        assertTrue(response.params.getBoolean("isNewInstall"))
        assertTrue(response.params.getBoolean("aiChatEnabled"))
    }

    @Test
    fun `when promo record requested and multiplier is fractional, then it keeps its decimal representation`() = runTest {
        givenStoredRecord(ACTIVE_RECORD.copy(terms = PromoTerms(multiplier = 1.1f, expiresAtMillis = EXPIRES_AT)))

        val response = process(METHOD_GET_BONUS_RECORD)!!

        val terms = response.params.getJSONObject("record").getJSONObject("terms")
        assertEquals(1.1, terms.getDouble("multiplier"), 0.0)
    }

    @Test
    fun `when promo record requested and some fields are unset, then they're left out`() = runTest {
        givenStoredRecord(PromoRecord(dismissedCampaignId = CAMPAIGN_NAME))

        val response = process(METHOD_GET_BONUS_RECORD)!!

        val record = response.params.getJSONObject("record")
        assertEquals(listOf("dismissedCampaignName"), record.keys().asSequence().toList())
    }

    @Test
    fun `when promo record requested and none is stored, then responds with a null record`() = runTest {
        givenStoredRecord(null)
        whenever(mockPromoNewInstallChecker.isNewInstall(NEW_INSTALL_WINDOW_DAYS))
            .thenReturn(false)
        whenever(mockDuckChat.isEnabled())
            .thenReturn(false)

        val response = process(METHOD_GET_BONUS_RECORD)!!

        assertTrue(response.params.isNull("record"))
        assertTrue(response.params.has("record"))
        assertFalse(response.params.getBoolean("isNewInstall"))
        assertFalse(response.params.getBoolean("aiChatEnabled"))
    }

    @Test
    fun `when promo record requested without a request ID, then no response and record untouched`() = runTest {
        val response = process(METHOD_GET_BONUS_RECORD, requestId = null)

        assertNull(response)
        verify(mockPromoCampaignRepository, never()).endPromo(any())
        verify(mockPromoCampaignRepository, never()).getPromoRecord()
    }

    @Test
    fun `when promo record requested and it can't be read, then no response`() = runTest {
        whenever(mockPromoCampaignRepository.getPromoRecord())
            .thenAnswer { throw PromoRecordStorageException(IOException()) }

        assertNull(process(METHOD_GET_BONUS_RECORD))
    }

    @Test
    fun `when promo record requested and it can't be decoded, then no response`() = runTest {
        whenever(mockPromoCampaignRepository.getPromoRecord())
            .thenAnswer { throw PromoRecordCorruptedException(JsonDataException()) }

        assertNull(process(METHOD_GET_BONUS_RECORD))
    }
    //endregion

    //region getAIChatNewBonusRecord
    @Test
    fun `when new promo record requested, then initializes the promo and responds with the stored record`() = runTest {
        whenever(mockPromoCampaignRepository.initializePromo(eq(CAMPAIGN_NAME), any()))
            .thenReturn(PENDING_RECORD)

        val response = process(METHOD_GET_NEW_BONUS_RECORD, JSONObject().put("campaignName", CAMPAIGN_NAME))!!

        assertEquals(METHOD_GET_NEW_BONUS_RECORD, response.method)
        assertEquals(REQUEST_ID, response.id)
        val record = response.params.getJSONObject("record")
        assertEquals(CAMPAIGN_NAME, record.getString("campaignName"))
        assertEquals(PROMO_ID.toString(), record.getString("bonusId"))
    }

    @Test
    fun `when new promo records requested, then each initialization gets a different promo ID`() = runTest {
        whenever(mockPromoCampaignRepository.initializePromo(eq(CAMPAIGN_NAME), any()))
            .thenReturn(PENDING_RECORD)

        process(METHOD_GET_NEW_BONUS_RECORD, JSONObject().put("campaignName", CAMPAIGN_NAME))
        process(METHOD_GET_NEW_BONUS_RECORD, JSONObject().put("campaignName", CAMPAIGN_NAME))

        val promoIds = argumentCaptor<UUID>()
        verify(mockPromoCampaignRepository, times(2)).initializePromo(eq(CAMPAIGN_NAME), promoIds.capture())
        assertEquals(2, promoIds.allValues.toSet().size)
    }

    @Test
    fun `when new promo record requested without a campaign name, then no response and promo not initialized`() = runTest {
        listOf(JSONObject(), JSONObject().put("campaignName", ""), JSONObject().put("campaignName", JSONObject()))
            .forEach { params ->
                assertNull(process(METHOD_GET_NEW_BONUS_RECORD, params))
            }

        verify(mockPromoCampaignRepository, never()).initializePromo(any(), any())
    }

    @Test
    fun `when new promo record requested without a request ID, then no response and promo not initialized`() = runTest {
        val response = process(METHOD_GET_NEW_BONUS_RECORD, JSONObject().put("campaignName", CAMPAIGN_NAME), requestId = null)

        assertNull(response)
        verify(mockPromoCampaignRepository, never()).initializePromo(any(), any())
    }

    @Test
    fun `when new promo record requested and it can't be written, then no response`() = runTest {
        whenever(mockPromoCampaignRepository.initializePromo(any(), any()))
            .thenAnswer { throw PromoRecordStorageException(IOException()) }

        assertNull(process(METHOD_GET_NEW_BONUS_RECORD, JSONObject().put("campaignName", CAMPAIGN_NAME)))
    }
    //endregion

    //region aiChatBonusRedeemOutcome
    @Test
    fun `when redeem succeeded, then the promo is started with the granted terms`() = runTest {
        val response = process(METHOD_REDEEM_OUTCOME, successOutcome())

        assertNull(response)
        verify(mockPromoCampaignRepository).startPromo(PROMO_ID, PromoTerms(MULTIPLIER, EXPIRES_AT))
    }

    @Test
    fun `when redeem succeeded with an invalid grant, then the record is untouched`() = runTest {
        listOf(
            null,
            JSONObject().put("expiresAt", EXPIRES_AT),
            JSONObject().put("multiplier", MULTIPLIER),
            JSONObject().put("multiplier", "two").put("expiresAt", EXPIRES_AT),
            JSONObject().put("multiplier", Double.MAX_VALUE).put("expiresAt", EXPIRES_AT),
        ).forEach { grant ->
            process(METHOD_REDEEM_OUTCOME, successOutcome().put("grant", grant))
        }

        verifyRecordUntouched()
    }

    @Test
    fun `when redeem failed definitively, then the promo is invalidated`() = runTest {
        process(METHOD_REDEEM_OUTCOME, failureOutcome(definitive = true))

        verify(mockPromoCampaignRepository).invalidatePromo(PROMO_ID)
    }

    @Test
    fun `when redeem failed non-definitively, then only the attempt is recorded`() = runTest {
        process(METHOD_REDEEM_OUTCOME, failureOutcome(definitive = false))

        verify(mockPromoCampaignRepository).recordRedeemAttempt(PROMO_ID)
        verify(mockPromoCampaignRepository, never()).invalidatePromo(any())
    }

    @Test
    fun `when redeem failed without a valid definitive flag, then the record is untouched`() = runTest {
        process(METHOD_REDEEM_OUTCOME, failureOutcome(definitive = true).apply { remove("definitive") })
        process(METHOD_REDEEM_OUTCOME, failureOutcome(definitive = true).put("definitive", "true"))

        verifyRecordUntouched()
    }

    @Test
    fun `when redeem outcome has an invalid promo ID or result, then the record is untouched`() = runTest {
        process(METHOD_REDEEM_OUTCOME, successOutcome().put("bonusId", "not-a-uuid"))
        process(METHOD_REDEEM_OUTCOME, successOutcome().put("bonusId", JSONObject.NULL))
        process(METHOD_REDEEM_OUTCOME, successOutcome().put("result", "unknown"))
        process(METHOD_REDEEM_OUTCOME, successOutcome().apply { remove("result") })

        verifyRecordUntouched()
    }

    @Test
    fun `when redeem outcome can't be stored, then it's dropped`() = runTest {
        whenever(mockPromoCampaignRepository.startPromo(any(), any()))
            .thenAnswer { throw PromoRecordStorageException(IOException()) }

        assertNull(process(METHOD_REDEEM_OUTCOME, successOutcome()))
    }
    //endregion

    //region aiChatBonusDismiss
    @Test
    fun `when offer dismissed, then its campaign is dismissed`() = runTest {
        val response = process(METHOD_DISMISS, JSONObject().put("campaignName", CAMPAIGN_NAME))

        assertNull(response)
        verify(mockPromoCampaignRepository).dismissCampaign(CAMPAIGN_NAME)
    }

    @Test
    fun `when offer dismissed without a campaign name, then the record is untouched`() = runTest {
        process(METHOD_DISMISS, JSONObject())
        process(METHOD_DISMISS, JSONObject().put("campaignName", ""))

        verifyRecordUntouched()
    }
    //endregion

    //region aiChatBonusEnd
    @Test
    fun `when promo ended, then the promo is ended without checking expiry`() = runTest {
        val response = process(METHOD_END, JSONObject().put("reason", "optedOut"))

        assertNull(response)
        verify(mockPromoCampaignRepository).endPromo(checkExpiry = false)
    }
    //endregion

    //region Feature flag
    @Test
    fun `when promo campaigns disabled, then messages are ignored`() = runTest {
        duckChatFeature.duckAiPromoCampaigns().setRawStoredState(Toggle.State(enable = false))

        assertNull(process(METHOD_GET_BONUS_RECORD))
        assertNull(process(METHOD_GET_NEW_BONUS_RECORD, JSONObject().put("campaignName", CAMPAIGN_NAME)))
        process(METHOD_REDEEM_OUTCOME, successOutcome())
        process(METHOD_DISMISS, JSONObject().put("campaignName", CAMPAIGN_NAME))
        process(METHOD_END, JSONObject().put("reason", "optedOut"))

        verify(mockPromoCampaignRepository, never()).getPromoRecord()
        verifyRecordUntouched()
    }

    @Test
    fun `when method is unknown, then no response`() = runTest {
        assertNull(process("unknownMethod"))
    }
    //endregion

    //region submitAIChatBonusRecord
    @Test
    fun `when stored record is read or changes, then a promo record event is emitted with it`() = runTest {
        whenever(mockPromoNewInstallChecker.isNewInstall(NEW_INSTALL_WINDOW_DAYS))
            .thenReturn(true)
        whenever(mockDuckChat.isEnabled())
            .thenReturn(true)

        testee.promoRecordEvents.test {
            val event = awaitItem()
            assertEquals(FEATURE_NAME, event.featureName)
            assertEquals("submitAIChatBonusRecord", event.subscriptionName)
            assertEquals(PROMO_ID.toString(), event.params.getJSONObject("record").getString("bonusId"))
            assertTrue(event.params.getBoolean("isNewInstall"))
            assertTrue(event.params.getBoolean("aiChatEnabled"))

            promoRecordFlow.value = null

            assertTrue(awaitItem().params.isNull("record"))
        }
    }

    @Test
    fun `when stored record changes while promo campaigns disabled, then no event is emitted`() = runTest {
        duckChatFeature.duckAiPromoCampaigns().setRawStoredState(Toggle.State(enable = false))

        testee.promoRecordEvents.test {
            promoRecordFlow.value = ACTIVE_RECORD

            expectNoEvents()
        }
    }

    @Test
    fun `when stored record can't be decoded, then events stop without failing`() = runTest {
        whenever(mockPromoCampaignRepository.promoRecord)
            .thenReturn(
                flow {
                    emit(PENDING_RECORD)
                    throw PromoRecordCorruptedException(JsonDataException())
                },
            )

        testee.promoRecordEvents.test {
            assertEquals(PROMO_ID.toString(), awaitItem().params.getJSONObject("record").getString("bonusId"))
            expectNoEvents()
        }
    }
    //endregion

    private suspend fun verifyRecordUntouched() {
        verify(mockPromoCampaignRepository, never()).dismissCampaign(any())
        verify(mockPromoCampaignRepository, never()).initializePromo(any(), any())
        verify(mockPromoCampaignRepository, never()).startPromo(any(), any())
        verify(mockPromoCampaignRepository, never()).recordRedeemAttempt(any())
        verify(mockPromoCampaignRepository, never()).invalidatePromo(any())
        verify(mockPromoCampaignRepository, never()).endPromo(any())
        verify(mockPromoCampaignRepository, never()).clear()
    }

    private suspend fun givenStoredRecord(record: PromoRecord?) {
        whenever(mockPromoCampaignRepository.getPromoRecord()).thenReturn(record)
    }

    private suspend fun process(
        method: String,
        data: JSONObject? = null,
        requestId: String? = REQUEST_ID,
    ): JsCallbackData? = testee.processJsCallbackMessage(FEATURE_NAME, method, requestId, data)

    private fun successOutcome(): JSONObject = JSONObject()
        .put("bonusId", PROMO_ID.toString())
        .put("result", "success")
        .put(
            "grant",
            JSONObject()
                .put("campaignName", CAMPAIGN_NAME)
                .put("multiplier", MULTIPLIER.toDouble())
                .put("expiresAt", EXPIRES_AT),
        )

    private fun failureOutcome(definitive: Boolean): JSONObject = JSONObject()
        .put("bonusId", PROMO_ID.toString())
        .put("result", "failure")
        .put("reason", "unavailable")
        .put("definitive", definitive)

    private companion object {
        const val FEATURE_NAME = "aiChat"
        const val REQUEST_ID = "123"
        const val METHOD_GET_BONUS_RECORD = "getAIChatBonusRecord"
        const val METHOD_GET_NEW_BONUS_RECORD = "getAIChatNewBonusRecord"
        const val METHOD_REDEEM_OUTCOME = "aiChatBonusRedeemOutcome"
        const val METHOD_DISMISS = "aiChatBonusDismiss"
        const val METHOD_END = "aiChatBonusEnd"

        const val CAMPAIGN_NAME = "spring-2026"
        val PROMO_ID: UUID = UUID.fromString("3f1c2b8e-9a4d-4e7b-8c21-5d6f0a9b1e34")
        const val MULTIPLIER = 2f
        const val EXPIRES_AT = 1_800_000_000_000L
        const val LAST_REDEEM_ATTEMPT_AT = 1_700_000_000_000L
        const val NEW_INSTALL_WINDOW_DAYS = 7

        val PENDING_RECORD = PromoRecord(campaignId = CAMPAIGN_NAME, promoId = PROMO_ID.toString())
        val ACTIVE_RECORD = PENDING_RECORD.copy(
            terms = PromoTerms(MULTIPLIER, EXPIRES_AT),
            lastRedeemAttemptMillis = LAST_REDEEM_ATTEMPT_AT,
        )
    }
}
