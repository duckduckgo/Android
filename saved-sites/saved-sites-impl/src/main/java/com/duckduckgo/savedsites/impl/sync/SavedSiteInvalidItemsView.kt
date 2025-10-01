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

package com.duckduckgo.savedsites.impl.sync

import android.content.Context
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.BookmarksScreenNoParams
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.saved.sites.impl.databinding.ViewSaveSiteSyncInvalidItemsWarningBinding
import com.duckduckgo.savedsites.impl.sync.SavedSiteInvalidItemsViewModel.Command
import com.duckduckgo.savedsites.impl.sync.SavedSiteInvalidItemsViewModel.Command.NavigateToBookmarks
import com.duckduckgo.savedsites.impl.sync.SavedSiteInvalidItemsViewModel.ViewState
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class SavedSiteInvalidItemsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private var job: ConflatedJob = ConflatedJob()
    private var conflatedStateJob: ConflatedJob = ConflatedJob()

    private val binding: ViewSaveSiteSyncInvalidItemsWarningBinding by viewBinding()

    private val viewModel: SavedSiteInvalidItemsViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SavedSiteInvalidItemsViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        val coroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope

        conflatedStateJob += viewModel.viewState()
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        job += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        job.cancel()
        conflatedStateJob.cancel()
    }

    private fun processCommands(command: Command) {
        when (command) {
            NavigateToBookmarks -> navigateToBookmarks()
        }
    }

    private fun render(viewState: ViewState) {
        this.isVisible = viewState.warningVisible

        val spannable = SpannableStringBuilder(
            context.resources.getQuantityString(
                R.plurals.saved_site_invalid_items_warning,
                viewState.invalidItemsSize,
                viewState.hint,
                viewState.invalidItemsSize - 1,
            ),
        ).append(context.getText(R.string.saved_site_invalid_items_warning_link))

        binding.savedSitesInvalidItemsWarning.setClickableLink(
            "manage_bookmarks",
            spannable,
            onClick = {
                viewModel.onWarningActionClicked()
            },
        )
    }

    private fun navigateToBookmarks() {
        globalActivityStarter.start(this.context, BookmarksScreenNoParams)
    }
}
