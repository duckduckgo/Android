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

package com.duckduckgo.adblocking.impl.duckplayer

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.view.isGone
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayerSettingsNoParams
import com.duckduckgo.adblocking.impl.R
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsEntryViewModel.Command
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsEntryViewModel.Command.OpenSettings
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ViewScope::class)
class DuckPlayerSettingsEntryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    init {
        orientation = VERTICAL
    }

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val listItem: OneLineListItem by lazy {
        OneLineListItem(context).apply {
            setLeadingIconResource(CommonR.drawable.ic_video_player_color_24)
            setPrimaryText(context.getString(R.string.duck_player_setting_title))
        }
    }

    private val viewModel: DuckPlayerSettingsEntryViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[DuckPlayerSettingsEntryViewModel::class.java]
    }

    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        if (childCount == 0) {
            addView(listItem)
        }
        listItem.setOnClickListener { viewModel.onSettingClicked() }

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)
        val coroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope

        conflatedStateJob += viewModel.viewState
            .onEach { isGone = !it.isVisible }
            .launchIn(coroutineScope!!)

        conflatedCommandJob += viewModel.commands()
            .onEach { processCommand(it) }
            .launchIn(coroutineScope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
    }

    private fun processCommand(command: Command) {
        when (command) {
            is OpenSettings -> globalActivityStarter.start(context, DuckPlayerSettingsNoParams, null)
        }
    }
}
