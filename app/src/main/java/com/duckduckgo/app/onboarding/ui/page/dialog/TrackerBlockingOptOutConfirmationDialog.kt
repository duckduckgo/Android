/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R


class TrackerBlockingOptOutConfirmationDialog : DialogFragment() {

    interface OptOutConfirmationDialogListener {
        fun continueWithoutTrackerBlocking()
    }

    var listener: OptOutConfirmationDialogListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        return AlertDialog.Builder(requireContext())
            .setView(R.layout.tracker_blocking_disabled_confirmation_dialog)
            .setPositiveButton(R.string.onboardingContinue) { _, _ ->
                listener?.continueWithoutTrackerBlocking()
            }
            .setNegativeButton(R.string.onboardingGoBack) { _, _ ->
                // do nothing
            }
            .create()
    }
}