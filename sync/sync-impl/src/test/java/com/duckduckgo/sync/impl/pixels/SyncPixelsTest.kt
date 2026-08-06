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
import com.duckduckgo.sync.impl.pixels.SyncPixels.AnotherDevicePromptOption
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
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(TestParameterInjector::class)
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
    fun `when daily pixel is called then pixel is fired`() {
        val dailyStats = givenSomeDailyStats()

        testee.fireDailySuccessRatePixel()

        verify(pixel).fire(
            SyncPixelName.SYNC_DAILY_SUCCESS_RATE_PIXEL,
            buildMap {
                put(SyncPixelParameters.COUNT, dailyStats.attempts)
                put(SyncPixelParameters.DATE, dailyStats.date)
                putAll(dailyStats.apiErrorStats)
            },
        )
    }

    @Test
    fun `when daily pixel is called twice then pixel is fired once`() {
        val dailyStats = givenSomeDailyStats()

        testee.fireDailySuccessRatePixel()
        testee.fireDailySuccessRatePixel()

        verify(pixel, times(1)).fire(
            SyncPixelName.SYNC_DAILY_SUCCESS_RATE_PIXEL,
            buildMap {
                put(SyncPixelParameters.COUNT, dailyStats.attempts)
                put(SyncPixelParameters.DATE, dailyStats.date)
                putAll(dailyStats.apiErrorStats)
                putAll(dailyStats.operationErrorStats)
            },
        )
    }

    @Test
    fun `when login pixel is fired then pixel is fired`(
        @TestParameter ui: UiVersion,
    ) {
        ui.configure(syncFeature)

        testee.fireLoginPixel()

        verify(pixel).fire(
            SyncPixelName.SYNC_LOGIN,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
            ),
        )
    }

    @Test
    fun `when signup direct pixel is called without source then pixel is fired`(
        @TestParameter ui: UiVersion,
    ) {
        ui.configure(syncFeature)

        testee.fireSignupDirectPixel(source = null)

        verify(pixel).fire(
            SyncPixelName.SYNC_SIGNUP_DIRECT,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
            ),
        )
    }

    @Test
    fun `when signup direct pixel is called with source then fired pixel includes source`(
        @TestParameter ui: UiVersion,
    ) {
        ui.configure(syncFeature)

        testee.fireSignupDirectPixel(source = "foo")

        verify(pixel).fire(
            SyncPixelName.SYNC_SIGNUP_DIRECT,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "foo",
            ),
        )
    }

    @Test
    fun `when signup connect pixel is called without source then pixel is fired`(
        @TestParameter ui: UiVersion,
    ) {
        ui.configure(syncFeature)

        testee.fireSignupConnectPixel(source = null)

        verify(pixel).fire(
            SyncPixelName.SYNC_SIGNUP_CONNECT,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
            ),
        )
    }

    @Test
    fun `when signup connect pixel is called with source then fired pixel includes source`(
        @TestParameter ui: UiVersion,
    ) {
        ui.configure(syncFeature)

        testee.fireSignupConnectPixel(source = "foo")

        verify(pixel).fire(
            SyncPixelName.SYNC_SIGNUP_CONNECT,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to "foo",
            ),
        )
    }

    @Test
    fun `when barcode screen is shown the pixel is fired`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSyncBarcodeScreenShown(screenType)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_BARCODE_SCREEN_SHOWN,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when scan code screen is shown the pixel is fired`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireScanCodeScreenShown(screenType)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_SCAN_QR_SCREEN_SHOWN,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when manual code entry screen is shown the pixel is fired`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSyncSetupManualCodeScreenShown(screenType)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTRY_SCREEN_SHOWN,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when barcode scanner parses a v1 code then the success pixel carries no code type`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireBarcodeScannerParseSuccess(screenType, CodeVersion.V1)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_BARCODE_SCANNER_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_CODE_VERSION to "v1",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when barcode scanner parses a v2 recovery code then the success pixel carries the code metadata`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireBarcodeScannerParseSuccess(screenType, CodeVersion.V2, SyncCodeType.RECOVERY)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_BARCODE_SCANNER_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_CODE_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_CODE_TYPE to "recovery",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when a v2 linking code is entered manually then the success pixel carries the code metadata`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSyncSetupCodePastedParseSuccess(screenType, CodeVersion.V2, SyncCodeType.LINKING)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTERED_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_CODE_VERSION to "v2",
                SyncPixelParameters.SYNC_SETUP_CODE_TYPE to "linking",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when a v1 code is entered manually then the success pixel carries no code type`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSyncSetupCodePastedParseSuccess(screenType, CodeVersion.V1)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTERED_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_CODE_VERSION to "v1",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when barcode scanner parse fails without a reason then the pixel is fired`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireBarcodeScannerParseError(screenType)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_BARCODE_SCANNER_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when barcode scanner parse fails with a reason then the pixel carries the reason`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireBarcodeScannerParseError(screenType, reason = SetupFailureReason.UNRECOGNIZED_CODE)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_BARCODE_SCANNER_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "unrecognized_code",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when manual code entry fails without a reason then the pixel is fired`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSyncSetupCodePastedParseFailure(screenType)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTERED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when manual code entry fails with a reason then the pixel carries the reason`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSyncSetupCodePastedParseFailure(screenType, reason = SetupFailureReason.UNRECOGNIZED_CODE)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTERED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "unrecognized_code",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup finished without a path then the pixel is fired`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSyncSetupFinishedSuccessfully(screenType)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup finished for a recovery path then the pixel carries the path`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSyncSetupFinishedSuccessfully(screenType, SetupPath.RECOVERY)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_PATH to "recovery",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup finished for a pairing path then the pixel carries the path role and peer`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSyncSetupFinishedSuccessfully(
            screenType,
            SetupPath.PAIRING,
            SetupRole.HOST,
            PeerKind.THIRD_PARTY,
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_SUCCESS,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_PATH to "pairing",
                SyncPixelParameters.SYNC_SETUP_MY_ROLE to "host",
                SyncPixelParameters.SYNC_SETUP_PEER_KIND to "3party",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup failed with path role and peer then all are included in the pixel`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSyncSetupFailed(
            screenType,
            SetupFailureReason.TRANSPORT_FAILURE,
            SetupPath.PAIRING,
            SetupRole.JOINER,
            PeerKind.DDG,
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "transport_failure",
                SyncPixelParameters.SYNC_SETUP_PATH to "pairing",
                SyncPixelParameters.SYNC_SETUP_MY_ROLE to "joiner",
                SyncPixelParameters.SYNC_SETUP_PEER_KIND to "ddg",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup failed without path role and peer then those params are omitted`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSyncSetupFailed(screenType, SetupFailureReason.UNEXPECTED_FAILURE)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "unexpected_failure",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup failed for an upgrade required outcome then needs upgrade reason is used`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSetupFailed(
            screenType,
            DispatchOutcome.UpgradeRequired(codeMajor = 3, path = SetupPath.PAIRING, myRole = SetupRole.HOST),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "needs_upgrade",
                SyncPixelParameters.SYNC_SETUP_PATH to "pairing",
                SyncPixelParameters.SYNC_SETUP_MY_ROLE to "host",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup failed for a failed outcome then the reason is mapped and the path forwarded`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSetupFailed(
            screenType,
            DispatchOutcome.Failed(
                reason = "boom",
                code = AccountErrorCodes.INVALID_CODE.code,
                path = SetupPath.RECOVERY,
            ),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "invalid_credentials",
                SyncPixelParameters.SYNC_SETUP_PATH to "recovery",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup failed for cancellation codes then pixel is not fired`() {
        testee.fireSetupFailed(ScreenType.SYNC_CONNECT, DispatchOutcome.Failed(reason = "user", code = AccountErrorCodes.PAIRING_CANCELLED.code))
        testee.fireSetupFailed(ScreenType.SYNC_CONNECT, DispatchOutcome.Failed(reason = "peer", code = AccountErrorCodes.PAIRING_REJECTED.code))

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when setup failed for a session timeout code then session timeout reason is used`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSetupFailed(
            screenType,
            DispatchOutcome.Failed(reason = "Session timed out", code = AccountErrorCodes.SESSION_TIMEOUT.code),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "session_timeout",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup failed for a create account failed code then account creation failed reason is used`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSetupFailed(
            screenType,
            DispatchOutcome.Failed(reason = "create_account_failed", code = AccountErrorCodes.CREATE_ACCOUNT_FAILED.code),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "account_creation_failed",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup failed for an account upgrade failed code then account upgrade failed reason is used`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSetupFailed(
            screenType,
            DispatchOutcome.Failed(reason = "upgrade_failed", code = AccountErrorCodes.ACCOUNT_UPGRADE_FAILED.code),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "account_upgrade_failed",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup failed for an already paired code then already paired reason is used`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSetupFailed(
            screenType,
            DispatchOutcome.Failed(reason = "same_account", code = AccountErrorCodes.ALREADY_PAIRED.code),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "already_paired",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup failed for an already connected outcome then already paired reason with pairing path`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        // v2 same-account case: both peers exchange intros and discover a matching user_id
        testee.fireSetupFailed(screenType, DispatchOutcome.AlreadyConnected)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "already_paired",
                SyncPixelParameters.SYNC_SETUP_PATH to "pairing",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup failed with a timeout stage then the pixel includes the timeout stage param`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSetupFailed(
            screenType,
            DispatchOutcome.Failed(
                reason = "Session timed out",
                code = AccountErrorCodes.SESSION_TIMEOUT.code,
                timeoutStage = SyncPixels.TimeoutStage.WAITING_FOR_CONFIRMATION,
            ),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "session_timeout",
                SyncPixelParameters.SYNC_SETUP_TIMEOUT_STAGE to "waiting_for_confirmation",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup failed for a pairing unavailable code then protocol error reason is used`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSetupFailed(
            screenType,
            DispatchOutcome.Failed(reason = "pairing_unavailable", code = AccountErrorCodes.PAIRING_UNAVAILABLE.code),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_FAILED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "protocol_error",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup abandoned with a reason then the pixel carries the reason`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSyncSetupAbandoned(screenType, CancellationReason.CANCELLED_BEFORE_FINISHED)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_ABANDONED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "cancelled_before_finished",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup abandoned without a reason then the reason is omitted`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSyncSetupAbandoned(screenType)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_ABANDONED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup cancelled if denied for a pairing cancelled code then abandoned pixel fired`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSetupCancelledIfDenied(
            screenType,
            DispatchOutcome.Failed(reason = "user_denied", code = AccountErrorCodes.PAIRING_CANCELLED.code),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_ABANDONED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "sync_confirmation_denied",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup cancelled if denied for a peer rejection code then abandoned pixel fired`(
        @TestParameter protocol: ProtocolVersion,
        @TestParameter ui: UiVersion,
        @TestParameter screenType: ScreenType,
    ) {
        protocol.configure(syncFeature)
        ui.configure(syncFeature)

        testee.fireSetupCancelledIfDenied(
            screenType,
            DispatchOutcome.Failed(reason = "peer", code = AccountErrorCodes.PAIRING_REJECTED.code),
        )

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ENDED_ABANDONED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_FLOW_VERSION to protocol.value,
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE to screenType.value,
                SyncPixelParameters.SYNC_SETUP_REASON to "sync_confirmation_denied",
                SyncPixelParameters.SYNC_SETUP_MY_KIND to "ddg",
            ),
        )
    }

    @Test
    fun `when setup cancelled if denied for a non cancellation outcome then pixel is not fired`() {
        testee.fireSetupCancelledIfDenied(
            ScreenType.SYNC_CONNECT,
            DispatchOutcome.Failed(reason = "boom", code = AccountErrorCodes.PAIRING_FAILED.code),
        )

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when daily api error for object limit exceeded then pixel is sent`() {
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.COUNT_LIMIT.code))

        verify(pixel).fire("m_sync_bookmarks_object_limit_exceeded_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when daily api error for request size limit exceeded then pixel is sent`() {
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.CONTENT_TOO_LARGE.code))

        verify(pixel).fire("m_sync_bookmarks_request_size_limit_exceeded_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when daily api error for validation error then pixel is sent`() {
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.VALIDATION_ERROR.code))

        verify(pixel).fire("m_sync_bookmarks_validation_error_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when daily api error for too many requests then pixel is sent`() {
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.TOO_MANY_REQUESTS_1.code))
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.TOO_MANY_REQUESTS_2.code))

        verify(pixel, times(2)).fire("m_sync_bookmarks_too_many_requests_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when rescope token error pixel is fired then it carries the error`(
        @TestParameter ui: UiVersion,
    ) {
        ui.configure(syncFeature)
        val error = Error(code = 401, reason = "unauthorized")

        testee.fireSyncAccountErrorPixel(error, SyncAccountOperation.RESCOPE_TOKEN)

        verify(pixel).fire(
            SyncPixelName.SYNC_RESCOPE_TOKEN_FAILURE,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.ERROR_CODE to "401",
                SyncPixelParameters.ERROR_REASON to "unauthorized",
            ),
        )
    }

    @Test
    fun `when ai chat active then daily pixel is fired`() {
        testee.fireAiChatActive()

        verify(pixel).fire(SyncPixelName.SYNC_AI_CHAT_ACTIVE, emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when ai chats rescope token error with 400 then validation error pixel is fired`() {
        val error = Error(code = API_CODE.VALIDATION_ERROR.code, reason = "bad request")

        testee.fireAiChatsRescopeTokenError(error)

        verify(pixel).fire("m_sync_ai_chats_validation_error_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when ai chats rescope token error with 409 then object limit exceeded pixel is fired`() {
        val error = Error(code = API_CODE.COUNT_LIMIT.code, reason = "count limit")

        testee.fireAiChatsRescopeTokenError(error)

        verify(pixel).fire("m_sync_ai_chats_object_limit_exceeded_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when ai chats rescope token error with 413 then request size limit exceeded pixel is fired`() {
        val error = Error(code = API_CODE.CONTENT_TOO_LARGE.code, reason = "too large")

        testee.fireAiChatsRescopeTokenError(error)

        verify(pixel).fire("m_sync_ai_chats_request_size_limit_exceeded_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when ai chats rescope token error with 429 then too many requests pixel is fired`() {
        val error = Error(code = API_CODE.TOO_MANY_REQUESTS_1.code, reason = "rate limited")

        testee.fireAiChatsRescopeTokenError(error)

        verify(pixel).fire("m_sync_ai_chats_too_many_requests_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when ai chats rescope token error with 401 then no pixel is fired`() {
        val error = Error(code = API_CODE.INVALID_LOGIN_CREDENTIALS.code, reason = "unauthorized")

        testee.fireAiChatsRescopeTokenError(error)

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when sync settings shown then the pixel is fired`(
        @TestParameter ui: UiVersion,
    ) {
        ui.configure(syncFeature)

        testee.fireSyncSettingsShown()

        verify(pixel).fire(
            SyncPixelName.SYNC_SETTINGS_SCREEN_SHOWN,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
            ),
        )
    }

    @Test
    fun `when backup this device tapped then the pixel is fired`(
        @TestParameter ui: UiVersion,
    ) {
        ui.configure(syncFeature)

        testee.fireBackupThisDeviceTapped()

        verify(pixel).fire(
            SyncPixelName.SYNC_SETTINGS_BACK_UP_THIS_DEVICE_TAPPED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
            ),
        )
    }

    @Test
    fun `when recover sync data tapped then the pixel is fired`(
        @TestParameter ui: UiVersion,
    ) {
        ui.configure(syncFeature)

        testee.fireRecoverSyncDataTapped()

        verify(pixel).fire(
            SyncPixelName.SYNC_SETTINGS_RECOVER_SYNC_DATA_TAPPED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
            ),
        )
    }

    @Test
    fun `when recover sync data confirmed then the pixel is fired`(
        @TestParameter ui: UiVersion,
    ) {
        ui.configure(syncFeature)

        testee.fireRecoverSyncDataConfirmed()

        verify(pixel).fire(
            SyncPixelName.SYNC_SETTINGS_RECOVER_SYNC_DATA_CONFIRMED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
            ),
        )
    }

    @Test
    fun `when another device prompt shown then the pixel is fired`(
        @TestParameter ui: UiVersion,
    ) {
        ui.configure(syncFeature)

        testee.fireSyncAnotherDevicePromptShown()

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ANOTHER_DEVICE_PROMPT_SHOWN,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
            ),
        )
    }

    @Test
    fun `when another device prompt option tapped then the pixel carries the option`(
        @TestParameter ui: UiVersion,
        @TestParameter option: AnotherDevicePromptOption,
    ) {
        ui.configure(syncFeature)

        testee.fireSyncAnotherDevicePromptOptionTapped(option)

        verify(pixel).fire(
            SyncPixelName.SYNC_SETUP_ANOTHER_DEVICE_PROMPT_OPTION_TAPPED,
            mapOf(
                SyncPixelParameters.SYNC_SETUP_UI_VERSION to ui.value,
                SyncPixelParameters.OPTION to option.value,
            ),
        )
    }

    private fun givenSomeDailyStats(): DailyStats {
        val date = DatabaseDateFormatter.getUtcIsoLocalDate()
        val dailyStats = DailyStats("1", date, emptyMap())
        whenever(syncStatsRepository.getYesterdayDailyStats()).thenReturn(dailyStats)

        return dailyStats
    }

    enum class ProtocolVersion(
        val value: String,
    ) {
        ProtocolV1("v1"),
        ProtocolV2("v2"),
        ;

        fun configure(syncFeature: SyncFeature) {
            val isEnabled = when (this) {
                ProtocolV1 -> false
                ProtocolV2 -> true
            }
            syncFeature.canUseV2ConnectFlow().setRawStoredState(State(isEnabled))
        }
    }

    enum class UiVersion(
        val value: String,
    ) {
        UiV1("v1"),
        UiV2("v2"),
        ;

        fun configure(syncFeature: SyncFeature) {
            val isEnabled = when (this) {
                UiV1 -> false
                UiV2 -> true
            }
            syncFeature.useSimplifiedSync().setRawStoredState(State(isEnabled))
        }
    }
}
