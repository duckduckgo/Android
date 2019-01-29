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
import androidx.fragment.app.DialogFragment


class GiveFeedbackDialogFragment : DialogFragment() {

    interface Listener {
        fun onUserSelectedToGiveFeedback()
        fun onUserDeclinedToGiveFeedback()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        isCancelable = false

        return AlertDialog.Builder(activity!!)
            .setTitle("Help us improve")
            .setMessage("Would you like to provide feedback so we can make your experience a happier one?")
            .setPositiveButton("YES") { _, _ ->
                listener?.onUserSelectedToGiveFeedback()
            }
            .setNegativeButton("NO") { _, _ ->
                listener?.onUserDeclinedToGiveFeedback()
            }
            .create()
    }

    private val listener: Listener?
        get() = activity as Listener

    companion object {

        fun create(): GiveFeedbackDialogFragment {
            return GiveFeedbackDialogFragment()
        }
    }
}