/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.ui.view.dialog

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface.OnClickListener
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.DialogTextAlertBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TextAlertDialog(val builder: Builder): DialogFragment() {

    private val binding: DialogTextAlertBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val rootView = layoutInflater.inflate(R.layout.dialog_text_alert, null)

        binding.textAlertDialogPositiveButton.text = builder.positiveButtonText
        binding.textAlertDialogPositiveButton.setOnClickListener { builder.positiveButtonOnClick.invoke() }

        val alertDialog = MaterialAlertDialogBuilder(
            requireActivity(),
            com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_RoundedDialog
        )
            .setView(rootView)

        isCancelable = false

        return alertDialog.create()
    }

    fun usage(){
        // val dialog = AppTPDisableConfirmationDialog.instance(this)
        // dialog.show(
        //     supportFragmentManager,
        //     AppTPDisableConfirmationDialog.TAG_APPTP_DISABLE_DIALOG
        // )
    }

    companion object {

        const val TAG_TEXT_ALERT_DIALOG = "TextAlertDialog"

        // fun instance(listener: Listener): AppTPDisableConfirmationDialog {
        //     return AppTPDisableConfirmationDialog(listener)
        // }
    }

    class Builder(val context: Context) {

        var positiveButtonText: CharSequence = ""
        var positiveButtonOnClick: () -> Unit = {}

        fun setPositiveButton(
            @StringRes textId: Int,
            onClick: () -> Unit
        ): Builder {
            positiveButtonText = context.getText(textId)
            positiveButtonOnClick = onClick
            return this
        }

        fun build(): TextAlertDialog {
            return TextAlertDialog()
        }
    }


}
