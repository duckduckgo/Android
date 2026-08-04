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

package com.duckduckgo.sync.impl.ui.v2

import android.content.Intent
import android.os.Parcelable
import androidx.core.content.IntentCompat
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.duckduckgo.sync.impl.ui.v2.SyncPairingResult.Role
import kotlinx.parcelize.Parcelize

/**
 * Terminal outcome of a sync-with-another-device attempt. Produced by the leaf activity
 * ([SyncAnotherDeviceActivity] or [DisplayQrCodeActivity]) and forwarded verbatim up the
 * back stack so that [SyncActivity] can decide which completion screen to show.
 */
sealed interface SyncPairingResult : Parcelable {

    @Parcelize
    data class Success(
        val device: ParcelableDevice,
        val role: Role?,
        val method: PairingMethod,
    ) : SyncPairingResult

    @Parcelize
    data object Failure : SyncPairingResult

    enum class Role {
        Host,
        Joiner,
    }

    enum class PairingMethod {
        ScannedCode,
        DisplayedCode,
    }

    companion object {
        const val RESULT_SYNC_COMPLETED = 210
        private const val PAIRING_RESULT_EXTRA_KEY = "sync_pairing_result"

        fun resultIntent(result: SyncPairingResult): Intent = Intent().putExtra(PAIRING_RESULT_EXTRA_KEY, result)

        fun fromIntent(intent: Intent): SyncPairingResult? =
            IntentCompat.getParcelableExtra(intent, PAIRING_RESULT_EXTRA_KEY, SyncPairingResult::class.java)
    }
}

internal fun DispatchOutcome.LoggedIn.toPairingRole(): Role? = when (path) {
    SetupPath.PAIRING -> when (myRole) {
        SetupRole.HOST -> Role.Host
        SetupRole.JOINER -> Role.Joiner
        null -> null
    }
    SetupPath.RECOVERY -> null
}
