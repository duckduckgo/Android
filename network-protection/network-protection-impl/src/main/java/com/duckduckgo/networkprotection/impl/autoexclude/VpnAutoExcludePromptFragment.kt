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
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.view.text.DaxTextView.TextType
import com.duckduckgo.common.ui.view.text.DaxTextView.TextType.Secondary
import com.duckduckgo.common.ui.view.text.DaxTextView.Typography
import com.duckduckgo.common.ui.view.text.DaxTextView.Typography.Body1
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.safeGetApplicationIcon
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptFragment.Companion.Source.UNKNOWN
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.PromptState.NEW_INCOMPATIBLE_APP
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.ViewState
import com.duckduckgo.networkprotection.impl.databinding.DialogAutoExcludeBinding
import com.duckduckgo.networkprotection.impl.databinding.ItemAutoexcludePromptAppBinding
import com.duckduckgo.networkprotection.store.db.VpnIncompatibleApp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class VpnAutoExcludePromptFragment : BottomSheetDialogFragment() {
    interface Listener {
        fun onAutoExcludeEnabled()
    }

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    private var _listener: Listener? = null

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

    fun addListener(listener: Listener) {
        _listener = listener
    }

    @Suppress("NewApi") // we use appBuildConfig
    private fun observerViewModel(binding: DialogAutoExcludeBinding) {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .distinctUntilChanged()
            .onEach { renderViewState(binding, it) }
            .onStart {
                viewModel.onPromptShown(
                    requireArguments().getStringArrayList(KEY_PROMPT_APP_PACKAGES)?.toList() ?: emptyList(),
                    if (appBuildConfig.sdkInt >= 33) {
                        requireArguments().getSerializable(KEY_PROMPT_SOURCE, Source::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        requireArguments().getSerializable(KEY_PROMPT_SOURCE) as? Source
                    } ?: UNKNOWN,
                )
            }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(
        binding: DialogAutoExcludeBinding,
        viewState: ViewState,
    ) {
        binding.apply {
            viewState.incompatibleApps.forEach { app ->
                val item = ItemAutoexcludePromptAppBinding.inflate(layoutInflater)
                item.incompatibleAppCheckBox.isChecked = true
                item.incompatibleAppCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.updateAppExcludeState(app.packageName, isChecked)
                }

                item.incompatibleAppName.setPrimaryText(app.name)

                context?.packageManager?.safeGetApplicationIcon(app.packageName)?.apply {
                    item.incompatibleAppName.setLeadingIconDrawable(this)
                    item.incompatibleAppName.setPrimaryTextColorStateList(
                        ContextCompat.getColorStateList(
                            root.context,
                            TextType.getTextColorStateList(Secondary),
                        ),
                    )
                }

                autoExcludePromptItemsContainer.addView(item.root)
            }
            if (viewState.promptState == NEW_INCOMPATIBLE_APP) {
                autoExcludePromptTitle.text = getString(R.string.netpAutoExcludePromptTitle)
                autoExcludePromptMessage.text = String.format(
                    getString(R.string.netpAutoExcludePromptMessage),
                    resources.getQuantityString(
                        R.plurals.netpAutoExcludeAppLabel,
                        viewState.incompatibleApps.size,
                        viewState.incompatibleApps.size,
                    ),
                )
            } else {
                autoExcludePromptTitle.text = getString(R.string.netpAutoExcludePromptExcludeAllTitle)
                autoExcludePromptMessage.text = String.format(
                    getString(R.string.netpAutoExcludePromptMessage),
                    resources.getQuantityString(
                        R.plurals.netpAutoExcludeAllAppLabel,
                        viewState.incompatibleApps.size,
                        viewState.incompatibleApps.size,
                    ),
                )
            }
        }
    }

    private fun configureViews(binding: DialogAutoExcludeBinding) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        binding.apply {
            autoExcludeCheckBox.format()
            autoExcludePromptAddAction.setOnClickListener {
                viewModel.onAddExclusionsSelected(autoExcludeCheckBox.isChecked)
                if (autoExcludeCheckBox.isChecked) {
                    _listener?.onAutoExcludeEnabled()
                }
                dismiss()
            }

            autoExcludePromptCancelAction.setOnClickListener {
                viewModel.onCancelPrompt()
                dismiss()
            }
        }
    }

    companion object {
        private const val KEY_PROMPT_APP_PACKAGES = "KEY_PROMPT_APP_PACKAGES"
        private const val KEY_PROMPT_SOURCE = "KEY_PROMPT_SOURCE"

        internal fun instance(
            incompatibleApps: List<VpnIncompatibleApp>,
            source: Source,
        ): VpnAutoExcludePromptFragment {
            return VpnAutoExcludePromptFragment().apply {
                val args = Bundle()
                args.putStringArrayList(KEY_PROMPT_APP_PACKAGES, ArrayList(incompatibleApps.map { it.packageName }))
                args.putSerializable(KEY_PROMPT_SOURCE, source)
                arguments = args
            }
        }

        enum class Source {
            VPN_SCREEN,
            EXCLUSION_LIST_SCREEN,
            UNKNOWN,
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
