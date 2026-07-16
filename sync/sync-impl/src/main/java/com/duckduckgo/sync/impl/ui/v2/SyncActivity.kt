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

package com.duckduckgo.sync.impl.ui.v2

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2Binding
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
class SyncActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2Binding>()

    private val viewModel by bindViewModel<SyncActivityViewModel>()

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val syncedDeviceAdapter = SyncedDeviceAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isEdgeToEdge = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SYNC)
        if (isEdgeToEdge) {
            enableTransparentEdgeToEdge()
        }
        setContentView(binding.root)
        if (isEdgeToEdge) {
            configureEdgeToEdgeInsets()
        }

        configureToolbar()
        configureDevicesRecyclerView()
        configureDataExpirationNotice()
        configureDataDeletionItem()

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        binding.syncHeader.setState(
            isSyncEnabled = viewState.showAccount,
            isDuckAiAvailable = viewState.aiChatSyncEnabled,
        )

        binding.includeDisabledView.root.isGone = viewState.showAccount
        binding.includeDisabledView.syncOnOtherPlatformsItem.setState(
            isNewDesktopBrowserAvailable = viewState.newDesktopBrowserSettingEnabled,
        )

        binding.includeEnabledView.root.isVisible = viewState.showAccount
        syncedDeviceAdapter.submitList(viewState.syncedDevices)

        binding.includeEnabledView.syncOnOtherPlatformsItem.setState(
            isNewDesktopBrowserAvailable = viewState.newDesktopBrowserSettingEnabled,
        )

        binding.includeEnabledView.restoreOnReinstallItem.isVisible = viewState.showAutoRestoreToggle
        if (viewState.showAutoRestoreToggle) {
            binding.includeEnabledView.restoreOnReinstallItem.quietlySetIsChecked(viewState.autoRestoreEnabled, changeListener = null)
            binding.includeEnabledView.restoreOnReinstallItem.setLeadingIconResource(
                if (viewState.autoRestoreEnabled) R.drawable.device_default_24 else R.drawable.device_soft_alert_24,
            )
        }
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentScrollView, drawBehindGestureNav = true)
    }

    private fun configureToolbar() {
        setSupportActionBar(binding.includeToolbar.toolbar)
        binding.includeToolbar.toolbar.setNavigationIcon(CommonR.drawable.ic_arrow_left_24)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun configureDevicesRecyclerView() {
        binding.includeEnabledView.devicesRecycler.apply {
            layoutManager = LinearLayoutManager(this@SyncActivity)
            adapter = syncedDeviceAdapter
        }
    }

    private fun configureDataExpirationNotice() {
        binding.includeEnabledView.expirationNoticeLabel.addClickableSpan(
            textSequence = getText(R.string.sync_settings_data_expiration),
            spans = listOf(
                "learn_more_link" to object : DuckDuckGoClickableSpan() {
                    override fun onClick(widget: View) {
                        logcat { "Learn more clicked" }
                    }
                },
            ),
        )
    }

    private fun configureDataDeletionItem() {
        val color = ColorStateList.valueOf(getColorFromAttr(CommonR.attr.daxColorDestructive))
        binding.includeEnabledView.deleteAccountItem.apply {
            leadingIcon().imageTintList = color
            setPrimaryTextColorStateList(color)
        }
    }
}
