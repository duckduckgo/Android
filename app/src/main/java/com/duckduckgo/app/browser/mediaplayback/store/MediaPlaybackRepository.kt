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

package com.duckduckgo.app.browser.mediaplayback.store

import com.duckduckgo.app.browser.mediaplayback.MediaPlaybackFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

interface MediaPlaybackRepository {
    val exceptions: CopyOnWriteArrayList<FeatureException>
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = MediaPlaybackRepository::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@SingleInstanceIn(AppScope::class)
class RealMediaPlaybackRepository @Inject constructor(
    private val mediaPlaybackFeature: MediaPlaybackFeature,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @IsMainProcess private val isMainProcess: Boolean,
) : MediaPlaybackRepository, PrivacyConfigCallbackPlugin {

    override val exceptions = CopyOnWriteArrayList<FeatureException>()

    init {
        loadToMemory()
    }

    override fun onPrivacyConfigDownloaded() {
        loadToMemory()
    }

    private fun loadToMemory() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                exceptions.clear()
                exceptions.addAll(mediaPlaybackFeature.self().getExceptions())
            }
        }
    }
}
