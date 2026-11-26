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

package com.duckduckgo.pagetranslation.impl.sheet

import android.app.Dialog
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoBottomSheetDialogFragment
import com.duckduckgo.common.ui.setRoundCorners
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.pagetranslation.impl.R
import com.duckduckgo.pagetranslation.impl.databinding.FragmentLanguagePackSheetBinding
import com.duckduckgo.pagetranslation.impl.language.DeviceLanguages
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.google.android.material.bottomsheet.BottomSheetDialog
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class LanguagePackSheetFragment : DuckDuckGoBottomSheetDialogFragment() {

    @Inject
    lateinit var deviceLanguages: DeviceLanguages

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialog.setRoundCorners() }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val args = requireArguments()
        val pair = LanguagePair(args.getString(ARG_SOURCE).orEmpty(), args.getString(ARG_TARGET).orEmpty())
        val sizeBytes = args.getLong(ARG_SIZE_BYTES, UNKNOWN_SIZE).takeIf { it != UNKNOWN_SIZE }
        val language = deviceLanguages.displayName(pair.source)

        return FragmentLanguagePackSheetBinding.inflate(inflater, container, false).also { binding ->
            binding.languagePackSheetTitle.text = getString(R.string.pageTranslationDownloadSheetTitle, language)
            binding.languagePackSheetDescription.text = getString(R.string.pageTranslationDownloadSheetDescription, language)
            binding.languagePackSheetSize.apply {
                text = sizeBytes?.let { getString(R.string.pageTranslationDownloadSheetSize, Formatter.formatShortFileSize(context, it)) }
                visibility = if (sizeBytes == null) View.GONE else View.VISIBLE
            }
            binding.languagePackSheetClose.setOnClickListener { dismiss() }
            binding.languagePackSheetButton.setOnClickListener {
                setFragmentResult(RESULT_KEY, bundleOf(ARG_SOURCE to pair.source, ARG_TARGET to pair.target))
                dismiss()
            }
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The default peek height is a screen ratio, which cuts the button off after a rotation to landscape.
        view.addOnLayoutChangeListener { sheet, _, _, _, _, _, _, _, _ ->
            (dialog as? BottomSheetDialog)?.behavior?.peekHeight = sheet.height
        }
    }

    companion object {
        const val TAG = "LanguagePackSheet"
        const val RESULT_KEY = "languagePackSheetConfirmed"
        private const val ARG_SOURCE = "source"
        private const val ARG_TARGET = "target"
        private const val ARG_SIZE_BYTES = "sizeBytes"
        private const val UNKNOWN_SIZE = -1L

        fun newInstance(
            pair: LanguagePair,
            sizeBytes: Long?,
        ) = LanguagePackSheetFragment().apply {
            arguments = bundleOf(
                ARG_SOURCE to pair.source,
                ARG_TARGET to pair.target,
                ARG_SIZE_BYTES to (sizeBytes ?: UNKNOWN_SIZE),
            )
        }

        fun pairFromResult(result: Bundle) = LanguagePair(result.getString(ARG_SOURCE).orEmpty(), result.getString(ARG_TARGET).orEmpty())
    }
}
