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

package com.duckduckgo.browser.ui.omnibar.animations

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.omnibar.TrackersAnimatorListener
import com.duckduckgo.app.trackerdetection.model.Entity

/** Public interface for the Browser URL Bar Privacy and Trackers animations */
interface BrowserTrackersAnimatorHelper {

    fun startTrackersAnimation(
        context: Context,
        shieldAnimationView: LottieAnimationView,
        trackersAnimationView: LottieAnimationView,
        omnibarViews: List<View>,
        entities: List<Entity>?,
        useLightAnimation: Boolean? = null,
    )

    fun startAddressBarTrackersAnimation(
        context: Context,
        sceneRoot: ViewGroup,
        animatedIconBackgroundView: View,
        addressBarTrackersBlockedAnimationShieldIcon: LottieAnimationView,
        omnibarViews: List<View>,
        shieldViews: List<View>,
        entities: List<Entity>?,
        customBackgroundColor: Int? = null,
    )

    fun createCookiesAnimation(
        context: Context,
        omnibarViews: List<View>,
        shieldViews: List<View>,
        cookieBackground: View,
        cookieAnimationView: LottieAnimationView,
        cookieScene: ViewGroup,
        cookieCosmeticHide: Boolean,
        enqueueCookieAnimation: Boolean,
    )

    fun cancelAnimations(
        omnibarViews: List<View>,
    )

    fun setListener(animatorListener: TrackersAnimatorListener)

    fun removeListener()
}
