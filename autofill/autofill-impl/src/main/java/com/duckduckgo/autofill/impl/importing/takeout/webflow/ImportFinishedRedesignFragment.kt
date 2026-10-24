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

package com.duckduckgo.autofill.impl.importing.takeout.webflow

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.FragmentImportBookmarksResultRedesignBinding
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(FragmentScope::class)
class ImportFinishedRedesignFragment : DuckDuckGoFragment(R.layout.fragment_import_bookmarks_result_redesign) {

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentImportBookmarksResultRedesignBinding.bind(view)
        if (requireArguments().getBoolean(ARG_IS_SUCCESS)) {
            renderSuccess(binding, requireArguments().getInt(ARG_IMPORTED_COUNT))
        } else {
            renderError(binding)
        }
        binding.resultDoneButton.setOnClickListener {
            setFragmentResult(ImportFinishedFragment.RESULT_KEY_DONE, Bundle())
        }
    }

    private fun renderSuccess(
        binding: FragmentImportBookmarksResultRedesignBinding,
        importedCount: Int,
    ) {
        with(binding) {
            resultIcon.setImageResource(R.drawable.success_128)
            resultTitle.setText(R.string.importBookmarksRedesignSuccessTitle)
            resultRow.setPrimaryText(getString(R.string.importBookmarksRedesignSuccessImportedRow, importedCount).html(requireContext()))
            resultRow.setLeadingIconResource(CommonR.drawable.check_recolorable_24)
            resultSecondaryText.isVisible = false
            resultDoneButton.setText(R.string.importBookmarksRedesignSuccessPrimaryButton)
        }
    }

    private fun renderError(binding: FragmentImportBookmarksResultRedesignBinding) {
        with(binding) {
            resultIcon.setImageResource(R.drawable.bookmarks_import_128)
            resultTitle.setText(R.string.importBookmarksRedesignErrorTitle)
            resultRow.setPrimaryText(getString(R.string.importBookmarksErrorGenericMessage))
            resultRow.setLeadingIconResource(CommonR.drawable.cross_recolorable_24)
            resultSecondaryText.isVisible = true
            resultDoneButton.setText(R.string.importBookmarksRedesignErrorPrimaryButton)
        }
    }

    companion object {
        private const val ARG_IS_SUCCESS = "is_success"
        private const val ARG_IMPORTED_COUNT = "imported_count"

        fun newInstanceSuccess(importedCount: Int): ImportFinishedRedesignFragment = ImportFinishedRedesignFragment().apply {
            arguments = Bundle().apply {
                putBoolean(ARG_IS_SUCCESS, true)
                putInt(ARG_IMPORTED_COUNT, importedCount)
            }
        }

        fun newInstanceError(): ImportFinishedRedesignFragment = ImportFinishedRedesignFragment().apply {
            arguments = Bundle().apply {
                putBoolean(ARG_IS_SUCCESS, false)
            }
        }
    }
}
