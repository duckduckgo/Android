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
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.AccountErrorCodes.CONNECT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.CREATE_ACCOUNT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.INVALID_CODE
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result.Error

/** Dialog copy for a v1 pairing terminal outcome. */
internal data class V1PairingErrorContent(
    @StringRes val message: Int,
    val reason: String,
)

/**
 * Maps a v1 pairing error code to dialog copy.
 */
internal fun Error.toV1PairingError(): V1PairingErrorContent {
    val message = when (code) {
        ALREADY_SIGNED_IN.code -> R.string.sync_login_authenticated_device_error
        LOGIN_FAILED.code -> R.string.sync_connect_login_error
        CONNECT_FAILED.code -> R.string.sync_connect_generic_error
        CREATE_ACCOUNT_FAILED.code -> R.string.sync_create_account_generic_error
        INVALID_CODE.code -> R.string.sync_invalid_code_error
        else -> R.string.sync_simplified_pairing_failed_generic_message
    }
    return V1PairingErrorContent(message, reason)
}

/**
 * Renders a v1 pairing error dialog: hardcoded title, and a provided message with a reason.
 */
internal fun Context.showV1PairingError(
    content: V1PairingErrorContent,
    onDismissed: () -> Unit,
) {
    TextAlertDialogBuilder(this)
        .setTitle(R.string.sync_dialog_error_title)
        .setMessage(getString(content.message) + "\n" + content.reason)
        .setPositiveButton(R.string.sync_dialog_error_ok)
        .addEventListener(
            object : TextAlertDialogBuilder.EventListener() {
                override fun onPositiveButtonClicked() {
                    onDismissed()
                }
            },
        )
        .show()
}
