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

package com.duckduckgo.app.browser.omnibar.animations.omnibaranimation

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

interface OmnibarAnimationManager {
    fun isFeatureEnabled(): Boolean
    fun getChangeBoundsDuration(): Long
    fun getFadeDuration(): Long
    fun getTension(): Float
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, OmnibarAnimationManager::class)
@ContributesMultibinding(AppScope::class, PrivacyConfigCallbackPlugin::class)
class RealOmnibarAnimationManager @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    @IsMainProcess private val isMainProcess: Boolean,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : OmnibarAnimationManager, PrivacyConfigCallbackPlugin {

    private var isFeatureEnabled = false
    private var changeBoundsDuration = DEFAULT_CHANGE_BOUNDS_DURATION
    private var fadeDuration = DEFAULT_FADE_DURATION
    private var tension = DEFAULT_TENSION

    init {
        if (isMainProcess) {
            loadToMemory()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        loadToMemory()
    }

    override fun isFeatureEnabled(): Boolean {
        return isFeatureEnabled
    }

    override fun getChangeBoundsDuration(): Long {
        return changeBoundsDuration
    }

    override fun getFadeDuration(): Long {
        return fadeDuration
    }

    override fun getTension(): Float {
        return tension
    }

    private fun loadToMemory() {
        appCoroutineScope.launch(dispatchers.io()) {
            isFeatureEnabled = androidBrowserConfigFeature.omnibarAnimation().isEnabled()
            androidBrowserConfigFeature.omnibarAnimation().getSettings()?.let {
                JSONObject(it).let { settings ->
                    runCatching {
                        changeBoundsDuration = settings.getLong("changeBoundsDuration")
                        fadeDuration = settings.getLong("fadeDuration")
                        tension = settings.getDouble("tension").toFloat()
                    }
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_CHANGE_BOUNDS_DURATION = 400L
        private const val DEFAULT_FADE_DURATION = 200L
        private const val DEFAULT_TENSION = 1F
    }
}
