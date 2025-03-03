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
 * See the License for the specific language governing Permissionss and
 * limitations under the License.
 */

package com.duckduckgo.app.settings

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentSettingPermissionsBinding
import com.duckduckgo.app.permissions.PermissionsScreenNoParams
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_PERMISSIONS_PRESSED
import com.duckduckgo.app.settings.PermissionsSettingViewModel.Command
import com.duckduckgo.app.settings.PermissionsSettingViewModel.Command.LaunchPermissionsScreen
import com.duckduckgo.app.settings.PermissionsSettingViewModel.ViewState
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
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(306)
class PermissionsSettingNode @Inject constructor() : RootSettingsNode {
    override val settingsHeaderNodeId = SettingsHeaderNodeId.Other
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return PermissionsSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
            "permission", "notification", "link",
        )
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class PermissionsSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, PermissionsSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): PermissionsSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[PermissionsSettingViewModel::class.java]
    }

    private val binding: ContentSettingPermissionsBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.permissionsSetting.setOnClickListener {
            viewModel.onPermissionsSettingClicked()
        }
    }

    override fun renderView(viewState: ViewState) {
        with(binding.permissionsSetting) {
            visibility = if (viewState.showPermissionsSetting) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            LaunchPermissionsScreen -> {
                globalActivityStarter.start(context, PermissionsScreenNoParams)
            }
        }
    }
}

@ContributesViewModel(ViewScope::class)
class PermissionsSettingViewModel @Inject constructor(
    private val pixel: Pixel,
) : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            showPermissionsSetting = false,
        )
    }

    fun onPermissionsSettingClicked() {
        _commands.trySend(LaunchPermissionsScreen)
        pixel.fire(SETTINGS_PERMISSIONS_PRESSED)
    }

    data class ViewState(
        val showPermissionsSetting: Boolean = true,
    )

    sealed class Command {
        data object LaunchPermissionsScreen : Command()
    }
}
