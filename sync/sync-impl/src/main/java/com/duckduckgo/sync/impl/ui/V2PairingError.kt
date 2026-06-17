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

package com.duckduckgo.sync.impl.ui

import android.content.Context
import androidx.annotation.StringRes
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_CANCELLED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_REJECTED
import com.duckduckgo.sync.impl.AccountErrorCodes.THIRD_PARTY_ALREADY_UPGRADED
import com.duckduckgo.sync.impl.R

/** Dialog copy for a v2 pairing terminal outcome. A null [message] renders a title-only dialog. */
data class V2PairingErrorContent(
    @StringRes val title: Int,
    @StringRes val message: Int?,
)

/**
 * Maps a v2 pairing [com.duckduckgo.sync.impl.DispatchOutcome.Failed] error code to dialog copy.
 * The scenario line is the title; the message is a short supporting line or null (title-only).
 */
fun Int.toV2PairingError(): V2PairingErrorContent = when (this) {
    PAIRING_REJECTED.code -> V2PairingErrorContent(
        title = R.string.sync_v2_error_pairing_rejected,
        message = R.string.sync_v2_error_try_again,
    )
    PAIRING_CANCELLED.code -> V2PairingErrorContent(
        title = R.string.sync_v2_error_pairing_canceled,
        message = null,
    )
    THIRD_PARTY_ALREADY_UPGRADED.code -> V2PairingErrorContent(
        title = R.string.sync_v2_error_pairing_failed,
        message = R.string.sync_v2_error_third_party_already_upgraded,
    )
    else -> V2PairingErrorContent(
        title = R.string.sync_v2_error_pairing_failed,
        message = R.string.sync_v2_error_try_again,
    )
}

/** Fixed copy for [com.duckduckgo.sync.impl.DispatchOutcome.UpgradeRequired] outcome (title-only). */
val v2UpgradeRequiredError = V2PairingErrorContent(
    title = R.string.sync_v2_error_upgrade_required,
    message = null,
)

/** Fixed copy for the same-account [com.duckduckgo.sync.impl.DispatchOutcome.AlreadyConnected] outcome. */
val v2AlreadyPairedError = V2PairingErrorContent(
    title = R.string.sync_v2_already_paired_title,
    message = R.string.sync_v2_already_paired_message,
)

/**
 * Renders a v2 pairing error dialog: the scenario line as the title, an optional supporting
 * [V2PairingErrorContent.message], and the shared v2 "Got It" button. A null message yields a
 * title-only dialog (the builder hides the message view when it is empty). [onDismissed] runs on
 * the positive-button tap, mirroring the v1 path's `viewModel.onErrorDialogDismissed()`.
 */
fun Context.showV2PairingError(
    content: V2PairingErrorContent,
    onDismissed: () -> Unit,
) {
    TextAlertDialogBuilder(this)
        .setTitle(content.title)
        .apply { content.message?.let { setMessage(it) } }
        .setPositiveButton(R.string.sync_v2_error_got_it)
        .addEventListener(
            object : TextAlertDialogBuilder.EventListener() {
                override fun onPositiveButtonClicked() {
                    onDismissed()
                }
            },
        )
        .show()
}
