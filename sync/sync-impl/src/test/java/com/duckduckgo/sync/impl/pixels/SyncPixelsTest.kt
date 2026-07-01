/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.pixels

import android.content.SharedPreferences
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.api.engine.SyncableType
import com.duckduckgo.sync.impl.API_CODE
import com.duckduckgo.sync.impl.AccountErrorCodes
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.SyncCodeType
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.pixels.SyncPixels.CancellationReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.CodeVersion
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupFailureReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.duckduckgo.sync.impl.stats.DailyStats
import com.duckduckgo.sync.impl.stats.SyncStatsRepository
import com.duckduckgo.sync.store.SharedPrefsProvider
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealSyncPixelsTest {

    private var pixel: Pixel = mock()
    private var syncStatsRepository: SyncStatsRepository = mock()
    private var sharedPrefsProv: SharedPrefsProvider = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var testee: RealSyncPixels

    @Before
    fun setUp() {
        sharedPreferences = InMemorySharedPreferences()
        whenever(
            sharedPrefsProv.getSharedPrefs(eq("com.duckduckgo.sync.pixels.v1")),
        ).thenReturn(sharedPreferences)

        testee = RealSyncPixels(
            pixel,
            syncStatsRepository,
            sharedPrefsProv,
            syncFeature,
        )
    }

    @Test
    fun whenDailyPixelCalledThenPixelFired() {
        val dailyStats = givenSomeDailyStats()

        testee.fireDailySuccessRatePixel()

        val payload = mapOf(
            SyncPixelParameters.COUNT to dailyStats.attempts,
            SyncPixelParameters.DATE to dailyStats.date,
        ).plus(dailyStats.apiErrorStats)

        verify(pixel).fire(SyncPixelName.SYNC_DAILY_SUCCESS_RATE_PIXEL, payload)
    }

    @Test
    fun whenDailyPixelCalledTwiceThenPixelFiredOnce() {
        val dailyStats = givenSomeDailyStats()

        testee.fireDailySuccessRatePixel()
        testee.fireDailySuccessRatePixel()

        val payload = mapOf(
            SyncPixelParameters.COUNT to dailyStats.attempts,
            SyncPixelParameters.DATE to dailyStats.date,
        ).plus(dailyStats.apiErrorStats).plus(dailyStats.operationErrorStats)

        verify(pixel, times(1)).fire(SyncPixelName.SYNC_DAILY_SUCCESS_RATE_PIXEL, payload)
    }

    @Test
    fun whenLoginPixelCalledThenPixelFired() {
        testee.fireLoginPixel()

        verify(pixel).fire(SyncPixelName.SYNC_LOGIN)
    }

    @Test
    fun whenSignupDirectPixelCalledWithNoSourceThenPixelFired() {
        testee.fireSignupDirectPixel(source = null)

        verify(pixel).fire(SyncPixelName.SYNC_SIGNUP_DIRECT)
    }

    @Test
    fun whenSignupDirectPixelCalledWithSourceThenPixelFiredIncludesSource() {
        testee.fireSignupDirectPixel(source = "foo")
        verify(pixel).fire(SyncPixelName.SYNC_SIGNUP_DIRECT, mapOf("source" to "foo"))
    }

    @Test
    fun whenSignupConnectPixelCalledWithNoSourceThenPixelFired() {
        testee.fireSignupConnectPixel(source = null)

        verify(pixel).fire(SyncPixelName.SYNC_SIGNUP_CONNECT)
    }

    @Test
    fun whenSignupConnectPixelCalledWithSourceThenPixelFiredIncludesSource() {
        testee.fireSignupConnectPixel(source = "foo")
        verify(pixel).fire(SyncPixelName.SYNC_SIGNUP_CONNECT, mapOf("source" to "foo"))
    }

    @Test
    fun whenBarcodeScreenShownAndV2FlowEnabledThenPixelFiredWithV2AndDdg() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSyncBarcodeScreenShown(ScreenType.SYNC_CONNECT)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_BARCODE_SCREEN_SHOWN,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenBarcodeScreenShownAndV2FlowDisabledThenPixelFiredWithV1AndDdg() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(false))

        testee.fireSyncBarcodeScreenShown(ScreenType.SYNC_EXCHANGE)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_BARCODE_SCREEN_SHOWN,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v1",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenManualCodeEntryScreenShownAndV2FlowEnabledThenPixelFiredWithV2AndDdg() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSyncSetupManualCodeScreenShown(ScreenType.SYNC_EXCHANGE)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTRY_SCREEN_SHOWN,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenManualCodeEntryScreenShownAndV2FlowDisabledThenPixelFiredWithV1AndDdg() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(false))

        testee.fireSyncSetupManualCodeScreenShown(ScreenType.SYNC_CONNECT)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTRY_SCREEN_SHOWN,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v1",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenBarcodeScannerParseSuccessOnLegacyPathThenPixelFiredWithV1AndNoCodeType() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(false))

        testee.fireBarcodeScannerParseSuccess(ScreenType.SYNC_CONNECT, CodeVersion.V1)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_BARCODE_SCANNER_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_CODE_VERSION to "v1",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v1",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenBarcodeScannerParseSuccessForV2RecoveryCodeThenPixelFiredWithCodeMetadata() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireBarcodeScannerParseSuccess(ScreenType.SYNC_EXCHANGE, CodeVersion.V2, SyncCodeType.RECOVERY)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_BARCODE_SCANNER_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_CODE_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_CODE_TYPE to "recovery",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenManualCodeEnteredSuccessForV2LinkingCodeThenPixelFiredWithCodeMetadata() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSyncSetupCodePastedParseSuccess(ScreenType.SYNC_CONNECT, CodeVersion.V2, SyncCodeType.LINKING)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTERED_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_CODE_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_CODE_TYPE to "linking",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenManualCodeEnteredSuccessOnLegacyPathThenPixelFiredWithV1AndNoCodeType() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(false))

        testee.fireSyncSetupCodePastedParseSuccess(ScreenType.SYNC_EXCHANGE, CodeVersion.V1)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTERED_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_CODE_VERSION to "v1",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v1",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenBarcodeScannerParseErrorThenPixelFiredWithFlowMetadata() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(false))

        testee.fireBarcodeScannerParseError(ScreenType.SYNC_CONNECT)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_BARCODE_SCANNER_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v1",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenManualCodeEnteredFailureThenPixelFiredWithFlowMetadata() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSyncSetupCodePastedParseFailure(ScreenType.SYNC_EXCHANGE)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTERED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenSetupFinishedV1ThenPixelFiredWithFlowMetadataOnly() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(false))

        testee.fireSyncSetupFinishedSuccessfully(ScreenType.SYNC_CONNECT)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v1",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenSetupFinishedV2RecoveryThenPixelFiredWithPathNoRoleOrPeer() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSyncSetupFinishedSuccessfully(ScreenType.SYNC_EXCHANGE, SetupPath.RECOVERY)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_PATH to "recovery",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenSetupFinishedV2PairingThenPixelFiredWithPathRoleAndPeer() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSyncSetupFinishedSuccessfully(
            ScreenType.SYNC_CONNECT,
            SetupPath.PAIRING,
            SetupRole.HOST,
            PeerKind.THIRD_PARTY,
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_PATH to "pairing",
                SyncPixelParameters.SYNC_SETUP_MY_ROLE to "host",
                SyncPixelParameters.SYNC_SETUP_PEER_KIND to "3party",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenSyncSetupFailedWithPathRolePeerKindThenAllIncludedInPixel() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSyncSetupFailed(
            ScreenType.SYNC_CONNECT,
            SetupFailureReason.TRANSPORT_FAILURE,
            SetupPath.PAIRING,
            SetupRole.JOINER,
            PeerKind.DDG,
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_REASON to "transport_failure",
                SyncPixelParameters.SYNC_SETUP_PATH to "pairing",
                SyncPixelParameters.SYNC_SETUP_MY_ROLE to "joiner",
                SyncPixelParameters.SYNC_SETUP_PEER_KIND to "ddg",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenSyncSetupFailedWithoutPathRolePeerKindThenThoseParamsOmitted() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSyncSetupFailed(ScreenType.SYNC_EXCHANGE, SetupFailureReason.UNEXPECTED_FAILURE)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_REASON to "unexpected_failure",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenFireSetupFailedForUpgradeRequiredThenNeedsUpgrade() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSetupFailed(
            ScreenType.SYNC_CONNECT,
            DispatchOutcome.UpgradeRequired(codeMajor = 3, path = SetupPath.PAIRING, myRole = SetupRole.HOST),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_REASON to "needs_upgrade",
                SyncPixelParameters.SYNC_SETUP_PATH to "pairing",
                SyncPixelParameters.SYNC_SETUP_MY_ROLE to "host",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenFireSetupFailedForFailedOutcomeThenReasonMappedFromCode() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSetupFailed(
            ScreenType.SYNC_EXCHANGE,
            DispatchOutcome.Failed(
                reason = "boom",
                code = AccountErrorCodes.INVALID_CODE.code,
                path = SetupPath.RECOVERY,
            ),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_REASON to "invalid_credentials",
                SyncPixelParameters.SYNC_SETUP_PATH to "recovery",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenFireSetupFailedForCancellationCodesThenPixelNotFired() {
        testee.fireSetupFailed(ScreenType.SYNC_CONNECT, DispatchOutcome.Failed(reason = "user", code = AccountErrorCodes.PAIRING_CANCELLED.code))
        testee.fireSetupFailed(ScreenType.SYNC_CONNECT, DispatchOutcome.Failed(reason = "peer", code = AccountErrorCodes.PAIRING_REJECTED.code))

        verifyNoInteractions(pixel)
    }

    @Test
    fun whenSetupAbandonedWithReasonThenPixelFiredWithReasonAndFlowMetadata() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSyncSetupAbandoned(ScreenType.SYNC_CONNECT, CancellationReason.CANCELLED_BEFORE_FINISHED)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_ABANDONED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_REASON to "cancelled_before_finished",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenSetupAbandonedWithoutReasonThenReasonOmitted() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(false))

        testee.fireSyncSetupAbandoned(ScreenType.SYNC_EXCHANGE)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_ABANDONED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v1",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenFireSetupCancelledIfDeniedForPairingCancelledThenAbandonedFired() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSetupCancelledIfDenied(
            DispatchOutcome.Failed(reason = "user_denied", code = AccountErrorCodes.PAIRING_CANCELLED.code),
            ScreenType.SYNC_CONNECT,
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_ABANDONED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_REASON to "sync_confirmation_denied",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenFireSetupCancelledIfDeniedForPeerRejectionThenAbandonedFired() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSetupCancelledIfDenied(
            DispatchOutcome.Failed(reason = "peer", code = AccountErrorCodes.PAIRING_REJECTED.code),
            ScreenType.SYNC_EXCHANGE,
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_ABANDONED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_REASON to "sync_confirmation_denied",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenFireSetupCancelledIfDeniedForNonCancellationOutcomeThenNotFired() {
        testee.fireSetupCancelledIfDenied(
            DispatchOutcome.Failed(reason = "boom", code = AccountErrorCodes.PAIRING_FAILED.code),
            ScreenType.SYNC_CONNECT,
        )

        verifyNoInteractions(pixel)
    }

    @Test
    fun whenfireDailyApiErrorForObjectLimitExceededThenPixelSent() {
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.COUNT_LIMIT.code))

        verify(pixel).fire("m_sync_bookmarks_object_limit_exceeded_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenfireDailyApiErrorForRequestSizeLimitExceededThenPixelSent() {
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.CONTENT_TOO_LARGE.code))

        verify(pixel).fire("m_sync_bookmarks_request_size_limit_exceeded_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenfireDailyApiErrorForValidationErrorThenPixelSent() {
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.VALIDATION_ERROR.code))

        verify(pixel).fire("m_sync_bookmarks_validation_error_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenfireDailyApiErrorForTooManyRequestsThenPixelSent() {
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.TOO_MANY_REQUESTS_1.code))
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.TOO_MANY_REQUESTS_2.code))

        verify(pixel, times(2)).fire("m_sync_bookmarks_too_many_requests_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenFireSyncAccountErrorPixelForRescopeTokenThenPixelSent() {
        val error = Error(code = 401, reason = "unauthorized")

        testee.fireSyncAccountErrorPixel(error, SyncAccountOperation.RESCOPE_TOKEN)

        verify(pixel).fire(
            SyncPixelName.SYNC_RESCOPE_TOKEN_FAILURE,
            mapOf(
                SyncPixelParameters.ERROR_CODE to "401",
                SyncPixelParameters.ERROR_REASON to "unauthorized",
            ),
        )
    }

    @Test
    fun whenFireAiChatActiveThenDailyPixelFired() {
        testee.fireAiChatActive()

        verify(pixel).fire(SyncPixelName.SYNC_AI_CHAT_ACTIVE, emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenFireAiChatsRescopeTokenErrorWith400ThenValidationErrorPixelFired() {
        val error = Error(code = API_CODE.VALIDATION_ERROR.code, reason = "bad request")

        testee.fireAiChatsRescopeTokenError(error)

        verify(pixel).fire("m_sync_ai_chats_validation_error_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenFireAiChatsRescopeTokenErrorWith409ThenObjectLimitExceededPixelFired() {
        val error = Error(code = API_CODE.COUNT_LIMIT.code, reason = "count limit")

        testee.fireAiChatsRescopeTokenError(error)

        verify(pixel).fire("m_sync_ai_chats_object_limit_exceeded_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenFireAiChatsRescopeTokenErrorWith413ThenRequestSizeLimitExceededPixelFired() {
        val error = Error(code = API_CODE.CONTENT_TOO_LARGE.code, reason = "too large")

        testee.fireAiChatsRescopeTokenError(error)

        verify(pixel).fire("m_sync_ai_chats_request_size_limit_exceeded_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenFireAiChatsRescopeTokenErrorWith429ThenTooManyRequestsPixelFired() {
        val error = Error(code = API_CODE.TOO_MANY_REQUESTS_1.code, reason = "rate limited")

        testee.fireAiChatsRescopeTokenError(error)

        verify(pixel).fire("m_sync_ai_chats_too_many_requests_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenFireAiChatsRescopeTokenErrorWith401ThenNoPixelFired() {
        val error = Error(code = API_CODE.INVALID_LOGIN_CREDENTIALS.code, reason = "unauthorized")

        testee.fireAiChatsRescopeTokenError(error)

        verifyNoInteractions(pixel)
    }

    @Test
    fun whenBarcodeScannerParseErrorWithReasonThenPixelFiredWithReason() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireBarcodeScannerParseError(ScreenType.SYNC_CONNECT, reason = SetupFailureReason.UNRECOGNIZED_CODE)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_BARCODE_SCANNER_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_REASON to "unrecognized_code",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenManualCodeEnteredFailureWithReasonThenPixelFiredWithReason() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSyncSetupCodePastedParseFailure(ScreenType.SYNC_EXCHANGE, reason = SetupFailureReason.UNRECOGNIZED_CODE)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTERED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_REASON to "unrecognized_code",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenFireSetupFailedForSessionTimeoutCodeThenSessionTimeoutReason() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSetupFailed(
            ScreenType.SYNC_EXCHANGE,
            DispatchOutcome.Failed(reason = "Session timed out", code = AccountErrorCodes.SESSION_TIMEOUT.code),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_REASON to "session_timeout",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenFireSetupFailedForCreateAccountFailedCodeThenAccountCreationFailedReason() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSetupFailed(
            ScreenType.SYNC_CONNECT,
            DispatchOutcome.Failed(reason = "create_account_failed", code = AccountErrorCodes.CREATE_ACCOUNT_FAILED.code),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_REASON to "account_creation_failed",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenFireSetupFailedForAccountUpgradeFailedCodeThenAccountUpgradeFailedReason() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSetupFailed(
            ScreenType.SYNC_EXCHANGE,
            DispatchOutcome.Failed(reason = "upgrade_failed", code = AccountErrorCodes.ACCOUNT_UPGRADE_FAILED.code),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_REASON to "account_upgrade_failed",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenFireSetupFailedForAlreadyPairedCodeThenAlreadyPairedReason() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSetupFailed(
            ScreenType.SYNC_EXCHANGE,
            DispatchOutcome.Failed(reason = "same_account", code = AccountErrorCodes.ALREADY_PAIRED.code),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_REASON to "already_paired",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenFireSetupFailedForAlreadyConnectedOutcomeThenAlreadyPairedReasonWithPairingPath() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        // v2 same-account case: both peers exchange intros and discover a matching user_id
        testee.fireSetupFailed(ScreenType.SYNC_CONNECT, DispatchOutcome.AlreadyConnected)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "connect",
                SyncPixelParameters.SYNC_SETUP_REASON to "already_paired",
                SyncPixelParameters.SYNC_SETUP_PATH to "pairing",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenFireSetupFailedWithTimeoutStageThenPixelIncludesTimeoutStageParam() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSetupFailed(
            ScreenType.SYNC_EXCHANGE,
            DispatchOutcome.Failed(
                reason = "Session timed out",
                code = AccountErrorCodes.SESSION_TIMEOUT.code,
                timeoutStage = SyncPixels.TimeoutStage.WAITING_FOR_CONFIRMATION,
            ),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_REASON to "session_timeout",
                SyncPixelParameters.SYNC_SETUP_TIMEOUT_STAGE to "waiting_for_confirmation",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun whenFireSetupFailedForPairingUnavailableCodeThenProtocolErrorReason() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))

        testee.fireSetupFailed(
            ScreenType.SYNC_EXCHANGE,
            DispatchOutcome.Failed(reason = "pairing_unavailable", code = AccountErrorCodes.PAIRING_UNAVAILABLE.code),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "exchange",
                SyncPixelParameters.SYNC_SETUP_REASON to "protocol_error",
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    private fun givenSomeDailyStats(): DailyStats {
        val date = DatabaseDateFormatter.getUtcIsoLocalDate()
        val dailyStats = DailyStats("1", date, emptyMap())
        whenever(syncStatsRepository.getYesterdayDailyStats()).thenReturn(dailyStats)

        return dailyStats
    }
}
