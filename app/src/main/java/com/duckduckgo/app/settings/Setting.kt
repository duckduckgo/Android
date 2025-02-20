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
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.duckduckgo.common.ui.SearchStatus
import com.duckduckgo.common.ui.SearchStatus.MISS
import com.duckduckgo.common.ui.SearchStatus.NONE
import com.duckduckgo.common.ui.Searchable
import com.duckduckgo.common.utils.ConflatedJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

@SuppressLint("ViewConstructor")
abstract class SettingNodeView<C, VS, VM : SettingViewModel<C, VS>>(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    override val searchableId: UUID,
) : FrameLayout(context, attrs, defStyle), Searchable {

    protected val viewModel: VM by lazy {
        provideViewModel()
    }

    protected abstract fun provideViewModel(): VM

    private var conflatedCommandsJob: ConflatedJob = ConflatedJob()
    private var conflatedStateJob: ConflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val viewTreeLifecycleOwner = findViewTreeLifecycleOwner()!!
        viewTreeLifecycleOwner.lifecycle.addObserver(viewModel)
        val coroutineScope = viewTreeLifecycleOwner.lifecycleScope

        conflatedCommandsJob += viewModel.commands
            .onEach { processCommands(it) }
            .launchIn(coroutineScope)

        conflatedStateJob += viewModel.viewState
            .onEach { renderView(it) }
            .launchIn(coroutineScope)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        conflatedCommandsJob.cancel()
        conflatedStateJob.cancel()
    }

    final override fun setSearchStatus(status: SearchStatus) {
        viewModel.setSearchStatus(status)
    }

    protected abstract fun renderView(viewState: VS)

    protected open fun processCommands(command: C) {}
}

abstract class SettingViewModel<C, V>(defaultViewState: V) : ViewModel(), DefaultLifecycleObserver {
    protected val _commands = Channel<C>(capacity = Channel.CONFLATED)
    val commands: Flow<C> = _commands.receiveAsFlow()

    private val searchStatus = MutableStateFlow(NONE)
    protected val _viewState = MutableStateFlow(defaultViewState)
    val viewState = _viewState.combine(searchStatus) { viewState, searchStatus ->
        if (searchStatus == MISS) {
            getSearchMissViewState()
        } else {
            viewState
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultViewState)

    abstract fun getSearchMissViewState(): V

    fun setSearchStatus(status: SearchStatus) {
        searchStatus.value = status
    }
}
