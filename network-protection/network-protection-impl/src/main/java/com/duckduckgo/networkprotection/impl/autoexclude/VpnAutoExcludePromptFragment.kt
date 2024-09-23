/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.autoexclude

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.text.DaxTextView.TextType
import com.duckduckgo.common.ui.view.text.DaxTextView.TextType.Secondary
import com.duckduckgo.common.ui.view.text.DaxTextView.Typography
import com.duckduckgo.common.ui.view.text.DaxTextView.Typography.Body1
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.ViewState
import com.duckduckgo.networkprotection.impl.databinding.DialogAutoExcludeBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class VpnAutoExcludePromptFragment private constructor() : BottomSheetDialogFragment() {

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[VpnAutoExcludePromptViewModel::class.java]
    }

    override fun getTheme(): Int = com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_BottomSheetDialogCollapsed

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return DialogAutoExcludeBinding.inflate(inflater, container, false).apply {
            configureViews(this)
            observerViewModel(this)
        }.root
    }

    override fun onStart() {
        super.onStart()
        viewModel.onPromptShown()
    }

    private fun observerViewModel(binding: DialogAutoExcludeBinding) {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { renderViewState(binding, it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(
        binding: DialogAutoExcludeBinding,
        viewState: ViewState,
    ) {
        binding.apply {
            viewState.flaggedApps.forEach { app ->
                val appCheckBox = CheckBox(this.root.context)
                appCheckBox.text = app.appName
                appCheckBox.isChecked = true
                appCheckBox.format()
                autoExcludePromptItemsContainer.addView(appCheckBox)
            }
            autoExcludePromptMessage.text = String.format(
                getString(R.string.netpAutoExcludePromptMessage),
                viewState.flaggedApps.size,
            )
        }
    }

    private fun configureViews(binding: DialogAutoExcludeBinding) {
        binding.apply {
            autoExcludeCheckBox.format()
            autoExcludePromptAddAction.setOnClickListener {
                viewModel.onAddExclusionsSelected(autoExcludeCheckBox.isChecked)
            }

            autoExcludePromptCancelAction.setOnClickListener {
                dismiss()
            }
        }
    }

    companion object {
        fun instance(): VpnAutoExcludePromptFragment {
            return VpnAutoExcludePromptFragment()
        }
    }

    private fun CheckBox.format() {
        setTextAppearance(Typography.getTextAppearanceStyle(Body1))
        setTextColor(
            ContextCompat.getColorStateList(
                context,
                TextType.getTextColorStateList(Secondary),
            ),
        )
    }
}
