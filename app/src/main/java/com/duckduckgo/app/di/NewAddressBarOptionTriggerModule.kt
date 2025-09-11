/*
 * Copyright (c) 2025 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 ( the "License" );
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

package com.duckduckgo.app.di

import com.duckduckgo.app.browser.newaddressbaroption.NewAddressBarOptionDataStore
import com.duckduckgo.app.browser.newaddressbaroption.NewAddressBarOptionManager
import com.duckduckgo.app.browser.newaddressbaroption.RealNewAddressBarOptionManager
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides

@ContributesTo(AppScope::class)
@Module
object NewAddressBarOptionTriggerModule {

    @Provides
    fun provideNewAddressBarOptionTrigger(
        duckAiFeatureState: DuckAiFeatureState,
        userStageStore: UserStageStore,
        duckChat: DuckChat,
        remoteMessagingRepository: RemoteMessagingRepository,
        newAddressBarOptionDataStore: NewAddressBarOptionDataStore,
        settingsDataStore: SettingsDataStore,
    ): NewAddressBarOptionManager {
        return RealNewAddressBarOptionManager(
            duckAiFeatureState = duckAiFeatureState,
            userStageStore = userStageStore,
            duckChat = duckChat,
            remoteMessagingRepository = remoteMessagingRepository,
            newAddressBarOptionDataStore = newAddressBarOptionDataStore,
            settingsDataStore = settingsDataStore,
        )
    }
}
