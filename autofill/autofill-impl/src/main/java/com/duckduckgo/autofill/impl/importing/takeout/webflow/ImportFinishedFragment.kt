/*
 * Copyright (c) 2025 DuckDuckGo
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.FragmentImportBookmarksResultBinding
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.di.scopes.FragmentScope

@InjectWith(FragmentScope::class)
class ImportFinishedFragment : DuckDuckGoFragment() {
    private var binding: FragmentImportBookmarksResultBinding? = null
    private var onDoneCallback: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentImportBookmarksResultBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        setupToolbar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setupUi() {
        val success = arguments?.getBoolean(ARG_BOOKMARK_IMPORT_SUCCESS, false) ?: false

        if (success) {
            setupUiForSuccess()
        } else {
            setupUiForFailure()
        }

        binding?.doneButton?.setOnClickListener {
            onDoneCallback?.invoke()
        }
    }

    private fun setupUiForSuccess() {
        val bookmarkCount = arguments?.getInt(ARG_BOOKMARK_COUNT_SUCCESS, 0) ?: 0

        binding?.run {
            bookmarksImportResult.setPrimaryText(getString(R.string.importBookmarksFromGoogleSuccessBookmarksCount, bookmarkCount))
            bookmarksImportResult.setLeadingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_check_green_24)
            importResultTitle.text = getString(R.string.importBookmarksSuccessTitle)
            secondaryErrorInfo.gone()
        }
    }

    private fun setupUiForFailure() {
        val error = arguments?.getString(ARG_BOOKMARK_FAILURE_MESSAGE) ?: getString(R.string.importBookmarksErrorGenericMessage)

        binding?.run {
            bookmarksImportResult.setPrimaryText(error)
            bookmarksImportResult.setLeadingIconResource(R.drawable.ic_cross_recolorable_red_24)
            importResultTitle.text = getString(R.string.importBookmarksErrorTitle)
            secondaryErrorInfo.show()
        }
    }

    fun setOnDoneCallback(callback: () -> Unit) {
        onDoneCallback = callback
    }

    private fun setupToolbar() {
        val toolbar = activity?.findViewById<Toolbar>(com.duckduckgo.mobile.android.R.id.toolbar)
        toolbar?.setNavigationOnClickListener {
            onDoneCallback?.invoke()
        }
    }

    companion object {
        private const val ARG_BOOKMARK_COUNT_SUCCESS = "bookmark_import_count_success"
        private const val ARG_BOOKMARK_IMPORT_SUCCESS = "bookmark_import_success"
        private const val ARG_BOOKMARK_FAILURE_MESSAGE = "bookmark_import_failure_message"

        fun newInstanceSuccess(bookmarksImported: Int): ImportFinishedFragment = ImportFinishedFragment().apply {
            arguments =
                Bundle().apply {
                    putInt(ARG_BOOKMARK_COUNT_SUCCESS, bookmarksImported)
                    putBoolean(ARG_BOOKMARK_IMPORT_SUCCESS, true)
                }
        }

        fun newInstanceFailure(message: String): ImportFinishedFragment = ImportFinishedFragment().apply {
            arguments =
                Bundle().apply {
                    putString(ARG_BOOKMARK_FAILURE_MESSAGE, message)
                    putBoolean(ARG_BOOKMARK_IMPORT_SUCCESS, false)
                }
        }
    }
}
