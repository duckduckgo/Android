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

package com.duckduckgo.browser.ui.newtab.hatch

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.browser.ui.databinding.ViewNewTabHatchBinding
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.ViewScope
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ViewScope::class)
class NewTabReturnHatchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    interface ItemPressedListener {
        fun onHatchPressed()
    }

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var faviconManager: FaviconManager

    private val binding: ViewNewTabHatchBinding by viewBinding()

    private val conflatedJob = ConflatedJob()

    private var hatchItemPressedListener: ItemPressedListener? = null

    private val viewModel: NewTabReturnHatchViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[NewTabReturnHatchViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        conflatedJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        conflatedJob.cancel()
    }

    val tabId: String
        get() = viewModel.viewState.value.tabId

    fun render(state: NewTabReturnHatchViewModel.ViewState) {
        if (state.shouldShow) {
            binding.returnHatchSiteTitle.text = state.tabTitle
            binding.returnHatchSiteURL.text = state.url.extractDomain()
            binding.returnHatchRoot.show()
            viewModel.viewModelScope.launch {
                faviconManager.loadToViewFromLocalWithPlaceholder(state.tabId, state.url, binding.returnHatchFavicon)
            }
        } else {
            binding.returnHatchRoot.gone()
        }
    }

    fun setHatchPressedListener(itemPressedListener: ItemPressedListener) {
        hatchItemPressedListener = itemPressedListener
        binding.returnHatchRoot.setOnClickListener {
            hatchItemPressedListener?.onHatchPressed()
        }
    }
}
