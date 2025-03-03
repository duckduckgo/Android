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

package com.duckduckgo.app.settings

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentSettingMainAutofillBinding
import com.duckduckgo.app.settings.AutofillSettingViewModel.Command
import com.duckduckgo.app.settings.AutofillSettingViewModel.Command.LaunchAutofillScreen
import com.duckduckgo.app.settings.AutofillSettingViewModel.ViewState
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.AutofillScreens.AutofillSettingsScreen
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource
import com.duckduckgo.common.ui.settings.RootSettingsNode
import com.duckduckgo.common.ui.settings.SettingNodeView
import com.duckduckgo.common.ui.settings.SettingViewModel
import com.duckduckgo.common.ui.settings.SettingsHeaderNodeId
import com.duckduckgo.common.ui.settings.SettingsNode
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(304)
class AutofillSettingNode @Inject constructor() : RootSettingsNode {
    override val settingsHeaderNodeId = SettingsHeaderNodeId.Other
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return AutofillSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
            "password", "login", "autofill", "credentials",
            "manager", "security", "vault", "authentication",
        )
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class AutofillSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, AutofillSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): AutofillSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[AutofillSettingViewModel::class.java]
    }

    private val binding: ContentSettingMainAutofillBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.autofillLoginsSetting.setOnClickListener {
            viewModel.onAutofillSettingClicked()
        }
    }

    override fun renderView(viewState: ViewState) {
        with(binding.autofillLoginsSetting) {
            visibility = if (viewState.showAutofillSetting) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            LaunchAutofillScreen -> {
                globalActivityStarter.start(context, AutofillSettingsScreen(source = AutofillSettingsLaunchSource.SettingsActivity))
            }
        }
    }
}

@ContributesViewModel(ViewScope::class)
class AutofillSettingViewModel @Inject constructor(
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
) : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            showAutofillSetting = false,
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        viewModelScope.launch {
            _viewState.update {
                it.copy(
                    showAutofillSetting = autofillCapabilityChecker.canAccessCredentialManagementScreen(),
                )
            }
        }
    }

    fun onAutofillSettingClicked() {
        _commands.trySend(LaunchAutofillScreen)
    }

    data class ViewState(
        val showAutofillSetting: Boolean = false,
    )

    sealed class Command {
        data object LaunchAutofillScreen : Command()
    }
}
