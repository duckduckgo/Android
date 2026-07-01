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

package com.duckduckgo.sync.impl.promotion.chat

import android.annotation.SuppressLint
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.common.ui.setRoundCorners
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.inputscreen.NativeInputChatTabItem
import com.duckduckgo.duckchat.api.inputscreen.NativeInputChatTabItemPlugin
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.api.SyncActivityWithAnotherDevice
import com.duckduckgo.sync.impl.databinding.DialogChatSyncPromoBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NativeInputChatTabItemPlugin::class,
    defaultActiveValue = DefaultFeatureValue.INTERNAL,
    priority = NativeInputChatTabItemPlugin.PRIORITY_PROMO,
    featureName = "pluginChatSyncPromoChatTabItemPlugin",
    parentFeatureName = "pluginPointNativeInputChatTabItemPlugin",
)
class ChatSyncPromoChatTabItemPlugin @Inject constructor(
    private val chatSyncPromotion: ChatSyncPromotion,
    private val duckChatInput: DuckChatInputModeState,
    private val activityStarter: GlobalActivityStarter,
) : NativeInputChatTabItemPlugin {
    override fun create(
        context: Context,
        scope: CoroutineScope,
    ): NativeInputChatTabItem {
        val listener = ChatTabPluginAdapterListener(context, chatSyncPromotion, scope, activityStarter)
        val adapter = ChatSyncPromoAdapter(listener)

        val showJob = scope.showPromotionBanner(adapter)
        scope.hideBannerOnInput(adapter, showJob)

        return ChatSyncChatTabItem(adapter)
    }

    private fun CoroutineScope.showPromotionBanner(adapter: ChatSyncPromoAdapter): Job {
        return launch {
            if (chatSyncPromotion.canShowPromotion()) adapter.show()
        }
    }

    private fun CoroutineScope.hideBannerOnInput(
        adapter: ChatSyncPromoAdapter,
        showJob: Job,
    ) {
        launch {
            duckChatInput.inputQuery.firstOrNull(String::isNotEmpty) ?: return@launch
            showJob.cancelAndJoin()
            adapter.dismiss(shouldAnimate = false)
        }
    }
}

private class ChatSyncChatTabItem(
    private val adapter: ChatSyncPromoAdapter,
) : NativeInputChatTabItem {
    override val adapters: List<RecyclerView.Adapter<*>> get() = listOf(adapter)
}

private class ChatTabPluginAdapterListener(
    private val context: Context,
    private val promotion: ChatSyncPromotion,
    private val scope: CoroutineScope,
    private val activityStarter: GlobalActivityStarter,
) : ChatSyncPromoAdapter.Listener {
    private var didBannerShow = false
    private var isDialogShowing = false

    override fun onSyncWithDeviceClicked(adapter: ChatSyncPromoAdapter) {
        scope.launch { promotion.recordPromotionAccepted() }
        adapter.dismiss(shouldAnimate = true)

        if (!isDialogShowing) {
            isDialogShowing = true
            val dialog = ChatSyncPromoBottomSheetDialog(
                context = context,
                onScanQrCodeClicked = {
                    activityStarter.start(context, SyncActivityWithAnotherDevice(source = "promotion_ai_chat"))
                },
            )
            dialog.setOnDismissListener { isDialogShowing = false }
            dialog.show()
        }
    }

    override fun onDismissClicked(adapter: ChatSyncPromoAdapter) {
        scope.launch { promotion.recordPromotionDismissed() }
        adapter.dismiss(shouldAnimate = true)
    }

    override fun onBannerShown(adapter: ChatSyncPromoAdapter) {
        if (!didBannerShow) {
            didBannerShow = true
            scope.launch { promotion.incrementImpressionCount() }
        }
    }
}

@SuppressLint("NoBottomSheetDialog")
private class ChatSyncPromoBottomSheetDialog(
    context: Context,
    onScanQrCodeClicked: () -> Unit,
) : BottomSheetDialog(context) {
    init {
        val binding = DialogChatSyncPromoBinding.inflate(layoutInflater).apply {
            scanQrCodeButton.setOnClickListener {
                onScanQrCodeClicked()
                dismiss()
            }
            notNowButton.setOnClickListener { dismiss() }
            closeButton.setOnClickListener { dismiss() }
        }
        setContentView(binding.root)

        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = false

        setOnShowListener {
            setRoundCorners()
        }
    }
}
