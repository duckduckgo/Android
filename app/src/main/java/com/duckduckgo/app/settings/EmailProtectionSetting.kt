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
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentSettingEmailProtectionBinding
import com.duckduckgo.app.email.ui.EmailProtectionUnsupportedScreenNoParams
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_EMAIL_PROTECTION_PRESSED
import com.duckduckgo.app.settings.EmailProtectionSettingViewModel.Command
import com.duckduckgo.app.settings.EmailProtectionSettingViewModel.Command.LaunchEmailProtection
import com.duckduckgo.app.settings.EmailProtectionSettingViewModel.Command.LaunchEmailProtectionNotSupported
import com.duckduckgo.app.settings.EmailProtectionSettingViewModel.ViewState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.email.EmailManager
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
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(107)
class EmailProtectionSettingNode @Inject constructor() : RootSettingsNode {
    override val settingsHeaderNodeId = SettingsHeaderNodeId.Protections
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return EmailProtectionSettingNodeView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
                "email", "protection", "phishing", "hide", "obfuscate",
        )
    }
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class EmailProtectionSettingNodeView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, EmailProtectionSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    override fun provideViewModel(): EmailProtectionSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[EmailProtectionSettingViewModel::class.java]
    }

    private val binding: ContentSettingEmailProtectionBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.emailProtectionSetting.setOnClickListener {
            viewModel.onEmailProtectionSettingClicked()
        }
    }

    override fun renderView(viewState: ViewState) {
        with(binding.emailProtectionSetting) {
            visibility = if (viewState.showEmailProtectionSetting) {
                View.VISIBLE
            } else {
                View.GONE
            }
            setStatus(viewState.isEmailProtectionEnabled)
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            is LaunchEmailProtection -> {
                val activity = context.getActivity()
                activity?.startActivity(BrowserActivity.intent(context, command.url, interstitialScreen = true))
                activity?.finish()
            }

            LaunchEmailProtectionNotSupported -> {
                globalActivityStarter.start(context, EmailProtectionUnsupportedScreenNoParams)
            }
        }
    }
}

@ContributesViewModel(ViewScope::class)
class EmailProtectionSettingViewModel @Inject constructor(
    private val emailManager: EmailManager,
    private val pixel: Pixel,
) : SettingViewModel<Command, ViewState>(ViewState()) {

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            showEmailProtectionSetting = false,
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        _viewState.update {
            it.copy(
                    isEmailProtectionEnabled = emailManager.getEmailAddress() != null,
            )
        }
    }

    fun onEmailProtectionSettingClicked() {
        val command = if (emailManager.isEmailFeatureSupported()) {
            LaunchEmailProtection(EMAIL_PROTECTION_URL)
        } else {
            LaunchEmailProtectionNotSupported
        }
        _commands.trySend(command)
        pixel.fire(SETTINGS_EMAIL_PROTECTION_PRESSED)
    }

    data class ViewState(
        val showEmailProtectionSetting: Boolean = true,
        val isEmailProtectionEnabled: Boolean = false,
    )

    sealed class Command {
        data class LaunchEmailProtection(val url: String) : Command()
        data object LaunchEmailProtectionNotSupported : Command()
    }

    companion object {
        const val EMAIL_PROTECTION_URL = "https://duckduckgo.com/email"
    }
}
