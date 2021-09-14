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

package com.duckduckgo.app.email.ui

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R

class EmailLogoutDialog : DialogFragment() {

    var onLogout: (() -> Unit) = {}

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertBuilder = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setMessage(R.string.emailSignOutDialogText)
            .setTitle(getString(R.string.emailSignOutDialogTitle))
            .setNegativeButton(R.string.emailSignOutDialogCancel) { _, _ ->
                dismiss()
            }
            .setPositiveButton(R.string.emailSignOutDialogDisable) { _, _ ->
                dialog?.let {
                    onLogout()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }

        return alertBuilder.create()
    }

    companion object {
        fun create(): EmailLogoutDialog = EmailLogoutDialog()
    }

}
