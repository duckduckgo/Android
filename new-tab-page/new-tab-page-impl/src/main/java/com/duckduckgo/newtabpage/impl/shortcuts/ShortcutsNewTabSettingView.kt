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

package com.duckduckgo.newtabpage.impl.shortcuts

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.api.NewTabPageSectionSettingsPlugin
import com.duckduckgo.newtabpage.impl.databinding.ViewNewTabShortcutsSettingItemBinding
import com.duckduckgo.newtabpage.impl.shortcuts.ShortcutsNewTabSettingsViewModel.ViewState
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.logcat

@InjectWith(ViewScope::class)
class ShortcutsNewTabSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    private val binding: ViewNewTabShortcutsSettingItemBinding by viewBinding()

    private var coroutineScope: CoroutineScope? = null

    private val viewModel: ShortcutsNewTabSettingsViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ShortcutsNewTabSettingsViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        ViewTreeLifecycleOwner.get(this)?.lifecycle?.addObserver(viewModel)

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)
    }

    private fun render(viewState: ViewState) {
        binding.root.quietlySetIsChecked(viewState.enabled) { _, enabled ->
            viewModel.onSettingEnabled(enabled)
        }
    }
}

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(NewTabPageSectionSettingsPlugin.SHORTCUTS)
class ShortcutsNewTabSettingViewPlugin @Inject constructor() : NewTabPageSectionSettingsPlugin {
    override val name = NewTabPageSection.SHORTCUTS.name

    override fun getView(context: Context): View {
        return ShortcutsNewTabSettingView(context)
    }

    override suspend fun isActive(): Boolean {
        return true
    }
}

interface NewTabShortcutsSectionSetting {
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean
    fun isEnabledFlow(): Flow<Boolean>
}

@ContributesBinding(scope = AppScope::class)
class RealNewTabShortcutsSectionSetting @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : NewTabShortcutsSectionSetting {

    init {
        appCoroutineScope.launch {
            isEnabledStateFlow.emit(isEnabled())
        }
    }

    private val isEnabledStateFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override fun setEnabled(enabled: Boolean) {
        preferences?.edit(commit = true) {
            putBoolean(KEY_SHORTCUTS_ENABLED, enabled)
        }
        appCoroutineScope.launch(dispatcherProvider.io()) {
            logcat { "New Tab: emit isEnabledStateFlow $enabled" }
            isEnabledStateFlow.emit(enabled)
        }
    }

    override fun isEnabledFlow(): StateFlow<Boolean> = isEnabledStateFlow.asStateFlow()

    override fun isEnabled(): Boolean {
        return preferences?.getBoolean(KEY_SHORTCUTS_ENABLED, true) ?: true
    }

    private val preferences: SharedPreferences? by lazy {
        runCatching {
            sharedPreferencesProvider.getSharedPreferences(PREFS_FILENAME, multiprocess = false, migrate = false)
        }.getOrNull()
    }

    companion object {
        private const val PREFS_FILENAME = "com.duckduckgo.newtabpage.shortcuts.settings.v1"
        private const val KEY_SHORTCUTS_ENABLED = "KEY_SHORTCUTS_ENABLED"
    }
}
