/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.dashboard.api.animations

import android.view.View
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.trackerdetection.model.Entity

interface BrowserTrackersAnimatorHelper {
    fun startTrackersAnimation(
        runPartialAnimation: Boolean,
        shieldAnimationView: LottieAnimationView,
        trackersAnimationView: LottieAnimationView,
        omnibarViews: List<View>,
        entities: List<Entity>?
    )

    fun cancelAnimations(
        omnibarViews: List<View>
    )
    fun setListener(animatorListener: TrackersAnimatorListener)
    fun removeListener()
    fun finishPartialTrackerAnimation()
}
