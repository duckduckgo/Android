/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.fire

import android.content.Context
import androidx.annotation.UiThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.airbnb.lottie.LottieCompositionFactory
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.settings.db.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface FireAnimationLoader : LifecycleObserver {
    fun preloadSelectedAnimation()
}

class LottieFireAnimationLoader
constructor(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider,
    private val appCoroutineScope: CoroutineScope
) : FireAnimationLoader {

    @UiThread
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    override fun preloadSelectedAnimation() {
        appCoroutineScope.launch(dispatchers.io()) {
            if (animationEnabled()) {
                val selectedFireAnimation = settingsDataStore.selectedFireAnimation
                LottieCompositionFactory.fromRawRes(context, selectedFireAnimation.resId)
            }
        }
    }

    private fun animationEnabled() = settingsDataStore.fireAnimationEnabled
}
