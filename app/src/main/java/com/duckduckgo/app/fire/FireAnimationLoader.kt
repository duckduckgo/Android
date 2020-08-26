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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.airbnb.lottie.LottieCompositionFactory
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.statistics.VariantManager
import javax.inject.Inject

class FireAnimationLoader @Inject constructor(
    private val variantManager: VariantManager,
    private val context: Context
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppStart() {
        if (variantManager.getVariant().hasFeature(VariantManager.VariantFeature.FireButtonEducation)) {
            LottieCompositionFactory.fromRawRes(context, R.raw.fire_hero_rising)
        }
    }
}
