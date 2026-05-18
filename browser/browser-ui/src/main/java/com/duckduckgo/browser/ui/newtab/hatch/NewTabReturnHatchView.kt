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
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.browser.api.ui.BrowserScreens.TabSwitcherScreenNoParams
import com.duckduckgo.browser.ui.R
import com.duckduckgo.browser.ui.databinding.ViewNewTabHatchBinding
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ViewScope::class)
class NewTabReturnHatchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    interface HatchListener {
        fun onHatchPressed()
        fun onHatchRendered(visible: Boolean)
        fun onBurnTabPressed()
        fun onAfterInactivityPressed()
    }

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: ViewNewTabHatchBinding by viewBinding()

    private val conflatedJob = ConflatedJob()
    private val faviconJob = ConflatedJob()

    private var hatchHatchListener: HatchListener? = null

    private val viewModel: NewTabReturnHatchViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[NewTabReturnHatchViewModel::class.java]
    }

    private val popupMenu by lazy {
        PopupMenu(LayoutInflater.from(context), R.layout.popup_hatch_menu)
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        conflatedJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)

        viewModel.commands
            .onEach { processCommand(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)

        initPopupMenu()
    }

    private fun processCommand(command: NewTabReturnHatchViewModel.Command) {
        when (command) {
            NewTabReturnHatchViewModel.Command.LaunchTabSwitcher ->
                globalActivityStarter.start(context, TabSwitcherScreenNoParams)
            is NewTabReturnHatchViewModel.Command.ShowTabClosedSnackbar ->
                showTabClosedSnackbar(command.tabId)
        }
    }

    private fun showTabClosedSnackbar(tabId: String) {
        Snackbar.make(rootView, R.string.newTabReturnHatchTabClosed, Snackbar.LENGTH_LONG)
            .setAction(R.string.newTabReturnHatchUndo) { viewModel.onUndoCloseTab(tabId) }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(snackbar: Snackbar, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) viewModel.onTabClosedSnackbarDismissed(tabId)
                }
            })
            .show()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        conflatedJob.cancel()
        faviconJob.cancel()
    }

    val tabId: String
        get() = viewModel.viewState.value.tabId

    fun render(state: NewTabReturnHatchViewModel.ViewState) {
        faviconJob.cancel()
        if (state.shouldShow) {
            binding.returnHatchSiteTitle.text = state.titleOrPlaceholder()
            if (state.isDuckChat) {
                binding.returnHatchFavicon.setImageResource(CommonR.drawable.ic_duckai)
            } else {
                faviconJob += viewModel.viewModelScope.launch {
                    faviconManager.loadToViewFromLocalWithRetry(state.tabId, state.url, binding.returnHatchFavicon)
                }
            }

            if (state.showTabsButton) {
                binding.returnHatchTabsMenu.count = state.tabs
                binding.returnHatchTabsMenu.show()
            } else {
                binding.returnHatchTabsMenu.gone()
            }
            binding.returnHatchRoot.show()
        } else {
            binding.returnHatchRoot.gone()
        }
        hatchHatchListener?.onHatchRendered(state.shouldShow)
    }

    private fun initPopupMenu() {
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.hatchMenuReturnToTab)) {
            hatchHatchListener?.onHatchPressed()
        }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.hatchMenuCloseTab)) {
            viewModel.closeTab()
        }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.hatchMenuBurnTab)) {
            viewModel.onBurnTabPressed()
            hatchHatchListener?.onBurnTabPressed()
        }
        popupMenu.onMenuItemClicked(popupMenu.contentView.findViewById(R.id.hatchMenuAfterInactivity)) {
            hatchHatchListener?.onAfterInactivityPressed()
        }
    }

    private fun NewTabReturnHatchViewModel.ViewState.titleOrPlaceholder(): String {
        if (tabTitle.isNotEmpty()) return tabTitle
        if (isDuckChat) return context.getString(R.string.newTabReturnHatchDuckChatPlaceholderTitle)
        if (isSerp) return context.getString(R.string.newTabReturnHatchSerpPlaceholderTitle)
        return tabTitle
    }

    fun setHatchListener(hatchListener: HatchListener) {
        hatchHatchListener = hatchListener
        binding.returnHatchRoot.setOnClickListener {
            hatchHatchListener?.onHatchPressed()
        }
        binding.returnHatchOptions.setOnClickListener { view ->
            popupMenu.show(binding.root, view)
        }
        binding.returnHatchTabsMenu.setOnClickListener { view ->
            viewModel.onTabManagerPressed()
        }
    }
}
