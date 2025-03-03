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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentSettingMainSyncBinding
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_SYNC_PRESSED
import com.duckduckgo.app.settings.SyncSettingViewModel.Command
import com.duckduckgo.app.settings.SyncSettingViewModel.Command.LaunchSyncScreen
import com.duckduckgo.app.settings.SyncSettingViewModel.ViewState
import com.duckduckgo.app.statistics.pixels.Pixel
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
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(302)
class SyncSettingNode @Inject constructor() : RootSettingsNode {
    override val settingsHeaderNodeId = SettingsHeaderNodeId.Other
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return SyncSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
            "sync", "backup", "cloud", "restore",
            "account", "data", "transfer", "storage",
            "link", "synchronize", "upload", "download",
        )
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class SyncSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, SyncSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): SyncSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SyncSettingViewModel::class.java]
    }

    private val binding: ContentSettingMainSyncBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.syncSetting.setOnClickListener {
            viewModel.onSyncSettingClicked()
        }
    }

    override fun renderView(viewState: ViewState) {
        with(binding.syncSetting) {
            visibility = if (viewState.showSyncSetting && viewState.isSyncEnabled) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            LaunchSyncScreen -> {
                globalActivityStarter.start(context, SyncActivityWithEmptyParams)
            }
        }
    }
}

@ContributesViewModel(ViewScope::class)
class SyncSettingViewModel @Inject constructor(
    private val deviceSyncState: DeviceSyncState,
    private val pixel: Pixel,
) : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            showSyncSetting = false,
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        _viewState.update {
            it.copy(
                isSyncEnabled = deviceSyncState.isFeatureEnabled(),
            )
        }
    }

    fun onSyncSettingClicked() {
        _commands.trySend(LaunchSyncScreen)
        pixel.fire(SETTINGS_SYNC_PRESSED)
    }

    data class ViewState(
        val showSyncSetting: Boolean = true,
        val isSyncEnabled: Boolean = false,
    )

    sealed class Command {
        data object LaunchSyncScreen : Command()
    }
}
