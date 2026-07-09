/*
 * Copyright (c) 2022 DuckDuckGo
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
import androidx.core.content.edit
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter.Companion.removeAtb
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.engine.DeletableType
import com.duckduckgo.sync.api.engine.SyncFeatureType
import com.duckduckgo.sync.impl.API_CODE
import com.duckduckgo.sync.impl.AccountErrorCodes
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.SyncCodeType
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_DAILY
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_DAILY_SUCCESS_RATE_PIXEL
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_OBJECT_LIMIT_EXCEEDED_DAILY
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.CONNECTED_DEVICES_WHEN_DELETING
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_FEATURE_PROMOTION_SOURCE
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_CODE_TYPE
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_CODE_VERSION
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_FLOW_VERSION
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_MY_KIND
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_MY_ROLE
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_PATH
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_PEER_KIND
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_REASON
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_SCREEN_TYPE
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters.SYNC_SETUP_TIMEOUT_STAGE
import com.duckduckgo.sync.impl.pixels.SyncPixels.CancellationReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.CodeVersion
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupFailureReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.duckduckgo.sync.impl.pixels.SyncPixels.TimeoutStage
import com.duckduckgo.sync.impl.stats.SyncStatsRepository
import com.duckduckgo.sync.store.SharedPrefsProvider
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

interface SyncPixels {

    /**
     * Fired once per day, for all users with sync enabled
     * Sent during the first sync of the day
     */
    fun fireDailyPixel()

    /**
     * Fired once per day, for all users with sync enabled
     * It carries the daily stats for errors and sync count
     */
    fun fireDailySuccessRatePixel()

    /**
     * Fired after a sync operation has found timestamp conflict
     */
    fun fireTimestampConflictPixel(feature: String)

    /**
     * Fired when adding new device to existing account
     */
    fun fireLoginPixel()

    /**
     * Fired when user sets up a sync account from connect flow
     * @param source: the source of the signup, e.g. "promotion_bookmarks", "promotion_passwords" etc.... Can be null if not applicable.
     */
    fun fireSignupConnectPixel(source: String?)

    /**
     * Fired when user sets up a sync account directly.
     * @param source: the source of the signup, e.g. "promotion_bookmarks", "promotion_passwords" etc.... Can be null if not applicable.
     */
    fun fireSignupDirectPixel(source: String?)

    fun fireSyncAccountErrorPixel(
        result: Error,
        type: SyncAccountOperation,
    )

    fun fireDailySyncApiErrorPixel(
        feature: SyncFeatureType,
        apiError: Error,
    )

    fun fireAskUserToSwitchAccount()
    fun fireUserAcceptedSwitchingAccount()
    fun fireUserCancelledSwitchingAccount()
    fun fireUserSwitchedAccount()
    fun fireUserSwitchedLogoutError()
    fun fireUserSwitchedLoginError()
    fun fireTimeoutOnDeepLinkSetup()
    fun fireSyncBarcodeScreenShown(screenType: ScreenType)
    fun fireSyncSetupFinishedSuccessfully(
        screenType: ScreenType,
        path: SetupPath? = null,
        myRole: SetupRole? = null,
        peerKind: PeerKind? = null,
    )
    fun fireSyncSetupAbandoned(screenType: ScreenType, reason: CancellationReason? = null)
    fun fireSyncSetupManualCodeScreenShown(screenType: ScreenType)
    fun fireSyncSetupCodePastedParseSuccess(screenType: ScreenType, codeVersion: CodeVersion, codeType: SyncCodeType? = null)
    fun fireSyncSetupCodePastedParseFailure(screenType: ScreenType, reason: SetupFailureReason? = null)
    fun fireSyncSetupCodeCopiedToClipboard(screenType: ScreenType)
    fun fireBarcodeScannerParseError(screenType: ScreenType, reason: SetupFailureReason? = null)
    fun fireBarcodeScannerParseSuccess(screenType: ScreenType, codeVersion: CodeVersion, codeType: SyncCodeType? = null)

    /**
     * "Setup failed" — a v2 setup that started (code recognized) then failed with an error (not a
     * user cancellation). [screenType] identifies the originating screen.
     * [path]/[myRole]/[peerKind] are best-effort and omitted when unknown.
     * [timeoutStage] is only meaningful when [reason] is [SetupFailureReason.SESSION_TIMEOUT] and
     * identifies which phase of the flow was reached at the deadline.
     */
    fun fireSyncSetupFailed(
        screenType: ScreenType,
        reason: SetupFailureReason,
        path: SetupPath? = null,
        myRole: SetupRole? = null,
        peerKind: PeerKind? = null,
        timeoutStage: TimeoutStage? = null,
    )

    enum class ScreenType(val value: String) {
        SYNC_CONNECT("connect"),
        SYNC_EXCHANGE("exchange"),
    }

    /** Protocol version of the code that was scanned/pasted, per the "Code recognized" telemetry. */
    enum class CodeVersion(val value: String) {
        V1("v1"),
        V2("v2"),
    }

    /** Whether a successful setup was a recovery-code login or a device pairing. v2 only. */
    enum class SetupPath(val value: String) {
        RECOVERY("recovery"),
        PAIRING("pairing"),
    }

    /** This device's elected role in a v2 pairing. */
    enum class SetupRole(val value: String) {
        HOST("host"),
        JOINER("joiner"),
    }

    /** The peer device's credential kind in a v2 pairing. */
    enum class PeerKind(val value: String) {
        DDG("ddg"),
        THIRD_PARTY("3party"),
    }

    /**
     * Why a setup was cancelled, per the "Cancellation" telemetry. Based on the v2 protocol state at
     * the moment of cancellation: [SCANNING_CANCELLED] before the exchange engages a peer,
     * [CANCELLED_BEFORE_FINISHED] mid-exchange, [CONFIRMATION_DENIED] when this device denied the prompt.
     */
    enum class CancellationReason(val value: String) {
        SCANNING_CANCELLED("scanning_cancelled"),
        CONFIRMATION_DENIED("sync_confirmation_denied"),
        CANCELLED_BEFORE_FINISHED("cancelled_before_finished"),
    }

    enum class SetupFailureReason(val value: String) {
        SESSION_TIMEOUT("session_timeout"),
        TRANSPORT_FAILURE("transport_failure"),
        UNRECOGNIZED_CODE("unrecognized_code"),
        NEEDS_UPGRADE("needs_upgrade"),
        INCOMPATIBLE_CODE("incompatible_code"),
        INVALID_CREDENTIALS("invalid_credentials"),
        ACCOUNT_CREATION_FAILED("account_creation_failed"),
        ACCOUNT_UPGRADE_FAILED("account_upgrade_failed"),
        ALREADY_UPGRADED("already_upgraded"),
        ALREADY_PAIRED("already_paired"),
        RECOVERY_CODE_PREPARATION_FAILED("recovery_code_preparation_failed"),
        MISSING_3PARTY_CREDENTIAL("missing_3party_credential"),
        UNDECRYPTABLE_3PARTY_CREDENTIAL("undecryptable_3party_credential"),
        ACCOUNT_EXTEND_FAILED("account_extend_failed"),
        MISSING_3PARTY_KEY("missing_3party_key"),
        LOCAL_STORAGE_FAILED("local_storage_failed"),
        PEER_RECOVERY_CODE_UNAVAILABLE("peer_recovery_code_unavailable"),
        UNEXPECTED_SECOND_HELLO("unexpected_second_hello"),
        UNEXPECTED_EVENT("unexpected_event"),
        PAIRING_SESSION_NOT_READY("pairing_session_not_ready"),
        RELAY_CHANNEL_UNAVAILABLE("relay_channel_unavailable"),
        PROTOCOL_ERROR("protocol_error"),
        UNEXPECTED_FAILURE("unexpected_failure"),
    }

    enum class TimeoutStage(val value: String) {
        WAITING_FOR_PEER_HELLO("waiting_for_peer_hello"),
        WAITING_FOR_PEER_STATUS("waiting_for_peer_status"),
        WAITING_FOR_CONFIRMATION("waiting_for_confirmation"),
        WAITING_FOR_RECOVERY_CODE("waiting_for_recovery_code"),
        LOGGING_IN("logging_in"),
    }
    fun fireSetupDeepLinkFlowStarted()
    fun fireSetupDeepLinkFlowSuccess()
    fun fireSetupDeepLinkFlowAbandoned()
    fun fireUserConfirmedToTurnOffSync()
    fun fireUserConfirmedToTurnOffSyncAndDelete(connectedDevices: Int)
    fun fireSetupSyncPromoBookmarkAddedDialogDismissed()
    fun fireSetupSyncPromoBookmarkAddedDialogConfirmed()
    fun fireSetupSyncPromoBookmarkAddedDialogShown()

    fun fireAiChatActive()
    fun fireAiChatsRescopeTokenError(error: Error)

    fun fireAutoRestoreSetupToggleShown()
    fun fireAutoRestoreSetupToggleOptedOut()
    fun fireAutoRestoreSettingsReadyShown(source: String)
    fun fireAutoRestoreSettingsRestoreTapped(source: String)
    fun fireAutoRestoreSettingsSkipRestoreTapped(source: String)
    fun fireAutoRestoreSettingsCancelled(source: String)
    fun fireAutoRestoreSettingsManualRecoveryShown()
    fun fireAutoRestoreSettingsPageShown()
    fun fireAutoRestoreSettingsPageToggleEnabled()
    fun fireAutoRestoreSettingsPageToggleDisabled()
    fun fireAutoRestoreSuccess(source: String)
    fun fireAutoRestoreFailure(source: String, errorCode: String, errorMessage: String)
    fun fireAutoRestorePreservedAccountCleared(source: String)
    fun fireAutoRestorePreservedAccountClearFailed(source: String, errorCode: String, errorMessage: String)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSyncPixels @Inject constructor(
    private val pixel: Pixel,
    private val statsRepository: SyncStatsRepository,
    private val sharedPrefsProvider: SharedPrefsProvider,
    private val syncFeature: SyncFeature,
) : SyncPixels {

    private val preferences: SharedPreferences by lazy {
        sharedPrefsProvider.getSharedPrefs(SYNC_PIXELS_PREF_FILE)
    }

    /**
     * Common metadata describing the setup flow this device is opening:
     * - [SYNC_SETUP_FLOW_VERSION]: "v2" when the device is on the v2 connect/exchange stack
     *   ([SyncFeature.canUseV2ConnectFlow]), "v1" otherwise. Independent of which code version is
     *   actually displayed (that is gated separately by [SyncFeature.canShowV2ConnectCode]).
     * - [SYNC_SETUP_MY_KIND]: always "ddg" — this is the native DuckDuckGo client.
     */
    private fun setupFlowMetadata(): Map<String, String> = mapOf(
        SYNC_SETUP_FLOW_VERSION to if (syncFeature.canUseV2ConnectFlow().isEnabled()) FLOW_VERSION_V2 else FLOW_VERSION_V1,
        SYNC_SETUP_MY_KIND to MY_KIND_DDG,
    )

    /**
     * Params for the "Code recognized" pixels (scanner/manual-entry success): the screen [source],
     * the recognized [codeVersion] (what was scanned/pasted), the common [setupFlowMetadata]
     * (flow_version + my_kind), and — only when known — the [codeType]. The legacy/v1 path does not
     * report a code type, so [codeType] is omitted there; the v2 path always supplies it.
     */
    private fun recognizedCodeParams(
        screenType: ScreenType,
        codeVersion: CodeVersion,
        codeType: SyncCodeType?,
    ): Map<String, String> = buildMap {
        put(SYNC_SETUP_SCREEN_TYPE, screenType.value)
        put(SYNC_SETUP_CODE_VERSION, codeVersion.value)
        when (codeType) {
            SyncCodeType.RECOVERY -> put(SYNC_SETUP_CODE_TYPE, CODE_TYPE_RECOVERY)
            SyncCodeType.LINKING -> put(SYNC_SETUP_CODE_TYPE, CODE_TYPE_LINKING)
            null -> {}
        }
        putAll(setupFlowMetadata())
    }

    /**
     * Params for the "Setup success" pixel: the screen [source], the common [setupFlowMetadata]
     * (flow_version + my_kind), and — v2 only — the [path] (recovery/pairing) plus, for pairing, the
     * elected [myRole] and the [peerKind]. Each is omitted when null.
     */
    private fun setupSuccessParams(
        screenType: ScreenType,
        path: SetupPath?,
        myRole: SetupRole?,
        peerKind: PeerKind?,
    ): Map<String, String> = buildMap {
        put(SYNC_SETUP_SCREEN_TYPE, screenType.value)
        if (path != null) put(SYNC_SETUP_PATH, path.value)
        if (myRole != null) put(SYNC_SETUP_MY_ROLE, myRole.value)
        if (peerKind != null) put(SYNC_SETUP_PEER_KIND, peerKind.value)
        putAll(setupFlowMetadata())
    }

    override fun fireDailyPixel() {
        tryToFireDailyPixel(SYNC_DAILY)
    }

    override fun fireDailySuccessRatePixel() {
        val dailyStats = statsRepository.getYesterdayDailyStats()
        val payload = mapOf(
            SyncPixelParameters.COUNT to dailyStats.attempts,
            SyncPixelParameters.DATE to dailyStats.date,
        ).plus(dailyStats.apiErrorStats).plus(dailyStats.operationErrorStats)
        tryToFireDailyPixel(SYNC_DAILY_SUCCESS_RATE_PIXEL, payload)
    }

    override fun fireTimestampConflictPixel(feature: String) {
        pixel.fire(
            String.format(Locale.US, SyncPixelName.SYNC_TIMESTAMP_RESOLUTION_TRIGGERED.pixelName, feature),
        )
    }

    override fun fireLoginPixel() {
        pixel.fire(SyncPixelName.SYNC_LOGIN)
    }

    override fun fireSignupConnectPixel(source: String?) {
        pixel.fire(SyncPixelName.SYNC_SIGNUP_CONNECT, buildSourceMap(source))
    }

    override fun fireSignupDirectPixel(source: String?) {
        pixel.fire(SyncPixelName.SYNC_SIGNUP_DIRECT, buildSourceMap(source))
    }

    override fun fireSyncAccountErrorPixel(
        result: Error,
        type: SyncAccountOperation,
    ) {
        when (type) {
            SyncAccountOperation.SIGNUP -> fireSignupErrorPixel(result)
            SyncAccountOperation.LOGIN -> fireLoginErrorPixel(result)
            SyncAccountOperation.LOGOUT -> fireLogoutErrorPixel(result)
            SyncAccountOperation.UPDATE_DEVICE -> fireUpdateDeviceErrorPixel(result)
            SyncAccountOperation.REMOVE_DEVICE -> fireRemoveDeviceErrorPixel(result)
            SyncAccountOperation.DELETE_ACCOUNT -> fireDeleteAccountErrorPixel(result)
            SyncAccountOperation.USER_SIGNED_IN -> fireAlreadySignedInErrorPixel(result)
            SyncAccountOperation.CREATE_PDF -> fireSaveRecoveryPdfErrorPixel(result)
            SyncAccountOperation.RESCOPE_TOKEN -> fireRescopeTokenErrorPixel(result)
            SyncAccountOperation.GENERIC -> fireSyncAccountErrorPixel(result)
        }
    }

    override fun fireDailySyncApiErrorPixel(
        feature: SyncFeatureType,
        apiError: Error,
    ) {
        when (apiError.code) {
            API_CODE.COUNT_LIMIT.code -> {
                pixel.fire(
                    String.format(Locale.US, SYNC_OBJECT_LIMIT_EXCEEDED_DAILY.pixelName, feature.field),
                    type = Pixel.PixelType.Daily(),
                )
            }

            API_CODE.CONTENT_TOO_LARGE.code -> {
                pixel.fire(
                    String.format(Locale.US, SyncPixelName.SYNC_REQUEST_SIZE_LIMIT_EXCEEDED_DAILY.pixelName, feature.field),
                    type = Pixel.PixelType.Daily(),
                )
            }

            API_CODE.VALIDATION_ERROR.code -> {
                pixel.fire(
                    String.format(Locale.US, SyncPixelName.SYNC_VALIDATION_ERROR_DAILY.pixelName, feature.field),
                    type = Pixel.PixelType.Daily(),
                )
            }

            API_CODE.TOO_MANY_REQUESTS_1.code, API_CODE.TOO_MANY_REQUESTS_2.code -> {
                pixel.fire(
                    String.format(Locale.US, SyncPixelName.SYNC_TOO_MANY_REQUESTS_DAILY.pixelName, feature.field),
                    type = Pixel.PixelType.Daily(),
                )
            }
        }
    }

    private fun fireSyncAccountErrorPixel(result: Error) {
        result.fireAddingErrorAsParams(SyncPixelName.SYNC_ACCOUNT_FAILURE)
    }

    private fun fireSignupErrorPixel(result: Error) {
        result.fireAddingErrorAsParams(SyncPixelName.SYNC_SIGN_UP_FAILURE)
    }

    private fun fireLoginErrorPixel(result: Error) {
        result.fireAddingErrorAsParams(SyncPixelName.SYNC_LOGIN_FAILURE)
    }

    private fun fireLogoutErrorPixel(result: Error) {
        result.fireAddingErrorAsParams(SyncPixelName.SYNC_LOGOUT_FAILURE)
    }

    private fun fireUpdateDeviceErrorPixel(result: Error) {
        result.fireAddingErrorAsParams(SyncPixelName.SYNC_UPDATE_DEVICE_FAILURE)
    }

    private fun fireRemoveDeviceErrorPixel(result: Error) {
        result.fireAddingErrorAsParams(SyncPixelName.SYNC_REMOVE_DEVICE_FAILURE)
    }

    private fun fireDeleteAccountErrorPixel(result: Error) {
        result.fireAddingErrorAsParams(SyncPixelName.SYNC_DELETE_ACCOUNT_FAILURE)
    }

    private fun fireAlreadySignedInErrorPixel(result: Error) {
        result.fireAddingErrorAsParams(SyncPixelName.SYNC_USER_SIGNED_IN_FAILURE)
    }

    private fun fireSaveRecoveryPdfErrorPixel(result: Error) {
        result.fireAddingErrorAsParams(SyncPixelName.SYNC_CREATE_PDF_FAILURE)
    }

    private fun fireRescopeTokenErrorPixel(result: Error) {
        result.fireAddingErrorAsParams(SyncPixelName.SYNC_RESCOPE_TOKEN_FAILURE)
    }

    private fun Error.fireAddingErrorAsParams(pixelName: SyncPixelName) {
        pixel.fire(
            pixelName,
            mapOf(
                SyncPixelParameters.ERROR_CODE to this.code.toString(),
                SyncPixelParameters.ERROR_REASON to this.reason,
            ),
        )
    }

    private fun tryToFireDailyPixel(
        pixel: SyncPixelName,
        payload: Map<String, String> = emptyMap(),
    ) {
        val now = getUtcIsoLocalDate()
        val timestamp = preferences.getString(pixel.name.appendTimestampSuffix(), null)

        // check if pixel was already sent in the current day
        if (timestamp == null || now > timestamp) {
            this.pixel.fire(pixel, payload)
                .also { preferences.edit { putString(pixel.name.appendTimestampSuffix(), now) } }
        }
    }

    private fun getUtcIsoLocalDate(): String {
        // returns YYYY-MM-dd
        return Instant.now().atOffset(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private fun String.appendTimestampSuffix(): String {
        return "${this}_timestamp"
    }

    private fun buildSourceMap(source: String?): Map<String, String> {
        return if (source != null) {
            mapOf(SYNC_FEATURE_PROMOTION_SOURCE to source)
        } else {
            emptyMap()
        }
    }

    override fun fireUserSwitchedAccount() {
        pixel.fire(SyncPixelName.SYNC_USER_SWITCHED_ACCOUNT)
    }

    override fun fireAskUserToSwitchAccount() {
        pixel.fire(SyncPixelName.SYNC_ASK_USER_TO_SWITCH_ACCOUNT)
    }

    override fun fireUserAcceptedSwitchingAccount() {
        pixel.fire(SyncPixelName.SYNC_USER_ACCEPTED_SWITCHING_ACCOUNT)
    }

    override fun fireUserCancelledSwitchingAccount() {
        pixel.fire(SyncPixelName.SYNC_USER_CANCELLED_SWITCHING_ACCOUNT)
    }

    override fun fireUserSwitchedLoginError() {
        pixel.fire(SyncPixelName.SYNC_USER_SWITCHED_LOGIN_ERROR)
    }

    override fun fireTimeoutOnDeepLinkSetup() {
        pixel.fire(SyncPixelName.SYNC_SETUP_DEEP_LINK_TIMEOUT)
    }

    override fun fireUserSwitchedLogoutError() {
        pixel.fire(SyncPixelName.SYNC_USER_SWITCHED_LOGOUT_ERROR)
    }

    override fun fireSetupDeepLinkFlowStarted() {
        pixel.fire(SyncPixelName.SYNC_SETUP_DEEP_LINK_FLOW_STARTED)
    }

    override fun fireSetupDeepLinkFlowSuccess() {
        pixel.fire(SyncPixelName.SYNC_SETUP_DEEP_LINK_FLOW_SUCCESS)
    }

    override fun fireSetupDeepLinkFlowAbandoned() {
        pixel.fire(SyncPixelName.SYNC_SETUP_DEEP_LINK_FLOW_ABANDONED)
    }

    override fun fireUserConfirmedToTurnOffSync() {
        pixel.fire(SyncPixelName.SYNC_USER_CONFIRMED_TO_TURN_OFF_SYNC)
    }

    override fun fireUserConfirmedToTurnOffSyncAndDelete(connectedDevices: Int) {
        val params = mapOf(CONNECTED_DEVICES_WHEN_DELETING to connectedDevices.toString())
        pixel.fire(SyncPixelName.SYNC_USER_CONFIRMED_TO_TURN_OFF_SYNC_AND_DELETE, parameters = params)
    }

    override fun fireSetupSyncPromoBookmarkAddedDialogDismissed() {
        pixel.fire(SyncPixelName.SYNC_SETUP_PROMO_BOOKMARK_ADDED_DIALOG_DISMISSED)
    }

    override fun fireSetupSyncPromoBookmarkAddedDialogConfirmed() {
        pixel.fire(SyncPixelName.SYNC_SETUP_PROMO_BOOKMARK_ADDED_DIALOG_CONFIRMED)
    }

    override fun fireSetupSyncPromoBookmarkAddedDialogShown() {
        pixel.fire(SyncPixelName.SYNC_SETUP_PROMO_BOOKMARK_ADDED_DIALOG_SHOWN)
    }

    override fun fireSyncBarcodeScreenShown(screenType: ScreenType) {
        val params = mapOf(SYNC_SETUP_SCREEN_TYPE to screenType.value) + setupFlowMetadata()
        pixel.fire(SyncPixelName.SYNC_SETUP_BARCODE_SCREEN_SHOWN, parameters = params)
    }

    override fun fireSyncSetupAbandoned(screenType: ScreenType, reason: CancellationReason?) {
        val params = buildMap {
            put(SYNC_SETUP_SCREEN_TYPE, screenType.value)
            if (reason != null) put(SYNC_SETUP_REASON, reason.value)
            putAll(setupFlowMetadata())
        }
        pixel.fire(SyncPixelName.SYNC_SETUP_ENDED_ABANDONED, parameters = params)
    }

    override fun fireSyncSetupFinishedSuccessfully(
        screenType: ScreenType,
        path: SetupPath?,
        myRole: SetupRole?,
        peerKind: PeerKind?,
    ) {
        val params = setupSuccessParams(screenType, path, myRole, peerKind)
        pixel.fire(SyncPixelName.SYNC_SETUP_ENDED_SUCCESS, parameters = params)
    }

    override fun fireSyncSetupFailed(
        screenType: ScreenType,
        reason: SetupFailureReason,
        path: SetupPath?,
        myRole: SetupRole?,
        peerKind: PeerKind?,
        timeoutStage: TimeoutStage?,
    ) {
        val params = buildMap {
            put(SYNC_SETUP_SCREEN_TYPE, screenType.value)
            put(SYNC_SETUP_REASON, reason.value)
            if (path != null) put(SYNC_SETUP_PATH, path.value)
            if (myRole != null) put(SYNC_SETUP_MY_ROLE, myRole.value)
            if (peerKind != null) put(SYNC_SETUP_PEER_KIND, peerKind.value)
            if (timeoutStage != null) put(SYNC_SETUP_TIMEOUT_STAGE, timeoutStage.value)
            putAll(setupFlowMetadata())
        }
        pixel.fire(SyncPixelName.SYNC_SETUP_ENDED_FAILED, parameters = params)
    }

    override fun fireSyncSetupManualCodeScreenShown(screenType: ScreenType) {
        val params = mapOf(SYNC_SETUP_SCREEN_TYPE to screenType.value) + setupFlowMetadata()
        pixel.fire(SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTRY_SCREEN_SHOWN, parameters = params)
    }

    override fun fireSyncSetupCodePastedParseSuccess(screenType: ScreenType, codeVersion: CodeVersion, codeType: SyncCodeType?) {
        val params = recognizedCodeParams(screenType, codeVersion, codeType)
        pixel.fire(SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTERED_SUCCESS, parameters = params)
    }

    override fun fireSyncSetupCodePastedParseFailure(screenType: ScreenType, reason: SetupFailureReason?) {
        val params = buildMap {
            put(SYNC_SETUP_SCREEN_TYPE, screenType.value)
            if (reason != null) put(SYNC_SETUP_REASON, reason.value)
            putAll(setupFlowMetadata())
        }
        pixel.fire(SyncPixelName.SYNC_SETUP_MANUAL_CODE_ENTERED_FAILED, parameters = params)
    }

    override fun fireSyncSetupCodeCopiedToClipboard(screenType: ScreenType) {
        val params = mapOf(SYNC_SETUP_SCREEN_TYPE to screenType.value)
        pixel.fire(SyncPixelName.SYNC_SETUP_BARCODE_CODE_COPIED, parameters = params)
    }

    override fun fireBarcodeScannerParseSuccess(screenType: ScreenType, codeVersion: CodeVersion, codeType: SyncCodeType?) {
        val params = recognizedCodeParams(screenType, codeVersion, codeType)
        pixel.fire(SyncPixelName.SYNC_SETUP_BARCODE_SCANNER_SUCCESS, parameters = params)
    }

    override fun fireBarcodeScannerParseError(screenType: ScreenType, reason: SetupFailureReason?) {
        val params = buildMap {
            put(SYNC_SETUP_SCREEN_TYPE, screenType.value)
            if (reason != null) put(SYNC_SETUP_REASON, reason.value)
            putAll(setupFlowMetadata())
        }
        pixel.fire(SyncPixelName.SYNC_SETUP_BARCODE_SCANNER_FAILED, parameters = params)
    }

    override fun fireAiChatActive() {
        pixel.fire(SyncPixelName.SYNC_AI_CHAT_ACTIVE, type = Pixel.PixelType.Daily())
    }

    override fun fireAiChatsRescopeTokenError(error: Error) {
        // Skip 401 errors - let FE handle those
        if (error.code == API_CODE.INVALID_LOGIN_CREDENTIALS.code) return

        // Reuse existing daily error pixel logic for AI Chats
        fireDailySyncApiErrorPixel(DeletableType.DUCK_AI_CHATS, error)
    }

    override fun fireAutoRestoreSetupToggleShown() {
        pixel.fire(SyncPixelName.SYNC_AUTO_RESTORE_TOGGLE_SHOWN)
    }

    override fun fireAutoRestoreSetupToggleOptedOut() {
        pixel.fire(SyncPixelName.SYNC_AUTO_RESTORE_TOGGLE_OPTED_OUT)
    }

    override fun fireAutoRestoreSettingsReadyShown(source: String) {
        pixel.fire(SyncPixelName.SYNC_AUTO_RESTORE_SETTINGS_READY_SHOWN, mapOf(SyncPixelParameters.AUTO_RESTORE_SOURCE to source))
    }

    override fun fireAutoRestoreSettingsRestoreTapped(source: String) {
        pixel.fire(SyncPixelName.SYNC_AUTO_RESTORE_SETTINGS_RESTORE_TAPPED, mapOf(SyncPixelParameters.AUTO_RESTORE_SOURCE to source))
    }

    override fun fireAutoRestoreSettingsSkipRestoreTapped(source: String) {
        pixel.fire(SyncPixelName.SYNC_AUTO_RESTORE_SETTINGS_SKIP_RESTORE_TAPPED, mapOf(SyncPixelParameters.AUTO_RESTORE_SOURCE to source))
    }

    override fun fireAutoRestoreSettingsCancelled(source: String) {
        pixel.fire(SyncPixelName.SYNC_AUTO_RESTORE_SETTINGS_CANCELLED, mapOf(SyncPixelParameters.AUTO_RESTORE_SOURCE to source))
    }

    override fun fireAutoRestoreSettingsManualRecoveryShown() {
        pixel.fire(SyncPixelName.SYNC_AUTO_RESTORE_SETTINGS_MANUAL_RECOVERY_SHOWN)
    }

    override fun fireAutoRestoreSettingsPageShown() {
        pixel.fire(SyncPixelName.SYNC_AUTO_RESTORE_SETTINGS_PAGE_SHOWN)
    }

    override fun fireAutoRestoreSettingsPageToggleEnabled() {
        pixel.fire(SyncPixelName.SYNC_AUTO_RESTORE_SETTINGS_PAGE_TOGGLE_ENABLED)
    }

    override fun fireAutoRestoreSettingsPageToggleDisabled() {
        pixel.fire(SyncPixelName.SYNC_AUTO_RESTORE_SETTINGS_PAGE_TOGGLE_DISABLED)
    }

    override fun fireAutoRestoreSuccess(source: String) {
        pixel.fire(SyncPixelName.SYNC_AUTO_RESTORE_SUCCESS, mapOf(SyncPixelParameters.AUTO_RESTORE_SOURCE to source))
    }

    override fun fireAutoRestoreFailure(source: String, errorCode: String, errorMessage: String) {
        pixel.fire(
            SyncPixelName.SYNC_AUTO_RESTORE_FAILURE,
            mapOf(
                SyncPixelParameters.AUTO_RESTORE_SOURCE to source,
                SyncPixelParameters.AUTO_RESTORE_ERROR_CODE to errorCode,
                SyncPixelParameters.AUTO_RESTORE_ERROR_MESSAGE to errorMessage,
            ),
        )
    }

    override fun fireAutoRestorePreservedAccountCleared(source: String) {
        pixel.fire(SyncPixelName.SYNC_AUTO_RESTORE_PRESERVED_ACCOUNT_CLEARED, mapOf(SyncPixelParameters.AUTO_RESTORE_SOURCE to source))
    }

    override fun fireAutoRestorePreservedAccountClearFailed(source: String, errorCode: String, errorMessage: String) {
        pixel.fire(
            SyncPixelName.SYNC_AUTO_RESTORE_PRESERVED_ACCOUNT_CLEAR_FAILED,
            mapOf(
                SyncPixelParameters.AUTO_RESTORE_SOURCE to source,
                SyncPixelParameters.AUTO_RESTORE_ERROR_CODE to errorCode,
                SyncPixelParameters.AUTO_RESTORE_ERROR_MESSAGE to errorMessage,
            ),
        )
    }

    companion object {
        private const val SYNC_PIXELS_PREF_FILE = "com.duckduckgo.sync.pixels.v1"
        private const val FLOW_VERSION_V1 = "v1"
        private const val FLOW_VERSION_V2 = "v2"
        private const val MY_KIND_DDG = "ddg"
        private const val CODE_TYPE_RECOVERY = "recovery"
        private const val CODE_TYPE_LINKING = "linking"
    }
}

enum class SyncAccountOperation {
    GENERIC,
    SIGNUP,
    LOGIN,
    LOGOUT,
    UPDATE_DEVICE,
    REMOVE_DEVICE,
    DELETE_ACCOUNT,
    USER_SIGNED_IN,
    CREATE_PDF,
    RESCOPE_TOKEN,
}

// https://app.asana.com/0/72649045549333/1205649300615861
enum class SyncPixelName(override val pixelName: String) : Pixel.PixelName {
    SYNC_DAILY("m_sync_daily"),
    SYNC_DAILY_SUCCESS_RATE_PIXEL("m_sync_success_rate_daily"),
    SYNC_TIMESTAMP_RESOLUTION_TRIGGERED("m_sync_%s_local_timestamp_resolution_triggered"),
    SYNC_LOGIN("m_sync_login"),
    SYNC_SIGNUP_DIRECT("m_sync_signup_direct"),
    SYNC_SIGNUP_CONNECT("m_sync_signup_connect"),
    SYNC_ACCOUNT_FAILURE("m_sync_account_failure"),
    SYNC_SIGN_UP_FAILURE("m_sync_signup_error"),
    SYNC_LOGIN_FAILURE("m_sync_login_error"),
    SYNC_LOGOUT_FAILURE("m_sync_logout_error"),
    SYNC_UPDATE_DEVICE_FAILURE("m_update_device_error"),
    SYNC_REMOVE_DEVICE_FAILURE("m_remove_device_error"),
    SYNC_DELETE_ACCOUNT_FAILURE("m_delete_account_error"),
    SYNC_USER_SIGNED_IN_FAILURE("m_login_existing_account_error"),
    SYNC_CREATE_PDF_FAILURE("m_sync_create_recovery_pdf_error"),
    SYNC_RESCOPE_TOKEN_FAILURE("m_sync_rescope_token_error"),
    SYNC_PATCH_COMPRESS_FAILED("m_sync_patch_compression_failed"),
    SYNC_TOO_MANY_REQUESTS_DAILY("m_sync_%s_too_many_requests_daily"),
    SYNC_OBJECT_LIMIT_EXCEEDED_DAILY("m_sync_%s_object_limit_exceeded_daily"),
    SYNC_REQUEST_SIZE_LIMIT_EXCEEDED_DAILY("m_sync_%s_request_size_limit_exceeded_daily"),
    SYNC_VALIDATION_ERROR_DAILY("m_sync_%s_validation_error_daily"),

    SYNC_FEATURE_PROMOTION_DISPLAYED("sync_promotion_displayed"),
    SYNC_FEATURE_PROMOTION_CONFIRMED("sync_promotion_confirmed"),
    SYNC_FEATURE_PROMOTION_DISMISSED("sync_promotion_dismissed"),

    SYNC_GET_OTHER_DEVICES_SCREEN_SHOWN("sync_get_other_devices"),
    SYNC_GET_OTHER_DEVICES_LINK_COPIED("sync_get_other_devices_copy"),
    SYNC_GET_OTHER_DEVICES_LINK_SHARED("sync_get_other_devices_share"),
    SYNC_ASK_USER_TO_SWITCH_ACCOUNT("sync_ask_user_to_switch_account"),
    SYNC_USER_ACCEPTED_SWITCHING_ACCOUNT("sync_user_accepted_switching_account"),
    SYNC_USER_CANCELLED_SWITCHING_ACCOUNT("sync_user_cancelled_switching_account"),
    SYNC_USER_SWITCHED_ACCOUNT("sync_user_switched_account"),
    SYNC_USER_SWITCHED_LOGOUT_ERROR("sync_user_switched_logout_error"),
    SYNC_USER_SWITCHED_LOGIN_ERROR("sync_user_switched_login_error"),
    SYNC_SETUP_DEEP_LINK_TIMEOUT("sync_setup_deep_link_timeout"),
    SYNC_SETUP_DEEP_LINK_FLOW_STARTED("sync_setup_deep_link_flow_started"),
    SYNC_SETUP_DEEP_LINK_FLOW_SUCCESS("sync_setup_deep_link_flow_success"),
    SYNC_SETUP_DEEP_LINK_FLOW_ABANDONED("sync_setup_deep_link_flow_abandoned"),

    SYNC_SETUP_BARCODE_SCREEN_SHOWN("sync_setup_barcode_screen_shown"),
    SYNC_SETUP_BARCODE_SCANNER_SUCCESS("sync_setup_barcode_scanner_success"),
    SYNC_SETUP_BARCODE_SCANNER_FAILED("sync_setup_barcode_scanner_failed"),
    SYNC_SETUP_BARCODE_CODE_COPIED("sync_setup_barcode_code_copied"),
    SYNC_SETUP_MANUAL_CODE_ENTRY_SCREEN_SHOWN("sync_setup_manual_code_entry_screen_shown"),
    SYNC_SETUP_MANUAL_CODE_ENTERED_SUCCESS("sync_setup_manual_code_entered_success"),
    SYNC_SETUP_MANUAL_CODE_ENTERED_FAILED("sync_setup_manual_code_entered_failed"),
    SYNC_SETUP_ENDED_ABANDONED("sync_setup_ended_abandoned"),
    SYNC_SETUP_ENDED_SUCCESS("sync_setup_ended_successful"),
    SYNC_SETUP_ENDED_FAILED("sync_setup_ended_failed"),
    SYNC_USER_CONFIRMED_TO_TURN_OFF_SYNC("sync_disabled"),
    SYNC_USER_CONFIRMED_TO_TURN_OFF_SYNC_AND_DELETE("sync_disabledanddeleted"),
    SYNC_SETUP_PROMO_BOOKMARK_ADDED_DIALOG_SHOWN("sync_setup_promo_bookmark_added_dialog_shown"),
    SYNC_SETUP_PROMO_BOOKMARK_ADDED_DIALOG_DISMISSED("sync_setup_promo_bookmark_added_dialog_dismissed"),
    SYNC_SETUP_PROMO_BOOKMARK_ADDED_DIALOG_CONFIRMED("sync_setup_promo_bookmark_added_dialog_confirmed"),
    SYNC_AI_CHAT_ACTIVE("sync_ai_chat_active"),

    SYNC_AUTO_RESTORE_TOGGLE_SHOWN("sync-auto-restore_toggle_shown"),
    SYNC_AUTO_RESTORE_TOGGLE_OPTED_OUT("sync-auto-restore_toggle_opted_out"),
    SYNC_AUTO_RESTORE_SETTINGS_READY_SHOWN("sync-auto-restore_settings_ready_shown"),
    SYNC_AUTO_RESTORE_SETTINGS_RESTORE_TAPPED("sync-auto-restore_settings_restore_tapped"),
    SYNC_AUTO_RESTORE_SETTINGS_SKIP_RESTORE_TAPPED("sync-auto-restore_settings_skip_restore_tapped"),
    SYNC_AUTO_RESTORE_SETTINGS_CANCELLED("sync-auto-restore_settings_cancelled"),
    SYNC_AUTO_RESTORE_SETTINGS_MANUAL_RECOVERY_SHOWN("sync-auto-restore_settings_manual_recovery_shown"),
    SYNC_AUTO_RESTORE_SETTINGS_PAGE_SHOWN("sync-auto-restore_settings_page_shown"),
    SYNC_AUTO_RESTORE_SETTINGS_PAGE_TOGGLE_ENABLED("sync-auto-restore_settings_page_toggle_enabled"),
    SYNC_AUTO_RESTORE_SETTINGS_PAGE_TOGGLE_DISABLED("sync-auto-restore_settings_page_toggle_disabled"),
    SYNC_AUTO_RESTORE_SUCCESS("sync-auto-restore_success"),
    SYNC_AUTO_RESTORE_FAILURE("sync-auto-restore_failure"),
    SYNC_AUTO_RESTORE_PRESERVED_ACCOUNT_CLEARED("sync-auto-restore_preserved_account_cleared"),
    SYNC_AUTO_RESTORE_PRESERVED_ACCOUNT_CLEAR_FAILED("sync-auto-restore_preserved_account_clear_failed"),
}

object SyncPixelParameters {
    const val COUNT = "sync_count"
    const val DATE = "date"
    const val OBJECT_LIMIT_EXCEEDED_COUNT = "%s_object_limit_exceeded_count"
    const val REQUEST_SIZE_LIMIT_EXCEEDED_COUNT = "%s_request_size_limit_exceeded_count"
    const val VALIDATION_ERROR_COUNT = "%s_validation_error"
    const val TOO_MANY_REQUESTS = "%s_too_many_requests_count"
    const val DATA_ENCRYPT_ERROR = "encrypt_error_count"
    const val DATA_DECRYPT_ERROR = "decrypt_error_count"
    const val DATA_PERSISTER_ERROR_PARAM = "%s_persister_error_count"
    const val DATA_PROVIDER_ERROR_PARAM = "%s_provider_error_count"
    const val TIMESTAMP_CONFLICT = "%s_local_timestamp_resolution_triggered"
    const val ORPHANS_PRESENT = "%s_orphans_present"
    const val ERROR_CODE = "code"
    const val ERROR_REASON = "reason"
    const val ERROR = "error"
    const val SYNC_FEATURE_PROMOTION_SOURCE = "source"
    const val SYNC_FEATURE_PROMOTION_DISMISS_REASON = "reason"
    const val GET_OTHER_DEVICES_SCREEN_LAUNCH_SOURCE = "source"
    const val SYNC_SETUP_SCREEN_TYPE = "source"
    const val SYNC_SETUP_FLOW_VERSION = "flow_version"
    const val SYNC_SETUP_MY_KIND = "my_kind"
    const val SYNC_SETUP_CODE_VERSION = "code_version"
    const val SYNC_SETUP_CODE_TYPE = "code_type"
    const val SYNC_SETUP_PATH = "path"
    const val SYNC_SETUP_MY_ROLE = "my_role"
    const val SYNC_SETUP_PEER_KIND = "peer_kind"
    const val SYNC_SETUP_REASON = "reason"
    const val SYNC_SETUP_TIMEOUT_STAGE = "timeout_stage"
    const val CONNECTED_DEVICES_WHEN_DELETING = "connected_devices"

    const val AUTO_RESTORE_SOURCE = "source"
    const val AUTO_RESTORE_ERROR_CODE = "errorCode"
    const val AUTO_RESTORE_ERROR_MESSAGE = "errorMessage"
    const val AUTO_RESTORE_SOURCE_PAIRING = "sync_pairing"
    const val AUTO_RESTORE_SOURCE_BACKUP = "sync_backup"
    const val AUTO_RESTORE_SOURCE_RECOVER = "sync_recover"
    const val AUTO_RESTORE_SOURCE_ONBOARDING = "onboarding"
    const val AUTO_RESTORE_SOURCE_SETTINGS = "settings"
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelParamRemovalPlugin::class,
)
object SyncPixelsRequiringDataCleaning : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            SyncPixelName.SYNC_USER_CONFIRMED_TO_TURN_OFF_SYNC.pixelName to removeAtb(),
            SyncPixelName.SYNC_USER_CONFIRMED_TO_TURN_OFF_SYNC_AND_DELETE.pixelName to removeAtb(),
            SyncPixelName.SYNC_SETUP_PROMO_BOOKMARK_ADDED_DIALOG_SHOWN.pixelName to removeAtb(),
            SyncPixelName.SYNC_SETUP_PROMO_BOOKMARK_ADDED_DIALOG_DISMISSED.pixelName to removeAtb(),
            SyncPixelName.SYNC_SETUP_PROMO_BOOKMARK_ADDED_DIALOG_CONFIRMED.pixelName to removeAtb(),
            SyncPixelName.SYNC_RESCOPE_TOKEN_FAILURE.pixelName to removeAtb(),
            SyncPixelName.SYNC_AI_CHAT_ACTIVE.pixelName to removeAtb(),
        )
    }
}

internal fun Int.toSetupFailureReason(): SetupFailureReason = when (this) {
    AccountErrorCodes.SESSION_TIMEOUT.code -> SetupFailureReason.SESSION_TIMEOUT
    AccountErrorCodes.THIRD_PARTY_ALREADY_UPGRADED.code -> SetupFailureReason.ALREADY_UPGRADED
    AccountErrorCodes.ALREADY_PAIRED.code -> SetupFailureReason.ALREADY_PAIRED
    AccountErrorCodes.LOGIN_FAILED.code,
    AccountErrorCodes.INVALID_CODE.code,
    AccountErrorCodes.EXCHANGE_FAILED.code,
    -> SetupFailureReason.INVALID_CREDENTIALS
    AccountErrorCodes.CREATE_ACCOUNT_FAILED.code -> SetupFailureReason.ACCOUNT_CREATION_FAILED
    AccountErrorCodes.ACCOUNT_UPGRADE_FAILED.code -> SetupFailureReason.ACCOUNT_UPGRADE_FAILED
    AccountErrorCodes.RECOVERY_CODE_PREPARATION_FAILED.code -> SetupFailureReason.RECOVERY_CODE_PREPARATION_FAILED
    AccountErrorCodes.MISSING_3PARTY_CREDENTIAL.code -> SetupFailureReason.MISSING_3PARTY_CREDENTIAL
    AccountErrorCodes.UNDECRYPTABLE_3PARTY_CREDENTIAL.code -> SetupFailureReason.UNDECRYPTABLE_3PARTY_CREDENTIAL
    AccountErrorCodes.ACCOUNT_EXTEND_FAILED.code -> SetupFailureReason.ACCOUNT_EXTEND_FAILED
    AccountErrorCodes.MISSING_3PARTY_KEY.code -> SetupFailureReason.MISSING_3PARTY_KEY
    AccountErrorCodes.LOCAL_STORAGE_FAILED.code -> SetupFailureReason.LOCAL_STORAGE_FAILED
    AccountErrorCodes.PEER_RECOVERY_CODE_UNAVAILABLE.code -> SetupFailureReason.PEER_RECOVERY_CODE_UNAVAILABLE
    AccountErrorCodes.UNEXPECTED_SECOND_HELLO.code -> SetupFailureReason.UNEXPECTED_SECOND_HELLO
    AccountErrorCodes.UNEXPECTED_EVENT.code -> SetupFailureReason.UNEXPECTED_EVENT
    AccountErrorCodes.PAIRING_SESSION_NOT_READY.code -> SetupFailureReason.PAIRING_SESSION_NOT_READY
    AccountErrorCodes.RELAY_CHANNEL_UNAVAILABLE.code -> SetupFailureReason.RELAY_CHANNEL_UNAVAILABLE
    AccountErrorCodes.PAIRING_UNAVAILABLE.code,
    AccountErrorCodes.NEGOTIATION_ABORTED.code,
    AccountErrorCodes.NO_RECOVERY_CODE.code,
    -> SetupFailureReason.PROTOCOL_ERROR
    AccountErrorCodes.PAIRING_FAILED.code -> SetupFailureReason.TRANSPORT_FAILURE
    else -> SetupFailureReason.UNEXPECTED_FAILURE
}

/**
 * Fire the "Setup failed" pixel for a v2 error [outcome]. No-op for non-error outcomes. User
 * cancellations (`PAIRING_CANCELLED`, `PAIRING_REJECTED`) are intentionally skipped — they are not
 * setup errors (the latter pending the cancellation-telemetry follow-up).
 */

internal fun SyncPixels.fireSetupFailed(screenType: ScreenType, outcome: DispatchOutcome) {
    when (outcome) {
        is DispatchOutcome.UpgradeRequired ->
            fireSyncSetupFailed(screenType, SetupFailureReason.NEEDS_UPGRADE, outcome.path, outcome.myRole, outcome.peerKind)
        is DispatchOutcome.AlreadyConnected ->
            // v2 same-account case: both devices exchanged intros and discovered a matching user_id.
            // Per spec, we report a failed pixel with reason=already_paired even though the user-facing
            // outcome is a benign "Connected" — the flow did not produce a new pairing.
            fireSyncSetupFailed(screenType, SetupFailureReason.ALREADY_PAIRED, path = SetupPath.PAIRING)
        is DispatchOutcome.Failed -> {
            if (outcome.code == AccountErrorCodes.PAIRING_CANCELLED.code || outcome.code == AccountErrorCodes.PAIRING_REJECTED.code) {
                return
            }
            fireSyncSetupFailed(
                screenType,
                outcome.code.toSetupFailureReason(),
                outcome.path,
                outcome.myRole,
                outcome.peerKind,
                timeoutStage = outcome.timeoutStage,
            )
        }
        else -> {}
    }
}

// Both devices fire the "abandoned" pixel on a v2 confirmation denial — from each device's POV the
// setup ended via denial. PAIRING_CANCELLED is this device's own denial; PAIRING_REJECTED is the peer's
// denial received on the wire.
internal fun SyncPixels.fireSetupCancelledIfDenied(screenType: ScreenType, outcome: DispatchOutcome) {
    if (outcome is DispatchOutcome.Failed &&
        (outcome.code == AccountErrorCodes.PAIRING_CANCELLED.code || outcome.code == AccountErrorCodes.PAIRING_REJECTED.code)
    ) {
        fireSyncSetupAbandoned(screenType, CancellationReason.CONFIRMATION_DENIED)
    }
}
