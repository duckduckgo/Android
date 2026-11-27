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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import com.duckduckgo.common.ui.setRoundCorners
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.ViewPastedTextSheetBinding
import com.duckduckgo.duckchat.impl.ui.nativeinput.attachment.PastedText
import com.duckduckgo.duckchat.impl.ui.nativeinput.attachment.PromptLength
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.duckduckgo.mobile.android.R as CommonR

fun showPastedTextSheet(
    context: Context,
    paste: PastedText,
    promptLength: PromptLength?,
    onSave: ((String) -> Unit)?,
) {
    val dialog = BottomSheetDialog(context, CommonR.style.Widget_DuckDuckGo_BottomSheetDialog_EdgeToEdge)
    val binding = ViewPastedTextSheetBinding.inflate(LayoutInflater.from(dialog.context))
    binding.bindContent(paste, readOnly = onSave == null)
    binding.bindCounter(paste, promptLength.takeIf { onSave != null })
    binding.bindActions(dialog, onSave)
    binding.root.padBottomForKeyboard()
    dialog.showExpanded(binding.root)
}

private fun ViewPastedTextSheetBinding.bindContent(paste: PastedText, readOnly: Boolean) {
    pastedTextTitle.setText(if (readOnly) R.string.duckAiPastedTextViewTitle else R.string.duckAiPastedTextEditTitle)
    pastedTextContent.setText(paste.content)
    if (readOnly) pastedTextContent.keyListener = null
}

private fun ViewPastedTextSheetBinding.bindCounter(paste: PastedText, promptLength: PromptLength?) {
    pastedTextCounter.isVisible = promptLength != null
    if (promptLength == null) return
    val otherLength = promptLength.count - paste.length
    val normalColor = pastedTextCounter.currentTextColor
    val overLimitColor = root.context.getColorFromAttr(CommonR.attr.daxColorDestructive)
    val render = {
        val count = otherLength + pastedTextContent.text.length
        pastedTextCounter.text = root.context.getString(R.string.duckAiPastedTextCounter, count, promptLength.limit)
        pastedTextCounter.setTextColor(if (count > promptLength.limit) overLimitColor else normalColor)
    }
    render()
    pastedTextContent.doAfterTextChanged { render() }
}

private fun ViewPastedTextSheetBinding.bindActions(dialog: BottomSheetDialog, onSave: ((String) -> Unit)?) {
    pastedTextClose.setOnClickListener { dialog.dismiss() }
    pastedTextSave.isVisible = onSave != null
    if (onSave == null) return
    pastedTextSave.isEnabled = pastedTextContent.text.isNotBlank()
    pastedTextContent.doAfterTextChanged { pastedTextSave.isEnabled = !it.isNullOrBlank() }
    pastedTextSave.setOnClickListener {
        onSave(pastedTextContent.text.toString())
        dialog.dismiss()
    }
}

private fun View.padBottomForKeyboard() {
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()).bottom
        view.updatePadding(bottom = initialBottom + bottom)
        insets
    }
}

private fun BottomSheetDialog.showExpanded(content: View) {
    setContentView(content)
    behavior.skipCollapsed = true
    behavior.isDraggable = false
    behavior.state = BottomSheetBehavior.STATE_EXPANDED
    setOnShowListener { setRoundCorners() }
    show()
}
