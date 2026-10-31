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

package com.duckduckgo.subscriptions.impl.withdrawal

import android.content.Context
import android.view.LayoutInflater
import com.duckduckgo.common.ui.view.addClickableLink
import com.duckduckgo.common.ui.view.dialog.CustomAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.DialogSubscriptionWithdrawalBinding

class SubscriptionWithdrawalDialog(
    private val context: Context,
    private val onConfirm: () -> Unit,
    private val onDismissed: () -> Unit,
    private val onHelpLinkClicked: () -> Unit,
) {
    private var dialog: DaxAlertDialog? = null
    private var content: DialogSubscriptionWithdrawalBinding? = null

    fun show(tierName: String, refundAmount: String) {
        dismiss()

        val content = DialogSubscriptionWithdrawalBinding.inflate(LayoutInflater.from(context))
        content.withdrawalConfirmSpinner.setIndicatorColor(content.withdrawalConfirmButton.currentTextColor)
        content.withdrawalError.addClickableLink(HELP_LINK_ANNOTATION, context.getText(R.string.subscriptionWithdrawErrorAlert)) {
            dismiss()
            onHelpLinkClicked()
        }
        content.withdrawalConfirmButton.setOnClickListener {
            renderInProgress(content)
            onConfirm()
        }
        content.withdrawalKeepButton.setOnClickListener {
            dismiss()
            onDismissed()
        }

        val dialog = CustomAlertDialogBuilder(context)
            .setTitle(R.string.subscriptionWithdrawDialogTitle)
            .setMessage(context.getString(R.string.subscriptionWithdrawDialogMessage, tierName, refundAmount))
            .setView(content)

        this.dialog = dialog
        this.content = content
        dialog.show()
    }

    fun showError() {
        val content = content ?: return
        with(content.withdrawalConfirmButton) {
            setText(R.string.subscriptionWithdrawDialogConfirm)
            contentDescription = null
            isClickable = true
        }
        content.withdrawalKeepButton.isEnabled = true
        content.withdrawalConfirmSpinner.gone()
        content.withdrawalError.show()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        content = null
    }

    private fun renderInProgress(content: DialogSubscriptionWithdrawalBinding) {
        with(content.withdrawalConfirmButton) {
            text = ""
            contentDescription = context.getString(R.string.subscriptionWithdrawDialogConfirm)
            isClickable = false
        }
        content.withdrawalKeepButton.isEnabled = false
        content.withdrawalError.gone()
        content.withdrawalConfirmSpinner.show()
    }

    private companion object {
        private const val HELP_LINK_ANNOTATION = "withdrawal_help_link"
    }
}
