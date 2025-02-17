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

package com.duckduckgo.common.ui.internal.experiments.visual

import android.content.Context
import android.util.AttributeSet
import android.widget.CompoundButton
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.internal.databinding.ViewVisualDesignExperimentBinding
import com.duckduckgo.common.ui.internal.experiments.visual.VisualDesignExperimentViewModel.ViewState
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class VisualDesignExperimentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    private val binding: ViewVisualDesignExperimentBinding by viewBinding()

    private val viewModel: VisualDesignExperimentViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[VisualDesignExperimentViewModel::class.java]
    }

    private val experimentalUIToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onExperimentalUIModeChanged(isChecked)
    }

    private val warmColorsPaletteToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onWarmColorsFlagChanged(isChecked)
    }

    private val iconsToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onIconsFlagChanged(isChecked)
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        val coroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope

        viewModel.viewState()
            .onEach { render(it) }
            .launchIn(coroutineScope!!)
    }

    private fun render(viewState: ViewState) {
        binding.experimentalUIMode.quietlySetIsChecked(viewState.isBrowserThemingFeatureEnabled, experimentalUIToggleListener)
        binding.experimentalUIColorPalette.quietlySetIsChecked(viewState.useWarmColors, warmColorsPaletteToggleListener)
        binding.experimentalUIIcons.quietlySetIsChecked(viewState.experimentalIcons, iconsToggleListener)
        Snackbar.make(binding.root, "Selected theme is ${viewState.selectedTheme}", Snackbar.LENGTH_SHORT).show()
    }
}
