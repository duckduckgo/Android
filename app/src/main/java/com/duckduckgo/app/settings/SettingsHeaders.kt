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
import androidx.annotation.StringRes
import androidx.core.view.doOnAttach
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentSettingsHeaderBinding
import com.duckduckgo.app.settings.SettingsHeaderViewModel.Command
import com.duckduckgo.app.settings.SettingsHeaderViewModel.ViewState
import com.duckduckgo.common.ui.settings.RootSettingsNode
import com.duckduckgo.common.ui.settings.SettingNodeView
import com.duckduckgo.common.ui.settings.SettingViewModel
import com.duckduckgo.common.ui.settings.SettingsHeaderNodeId
import com.duckduckgo.common.ui.settings.SettingsHeader
import com.duckduckgo.common.ui.settings.SettingsHeaderNode
import com.duckduckgo.common.ui.settings.SettingsNode
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(ActivityScope::class, boundType = RootSettingsNode::class)
@PriorityKey(100)
class ProtectionsSettingsHeader @Inject constructor() : RootSettingsNode, SettingsHeaderNode {
    override val settingsHeaderNodeId: SettingsHeaderNodeId = SettingsHeaderNodeId.Protections
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun generateKeywords(): Set<String> {
        TODO("Not yet implemented")
    }

    override fun getView(context: Context): View {
        return ProtectionSettingsHeaderNodeView(context, searchableId = id, textRes = R.string.settingsHeadingProtections).also { view ->
            view.doOnAttach {
                view.hideDivider()
            }
        }
    }
}

@ContributesMultibinding(ActivityScope::class, boundType = RootSettingsNode::class)
@PriorityKey(200)
class PrivacyProSettingsHeader @Inject constructor() : RootSettingsNode, SettingsHeaderNode {
    override val settingsHeaderNodeId: SettingsHeaderNodeId = SettingsHeaderNodeId.PPro
    override val children: List<SettingsNode> = emptyList()
    override val id: UUID = UUID.randomUUID()

    override fun generateKeywords(): Set<String> {
        TODO("Not yet implemented")
    }

    override fun getView(context: Context): View {
        return PProSettingsHeaderNodeView(context, searchableId = id, textRes = com.duckduckgo.subscriptions.impl.R.string.privacyPro)
    }
}

@ContributesMultibinding(ActivityScope::class, boundType = RootSettingsNode::class)
@PriorityKey(300)
class OtherSettingsHeader @Inject constructor() : RootSettingsNode, SettingsHeaderNode {
    override val settingsHeaderNodeId = SettingsHeaderNodeId.Other
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun generateKeywords(): Set<String> {
        TODO("Not yet implemented")
    }

    override fun getView(context: Context): View {
        return OtherSettingsHeaderNodeView(context, searchableId = id, textRes = R.string.settingsHeadingMainSettings)
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class ProtectionSettingsHeaderNodeView(
    context: Context,
    searchableId: UUID,
    textRes: Int
) : SettingsHeaderNodeView<ProtectionSettingsHeaderViewModel>(
        context = context, searchableId = searchableId, textRes = textRes,
) {
    override fun provideViewModel(): ProtectionSettingsHeaderViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ProtectionSettingsHeaderViewModel::class.java]
    }
}

@ContributesViewModel(ViewScope::class)
class ProtectionSettingsHeaderViewModel @Inject constructor(): SettingsHeaderViewModel()

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class PProSettingsHeaderNodeView(
    context: Context,
    searchableId: UUID,
    textRes: Int
) : SettingsHeaderNodeView<PProSettingsHeaderViewModel>(
        context = context, searchableId = searchableId, textRes = textRes,
) {
    override fun provideViewModel(): PProSettingsHeaderViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[PProSettingsHeaderViewModel::class.java]
    }
}

@ContributesViewModel(ViewScope::class)
class PProSettingsHeaderViewModel @Inject constructor(): SettingsHeaderViewModel()

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class OtherSettingsHeaderNodeView(
    context: Context,
    searchableId: UUID,
    textRes: Int
) : SettingsHeaderNodeView<OtherSettingsHeaderViewModel>(
    context = context, searchableId = searchableId, textRes = textRes,
) {
    override fun provideViewModel(): OtherSettingsHeaderViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[OtherSettingsHeaderViewModel::class.java]
    }
}

@ContributesViewModel(ViewScope::class)
class OtherSettingsHeaderViewModel @Inject constructor(): SettingsHeaderViewModel()

@SuppressLint("ViewConstructor")
abstract class SettingsHeaderNodeView <VM: SettingsHeaderViewModel>(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
    @StringRes
    private val textRes: Int,
) : SettingNodeView<Command, ViewState, VM>(context, attrs, defStyle, searchableId), SettingsHeader {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    private val binding: ContentSettingsHeaderBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
        binding.settingHeader.setText(textRes)
    }

    override fun renderView(viewState: ViewState) {
        with(binding.root) {
            visibility = if (viewState.showSettingsHeader) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        with(binding.settingDivider) {
            visibility = if (viewState.showDivider) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    override fun showDivider() {
        viewModel.showDivider()
    }

    override fun hideDivider() {
        viewModel.hideDivider()
    }
}

abstract class SettingsHeaderViewModel : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            showSettingsHeader = false,
        )
    }

    fun showDivider() {
        _viewState.update {
            it.copy(showDivider = true)
        }
    }

    fun hideDivider() {
        _viewState.update {
            it.copy(showDivider = false)
        }
    }

    data class ViewState(
        val showSettingsHeader: Boolean = true,
        val showDivider: Boolean = true,
    )

    sealed class Command
}
