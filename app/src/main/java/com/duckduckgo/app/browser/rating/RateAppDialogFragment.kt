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

package com.duckduckgo.app.browser.rating

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment


class RateAppDialogFragment : DialogFragment() {

    interface Listener {
        fun onUserSelectedToRateApp()
        fun onUserDeclinedToRateApp()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        return AlertDialog.Builder(activity!!)
            .setTitle("Rate us 5 star")
            .setMessage("Support us by giving us a 5 star rating")
            .setPositiveButton("OF COURSE") { _, _ ->
                listener?.onUserSelectedToRateApp()
            }
            .setNegativeButton("NO") { _, _ ->
                listener?.onUserDeclinedToRateApp()
            }
            .setCancelable(false)
            .create()
    }

    private val listener: Listener?
        get() = activity as Listener

    companion object {

        fun create(): RateAppDialogFragment {
            return RateAppDialogFragment()
        }
    }
}