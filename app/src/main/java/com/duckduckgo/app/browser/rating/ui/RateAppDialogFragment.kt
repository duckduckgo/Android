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
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*


class RateAppDialogFragment : EnjoymentDialog() {

    interface Listener {
        fun onUserSelectedToRateApp(promptCount: PromptCount)
        fun onUserDeclinedToRateApp(promptCount: PromptCount)
        fun onUserCancelledRateAppDialog(promptCount: PromptCount)
    }

    private lateinit var listener: Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        firePixelWithPromptCount(APP_RATING_DIALOG_SHOWN)

        return AlertDialog.Builder(activity!!, R.style.AlertDialogTheme)
            .setTitle(R.string.rateAppDialogTitle)
            .setMessage(R.string.rateAppDialogMessage)
            .setPositiveButton(R.string.rateAppDialogPositiveButton) { _, _ ->
                firePixelWithPromptCount(APP_RATING_DIALOG_USER_GAVE_RATING)
                listener.onUserSelectedToRateApp(promptCount)
            }
            .setNegativeButton(R.string.rateAppDialogNegativeButton) { _, _ ->
                firePixelWithPromptCount(APP_RATING_DIALOG_USER_DECLINED_RATING)
                listener.onUserDeclinedToRateApp(promptCount)
            }
            .setOnKeyListener(BackKeyListener {
                firePixelWithPromptCount(APP_RATING_DIALOG_USER_CANCELLED)
                listener.onUserCancelledRateAppDialog(promptCount)
            })
            .create()
    }

    companion object {
        fun create(promptCount: PromptCount, listener: Listener): RateAppDialogFragment {
            return RateAppDialogFragment().also { fragment ->
                val bundle = Bundle()
                bundle.putInt(PROMPT_COUNT_BUNDLE_KEY, promptCount.value)
                fragment.arguments = bundle
                fragment.listener = listener
            }
        }
    }
}