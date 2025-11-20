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

package com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.bookmarks.BookmarkAddedDialogPlugin
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.duckduckgo.sync.api.SyncState
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.promotion.SyncPromotions
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(BookmarkAddedDialogPlugin.PRIORITY_KEY_SETUP_SYNC)
class SetupSyncBookmarkAddedPromo @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    private val dispatchers: DispatcherProvider,
    private val activity: AppCompatActivity,
    private val syncPromotions: SyncPromotions,
    private val syncStateMonitor: SyncStateMonitor,
    private val syncPixels: SyncPixels,
) : BookmarkAddedDialogPlugin {

    @SuppressLint("InflateParams")
    override suspend fun getView(): View? {
        if (!syncPromotions.canShowBookmarkAddedDialogPromotion()) {
            return null
        }

        val root = LayoutInflater.from(activity).inflate(R.layout.view_sync_setup_bookmark_added_promo, null) as OneLineListItem
        root.setOnClickListener {
            onLaunchSyncFlow(activity)
            syncPixels.fireSetupSyncPromoBookmarkAddedDialogConfirmed()
        }
        root.configureOverflowMenu()
        root.configureViewAttachedListener()

        activity.lifecycleScope.launch {
            syncStateMonitor.syncState().collect { syncState -> onSyncStateAvailable(syncState, root) }
        }

        return root
    }

    private fun onLaunchSyncFlow(activity: AppCompatActivity) {
        val intent = globalActivityStarter.startIntent(activity, SyncActivityWithEmptyParams)
        activity.startActivity(intent)
    }

    private fun onSyncStateAvailable(syncState: SyncState, root: OneLineListItem) {
        when (syncState) {
            SyncState.READY, SyncState.IN_PROGRESS -> {
                root.gone()
            }
            else -> {
                // no-op
            }
        }
    }

    private fun OneLineListItem.configureOverflowMenu() {
        showTrailingIcon()
        setTrailingIconClickListener { overflowView ->
            val layoutInflater = LayoutInflater.from(context)
            val popupMenu = buildPopupMenu(this, layoutInflater)
            popupMenu.show(this, overflowView)
        }
    }

    private fun buildPopupMenu(
        rootView: View,
        layoutInflater: LayoutInflater,
    ): PopupMenu {
        val popupMenu = PopupMenu(layoutInflater, R.layout.popup_window_hide_sync_suggestion_menu)
        val hideButton = popupMenu.contentView.findViewById<View>(R.id.hide)

        popupMenu.apply {
            onMenuItemClicked(hideButton) {
                activity.lifecycleScope.launch(dispatchers.main()) {
                    rootView.gone()
                    syncPromotions.recordBookmarkAddedDialogPromotionDismissed()
                    syncPixels.fireSetupSyncPromoBookmarkAddedDialogDismissed()
                }
            }
        }

        return popupMenu
    }

    private fun OneLineListItem.configureViewAttachedListener() {
        this.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                syncPixels.fireSetupSyncPromoBookmarkAddedDialogShown()
            }

            override fun onViewDetachedFromWindow(v: View) {
                // no-op
            }
        })
    }
}
