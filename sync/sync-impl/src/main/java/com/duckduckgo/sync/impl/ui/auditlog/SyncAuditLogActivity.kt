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

package com.duckduckgo.sync.impl.ui.auditlog

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.codegen.annotations.ContributeToActivityStarter
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivitySyncAuditLogBinding
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.Command
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.Content
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * [entriesPerPage] null uses the server's default page size.
 * [openedFromNotification] true closes the screen at once if the audit log flag was turned off after the notification was posted.
 */
data class SyncAuditLogScreenParams(
    val entriesPerPage: Int? = null,
    val openedFromNotification: Boolean = false,
) : GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SyncAuditLogScreenParams::class)
class SyncAuditLogActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var syncAuditLogViewModelFactory: SyncAuditLogViewModel.Factory

    private val binding: ActivitySyncAuditLogBinding by viewBinding()
    private val adapter = SyncAuditLogAdapter(onRetryClicked = { viewModel.onRetryClicked() })

    private val viewModel: SyncAuditLogViewModel by lazy {
        val params = intent.getActivityParams(SyncAuditLogScreenParams::class.java) ?: SyncAuditLogScreenParams()
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = syncAuditLogViewModelFactory.create(params) as T
            },
        )[SyncAuditLogViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        configureEdgeToEdgeInsets()
        binding.syncAuditLogList.adapter = adapter
        binding.syncAuditLogList.addOnScrollListener(listEndListener)

        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { render(it) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            Command.Close -> finish()
        }
    }

    private val listEndListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                viewModel.onUserScrolled()
                checkListEnd()
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            checkListEnd()
        }
    }

    private fun checkListEnd() {
        val layoutManager = binding.syncAuditLogList.layoutManager as LinearLayoutManager
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        if (lastVisible == RecyclerView.NO_POSITION) return
        if (lastVisible >= adapter.itemCount - LOAD_MORE_THRESHOLD) viewModel.onNearListEnd()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.syncAuditLogList)
    }

    private fun render(state: ViewState) {
        val content = state.content
        binding.loadingIndicator.isVisible = content == Content.LOADING
        binding.syncAuditLogList.isVisible = content == Content.LIST
        binding.messageText.isVisible = content == Content.EMPTY || content == Content.ONLY_THIS_DEVICE || content == Content.ERROR
        when (content) {
            Content.EMPTY -> binding.messageText.setText(R.string.syncAuditLogEmpty)
            Content.ONLY_THIS_DEVICE -> binding.messageText.setText(R.string.syncAuditLogOnlyThisDevice)
            Content.ERROR -> binding.messageText.setText(R.string.syncAuditLogError)
            Content.LOADING, Content.LIST -> Unit
        }
        // onScrolled runs only when the visible range changes, so a page that adds rows below the screen would not trigger it.
        adapter.submitRows(state.rows, state.footer) { binding.syncAuditLogList.post { checkListEnd() } }
    }

    private companion object {
        // Rows from the end at which the next page starts to load, so it can arrive before the user reaches the end.
        const val LOAD_MORE_THRESHOLD = 5
    }
}
