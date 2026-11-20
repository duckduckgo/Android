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

sealed interface AgeSignalsOutcome {
    data object NotApplicable : AgeSignalsOutcome
    data object NotShared : AgeSignalsOutcome
    data object VerificationRequired : AgeSignalsOutcome
    data object Shared : AgeSignalsOutcome
    data object UnknownStatus : AgeSignalsOutcome
    data class Failed(val step: Step, val error: AgeSignalsError) : AgeSignalsOutcome
}

enum class Step { APPLICABLE_FEATURES, REQUEST_ACCESS, CHECK_SIGNALS }

enum class AgeSignalsError(val code: Int, val pixelValue: String) {
    API_NOT_AVAILABLE(AgeSignalsErrorCode.API_NOT_AVAILABLE, "-1"),
    PLAY_STORE_NOT_FOUND(AgeSignalsErrorCode.PLAY_STORE_NOT_FOUND, "-2"),
    NETWORK_ERROR(AgeSignalsErrorCode.NETWORK_ERROR, "-3"),
    PLAY_SERVICES_NOT_FOUND(AgeSignalsErrorCode.PLAY_SERVICES_NOT_FOUND, "-4"),
    CANNOT_BIND_TO_SERVICE(AgeSignalsErrorCode.CANNOT_BIND_TO_SERVICE, "-5"),
    PLAY_STORE_VERSION_OUTDATED(AgeSignalsErrorCode.PLAY_STORE_VERSION_OUTDATED, "-6"),
    PLAY_SERVICES_VERSION_OUTDATED(AgeSignalsErrorCode.PLAY_SERVICES_VERSION_OUTDATED, "-7"),
    CLIENT_TRANSIENT_ERROR(AgeSignalsErrorCode.CLIENT_TRANSIENT_ERROR, "-8"),
    APP_NOT_OWNED(AgeSignalsErrorCode.APP_NOT_OWNED, "-9"),
    SDK_VERSION_OUTDATED(AgeSignalsErrorCode.SDK_VERSION_OUTDATED, "-10"),
    DEVICE_NOT_SUPPORTED(AgeSignalsErrorCode.DEVICE_NOT_SUPPORTED, "-11"),
    INTERNAL_ERROR(AgeSignalsErrorCode.INTERNAL_ERROR, "-100"),
    UNKNOWN(Int.MIN_VALUE, "unknown"),
    TIMEOUT(Int.MIN_VALUE + 1, "timeout"),
}
