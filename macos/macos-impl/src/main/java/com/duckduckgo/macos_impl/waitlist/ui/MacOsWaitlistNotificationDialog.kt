/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.macos_impl.waitlist.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.macos_impl.R

class MacOsWaitlistNotificationDialog : DialogFragment() {

    var onNotifyClicked: (() -> Unit) = {}
    var onNoThanksClicked: (() -> Unit) = {}
    var onDialogDismissed: (() -> Unit) = {}
    var onDialogCreated: (() -> Unit) = {}

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertBuilder = AlertDialog.Builder(requireActivity())
            .setMessage(R.string.macos_notification_dialog_description)
            .setNegativeButton(R.string.macos_notification_dialog_no_thanks) { _, _ ->
                onNoThanksClicked()
                dismiss()
            }
            .setPositiveButton(R.string.macos_notification_dialog_notify_me) { _, _ ->
                onNotifyClicked()
            }

        onDialogCreated()
        return alertBuilder.create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDialogDismissed()
    }

    companion object {
        fun create(): MacOsWaitlistNotificationDialog = MacOsWaitlistNotificationDialog()
    }
}
