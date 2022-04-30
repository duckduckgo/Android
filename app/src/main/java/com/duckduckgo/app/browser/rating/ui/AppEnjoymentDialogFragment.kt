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
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.dialog.BackKeyListener
import com.duckduckgo.app.global.rating.PromptCount
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.di.scopes.FragmentScope

@InjectWith(FragmentScope::class)
class AppEnjoymentDialogFragment : EnjoymentDialog() {

    interface Listener {
        fun onUserSelectedAppIsEnjoyed(promptCount: PromptCount)
        fun onUserSelectedAppIsNotEnjoyed(promptCount: PromptCount)
        fun onUserCancelledAppEnjoymentDialog(promptCount: PromptCount)
    }

    private lateinit var listener: Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        firePixelWithPromptCount(APP_ENJOYMENT_DIALOG_SHOWN)

        return AlertDialog.Builder(requireActivity())
            .setTitle(R.string.appEnjoymentDialogTitle)
            .setMessage(R.string.appEnjoymentDialogMessage)
            .setPositiveButton(R.string.appEnjoymentDialogPositiveButton) { _, _ ->
                firePixelWithPromptCount(APP_ENJOYMENT_DIALOG_USER_ENJOYING)
                listener.onUserSelectedAppIsEnjoyed(promptCount)
            }
            .setNegativeButton(R.string.appEnjoymentDialogNegativeButton) { _, _ ->
                firePixelWithPromptCount(APP_ENJOYMENT_DIALOG_USER_NOT_ENJOYING)
                listener.onUserSelectedAppIsNotEnjoyed(promptCount)
            }
            .setOnKeyListener(
                BackKeyListener {
                    firePixelWithPromptCount(APP_ENJOYMENT_DIALOG_USER_CANCELLED)
                    listener.onUserCancelledAppEnjoymentDialog(promptCount)
                }
            )
            .create()
    }

    companion object {
        fun create(
            promptCount: PromptCount,
            listener: Listener
        ): AppEnjoymentDialogFragment {
            return AppEnjoymentDialogFragment().also { fragment ->
                val bundle = Bundle()
                bundle.putInt(PROMPT_COUNT_BUNDLE_KEY, promptCount.value)
                fragment.arguments = bundle
                fragment.listener = listener
            }
        }
    }
}
