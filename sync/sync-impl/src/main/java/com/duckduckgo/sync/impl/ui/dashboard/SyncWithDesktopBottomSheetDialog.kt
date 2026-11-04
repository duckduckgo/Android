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

package com.duckduckgo.sync.impl.ui.dashboard

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.duckduckgo.common.ui.applyBottomSystemBarInsetPadding
import com.duckduckgo.common.ui.setRoundCorners
import com.duckduckgo.sync.impl.databinding.BottomSheetSyncWithDesktopBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.duckduckgo.mobile.android.R as CommonR

class SyncWithDesktopBottomSheetDialog : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSyncWithDesktopBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = CommonR.style.Widget_DuckDuckGo_BottomSheetDialog_EdgeToEdge

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.setOnShowListener { dialog.setRoundCorners() }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetSyncWithDesktopBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.syncWithDesktopSheet.applyBottomSystemBarInsetPadding()
        binding.closeButton.setOnClickListener { dismiss() }
        binding.syncWithDesktopButton.setOnClickListener { selectChoice(Choice.SyncWithDesktop) }
        binding.noDesktopAppButton.setOnClickListener { selectChoice(Choice.NoDesktopApp) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun selectChoice(choice: Choice) {
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_KEY_CHOICE to choice))
        dismiss()
    }

    enum class Choice {
        SyncWithDesktop,
        NoDesktopApp,
    }

    companion object {
        const val TAG = "SyncWithDesktopBottomSheetDialog"
        const val REQUEST_KEY = "SyncWithDesktopResult"
        const val RESULT_KEY_CHOICE = "choice"

        fun newInstance() = SyncWithDesktopBottomSheetDialog()
    }
}
