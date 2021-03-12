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

package com.duckduckgo.app.browser.rating.ui

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.dialog.BackKeyListener
import com.duckduckgo.app.global.rating.PromptCount
import com.duckduckgo.app.pixels.AppPixelName.*

class GiveFeedbackDialogFragment : EnjoymentDialog() {

    interface Listener {
        fun onUserSelectedToGiveFeedback(promptCount: PromptCount)
        fun onUserDeclinedToGiveFeedback(promptCount: PromptCount)
        fun onUserCancelledGiveFeedbackDialog(promptCount: PromptCount)
    }

    private lateinit var listener: Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        firePixelWithPromptCount(APP_FEEDBACK_DIALOG_SHOWN)

        return AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setTitle(R.string.giveFeedbackDialogTitle)
            .setMessage(R.string.giveFeedbackDialogMessage)
            .setPositiveButton(R.string.giveFeedbackDialogPositiveButton) { _, _ ->
                firePixelWithPromptCount(APP_FEEDBACK_DIALOG_USER_GAVE_FEEDBACK)
                listener.onUserSelectedToGiveFeedback(promptCount)
            }
            .setNegativeButton(R.string.giveFeedbackDialogNegativeButton) { _, _ ->
                firePixelWithPromptCount(APP_FEEDBACK_DIALOG_USER_DECLINED_FEEDBACK)
                listener.onUserDeclinedToGiveFeedback(promptCount)
            }
            .setOnKeyListener(
                BackKeyListener {
                    firePixelWithPromptCount(APP_FEEDBACK_DIALOG_USER_CANCELLED)
                    listener.onUserCancelledGiveFeedbackDialog(promptCount)
                }
            )
            .create()
    }

    companion object {
        fun create(promptCount: PromptCount, listener: Listener): GiveFeedbackDialogFragment {
            return GiveFeedbackDialogFragment().also { fragment ->
                val bundle = Bundle()
                bundle.putInt(PROMPT_COUNT_BUNDLE_KEY, promptCount.value)
                fragment.arguments = bundle
                fragment.listener = listener
            }
        }
    }
}
