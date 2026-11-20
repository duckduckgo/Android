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

package com.duckduckgo.duckchat.impl.clienttools

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.DialogBrowserToolsPermissionsBinding
import com.duckduckgo.duckchat.impl.databinding.ItemClientToolGrantAccessBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.duckduckgo.mobile.android.R as CommonR

/**
 * Asks the user for several [tools] at once. Matches the design system's rebrand stacked dialog, which has no room
 * for a list: each tool gets a checkbox, in a height capped list that scrolls on its own.
 * The whole dialog scrolls only on screens too short for the header and buttons, such as a phone in landscape.
 *
 * [selection] is read on every render, so the checkboxes follow what [onToggle] stored.
 * [onAnswer] gets one answer per user action; [dismiss] closes the dialog without one.
 */
class BrowserToolsPermissionsDialog(
    private val activity: Activity,
    private val tools: List<AskingTool>,
    private val selection: () -> GrantAccessSelection?,
    private val onToggle: (toolName: String) -> Unit,
    private val onAnswer: (ClientToolPermissionAnswer) -> Unit,
) : DaxAlertDialog {

    private var dialog: AlertDialog? = null
    private var binding: DialogBrowserToolsPermissionsBinding? = null
    private var rows: List<Pair<AskingTool, ItemClientToolGrantAccessBinding>> = emptyList()

    // BrowserActivity handles rotation itself, so the dialog survives it and only its sizes need updating.
    private val resizeOnRotation = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            dialog?.window?.decorView?.post(::applySizes)
        }

        override fun onLowMemory() = Unit
    }

    override fun build(): DaxAlertDialog {
        val binding = DialogBrowserToolsPermissionsBinding.inflate(LayoutInflater.from(activity)).also { binding = it }
        rows = tools.map { tool ->
            val row = ItemClientToolGrantAccessBinding.inflate(LayoutInflater.from(activity), binding.browserToolsPermissionsTools, true)
            val toolName = activity.getString(tool.copy.name)
            row.grantAccessToolName.text = toolName
            row.grantAccessToolDescription.text = activity.getString(tool.copy.promptMessage)
            row.grantAccessToolCheckbox.contentDescription = toolName
            tool to row
        }
        binding.browserToolsPermissionsAllow.setOnClickListener {
            answer(ClientToolPermissionAnswer.AllowForChat(selection()?.removed.orEmpty()))
        }
        binding.browserToolsPermissionsDontAllow.setOnClickListener { answer(ClientToolPermissionAnswer.DontAllow) }

        dialog = MaterialAlertDialogBuilder(activity, CommonR.style.Widget_DuckDuckGo_Dialog_Rebrand)
            .setView(binding.root)
            // Only the buttons answer, as on desktop and iOS: Back and taps outside do nothing.
            .setCancelable(false)
            .setOnDismissListener { activity.unregisterComponentCallbacks(resizeOnRotation) }
            .setBackgroundInsetStart(0)
            .setBackgroundInsetEnd(0)
            .create()
        render()
        return this
    }

    override fun show() {
        if (dialog == null) build()
        val shown = dialog ?: return
        shown.show()
        applySizes()
        activity.registerComponentCallbacks(resizeOnRotation)
    }

    override fun dismiss() {
        dialog?.dismiss()
    }

    override fun isShowing(): Boolean = dialog?.isShowing == true

    private fun answer(answer: ClientToolPermissionAnswer) {
        onAnswer(answer)
        dismiss()
    }

    private fun render() {
        val current = selection() ?: return
        rows.forEach { (tool, row) ->
            val canToggle = current.canToggle(tool.name)
            val toggle = View.OnClickListener {
                onToggle(tool.name)
                render()
            }
            row.grantAccessToolCheckbox.isVisible = current.showsCheckboxes
            row.grantAccessToolCheckbox.isChecked = current.isChecked(tool.name)
            row.grantAccessToolCheckbox.isEnabled = canToggle
            row.grantAccessToolCheckbox.setOnClickListener(toggle)
            row.root.isClickable = canToggle
            row.root.setOnClickListener(toggle.takeIf { canToggle })
        }
    }

    private fun applySizes() {
        val resources = activity.resources
        binding?.browserToolsPermissionsToolsScroll?.updateLayoutParams<ConstraintLayout.LayoutParams> {
            matchConstraintMaxHeight = resources.getDimensionPixelSize(R.dimen.duckAiBrowserToolsPermissionsListMaxHeight)
        }
        val maxWidth = resources.getDimensionPixelSize(CommonR.dimen.rebrandDialogMaxWidth)
        val margin = resources.getDimensionPixelSize(CommonR.dimen.keyline_5)
        val width = minOf(maxWidth, resources.displayMetrics.widthPixels - margin * 2)
        dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
