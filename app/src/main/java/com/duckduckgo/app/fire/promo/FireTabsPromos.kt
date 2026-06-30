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

package com.duckduckgo.app.fire.promo

import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Local (client-side) state for the Fire Tabs tab-switcher banner.
 */
interface FireTabsPromos {
    /** True if the tab-switcher banner hasn't been shown/dismissed yet (show-once). */
    suspend fun canShowTabSwitcherPromo(): Boolean
    suspend fun onTabSwitcherPromoShown()
    suspend fun onFireModeEntered()
}

@ContributesBinding(AppScope::class)
class RealFireTabsPromos @Inject constructor(
    private val fireDataStore: FireDataStore,
    private val remoteMessageModel: RemoteMessageModel,
    private val dispatchers: DispatcherProvider,
) : FireTabsPromos {

    override suspend fun canShowTabSwitcherPromo(): Boolean = withContext(dispatchers.io()) {
        !fireDataStore.isTabSwitcherPromoDismissed()
    }

    override suspend fun onTabSwitcherPromoShown() {
        withContext(dispatchers.io()) { fireDataStore.setTabSwitcherPromoDismissed(true) }
    }

    override suspend fun onFireModeEntered() {
        withContext(dispatchers.io()) {
            fireDataStore.setUsedFireMode(true)
            fireDataStore.setTabSwitcherPromoDismissed(true)
            val activeMessage = remoteMessageModel.getActiveMessage()
            if ((activeMessage?.content as? Content.BigTwoActions)?.placeholder == Content.Placeholder.FIRE_TABS) {
                remoteMessageModel.onMessageDismissed(activeMessage)
            }
        }
    }
}
