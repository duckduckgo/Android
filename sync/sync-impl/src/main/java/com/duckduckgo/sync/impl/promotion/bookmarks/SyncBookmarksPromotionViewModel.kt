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

package com.duckduckgo.sync.impl.promotion.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
import com.duckduckgo.sync.impl.promotion.SyncPromotions
import com.duckduckgo.sync.impl.promotion.bookmarks.SyncBookmarksPromotionViewModel.Command.LaunchSyncSettings
import com.duckduckgo.sync.impl.promotion.bookmarks.SyncBookmarksPromotionViewModel.Command.ReevalutePromo
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ViewScope::class)
class SyncBookmarksPromotionViewModel @Inject constructor(
    private val syncPromotions: SyncPromotions,
    private val pixel: Pixel,
) : ViewModel() {

    sealed interface Command {
        data object LaunchSyncSettings : Command
        data object ReevalutePromo : Command
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    fun onUserSelectedSetUpSyncFromPromo() {
        viewModelScope.launch {
            command.send(LaunchSyncSettings)
        }
        pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, sourceMap())
    }

    fun onUserCancelledSyncPromo() {
        viewModelScope.launch {
            syncPromotions.recordBookmarksPromotionDismissed()
            command.send(ReevalutePromo)
        }
        pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISMISSED, sourceMap())
    }

    fun onPromoShown() {
        pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISPLAYED, sourceMap())
    }

    private fun sourceMap() = mapOf(SyncPixelParameters.SYNC_FEATURE_PROMOTION_SOURCE to "bookmarks")
}
