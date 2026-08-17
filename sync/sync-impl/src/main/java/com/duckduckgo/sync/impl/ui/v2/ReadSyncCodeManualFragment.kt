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

package com.duckduckgo.sync.impl.ui.v2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.sync.impl.databinding.FragmentSyncV2ReadSyncCodeManualBinding
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class ReadSyncCodeManualFragment : DuckDuckGoFragment() {
    private var _binding: FragmentSyncV2ReadSyncCodeManualBinding? = null
    private val binding
        get() = requireNotNull(_binding) {
            "Fragment $this tried to access ViewBinding outside of View's lifecycle."
        }

    private val entryPoint: SyncEntryPoint
        get() = requireNotNull(BundleCompat.getSerializable(requireArguments(), ENTRY_POINT_ARG, SyncEntryPoint::class.java)) {
            "Missing fragment argument: '$ENTRY_POINT_ARG'"
        }

    @Inject
    lateinit var syncCodeViewModelFactory: ReadSyncCodeViewModel.Factory

    private val viewModel by activityViewModels<ReadSyncCodeViewModel> {
        ReadSyncCodeViewModel.Factory.Provider(syncCodeViewModelFactory, entryPoint)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSyncV2ReadSyncCodeManualBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        configurePasteButton()
    }

    private fun configurePasteButton() {
        binding.pasteCodeButton.setOnClickListener { viewModel.pasteSyncCode() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ENTRY_POINT_ARG = "entry_point"

        fun instance(entryPoint: SyncEntryPoint) = ReadSyncCodeManualFragment().apply {
            arguments = bundleOf(ENTRY_POINT_ARG to entryPoint)
        }
    }
}
