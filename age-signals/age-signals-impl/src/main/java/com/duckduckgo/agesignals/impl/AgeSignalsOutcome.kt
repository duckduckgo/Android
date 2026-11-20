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

package com.duckduckgo.agesignals.impl

import com.google.android.play.agesignals.model.AgeSignalsErrorCode

internal sealed interface AgeSignalsOutcome {
    data object NotApplicable : AgeSignalsOutcome
    data object NotShared : AgeSignalsOutcome
    data object VerificationRequired : AgeSignalsOutcome
    data object Shared : AgeSignalsOutcome
    data object UnknownStatus : AgeSignalsOutcome
    data class Failed(val step: Step, val error: AgeSignalsError) : AgeSignalsOutcome
}

internal enum class Step { APPLICABLE_FEATURES, REQUEST_ACCESS, CHECK_SIGNALS }

internal enum class AgeSignalsError(val code: Int) {
    API_NOT_AVAILABLE(AgeSignalsErrorCode.API_NOT_AVAILABLE),
    PLAY_STORE_NOT_FOUND(AgeSignalsErrorCode.PLAY_STORE_NOT_FOUND),
    NETWORK_ERROR(AgeSignalsErrorCode.NETWORK_ERROR),
    PLAY_SERVICES_NOT_FOUND(AgeSignalsErrorCode.PLAY_SERVICES_NOT_FOUND),
    CANNOT_BIND_TO_SERVICE(AgeSignalsErrorCode.CANNOT_BIND_TO_SERVICE),
    PLAY_STORE_VERSION_OUTDATED(AgeSignalsErrorCode.PLAY_STORE_VERSION_OUTDATED),
    PLAY_SERVICES_VERSION_OUTDATED(AgeSignalsErrorCode.PLAY_SERVICES_VERSION_OUTDATED),
    CLIENT_TRANSIENT_ERROR(AgeSignalsErrorCode.CLIENT_TRANSIENT_ERROR),
    APP_NOT_OWNED(AgeSignalsErrorCode.APP_NOT_OWNED),
    SDK_VERSION_OUTDATED(AgeSignalsErrorCode.SDK_VERSION_OUTDATED),
    DEVICE_NOT_SUPPORTED(AgeSignalsErrorCode.DEVICE_NOT_SUPPORTED),
    INTERNAL_ERROR(AgeSignalsErrorCode.INTERNAL_ERROR),
    UNKNOWN(Int.MIN_VALUE),
}
