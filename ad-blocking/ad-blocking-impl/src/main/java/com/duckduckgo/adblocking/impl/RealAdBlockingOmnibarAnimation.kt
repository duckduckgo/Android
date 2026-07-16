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

package com.duckduckgo.adblocking.impl

import androidx.core.net.toUri
import com.duckduckgo.adblocking.api.AdBlockingAnimation
import com.duckduckgo.adblocking.api.AdBlockingOmnibarAnimationProvider
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(FragmentScope::class)
@ContributesBinding(scope = FragmentScope::class, boundType = AdBlockingOmnibarAnimationProvider::class)
class RealAdBlockingOmnibarAnimation @Inject constructor(
    private val statusChecker: AdBlockingStatusChecker,
    private val domainMatcher: AdBlockingExtensionDomainMatcher,
) : AdBlockingOmnibarAnimationProvider {

    private var lastAnimatedVideoId: String? = null

    override suspend fun getAnimation(url: String, pageChanged: Boolean): AdBlockingAnimation {
        // TODO (cbarreiro) Remove after fixing https://app.asana.com/1/137249556945/task/1216628472297441?focus=true
        return AdBlockingAnimation.Skip

        val videoId = videoIdOrNull(url)
        if (videoId == null) {
            lastAnimatedVideoId = null
            return AdBlockingAnimation.Skip
        }
        if (!statusChecker.canInject()) return AdBlockingAnimation.Skip
        return if (pageChanged) {
            // A load/reload is an "actual page change": a video page always animates (reload re-animates).
            lastAnimatedVideoId = videoId
            showBadge()
        } else {
            // In-page SPA change: animate only when moving to a different video than the last animated one.
            if (videoId == lastAnimatedVideoId) {
                AdBlockingAnimation.Retain
            } else {
                lastAnimatedVideoId = videoId
                showBadge()
            }
        }
    }

    private fun showBadge(): AdBlockingAnimation.Show =
        AdBlockingAnimation.Show(
            icon = R.drawable.ic_video_player_blocked_color_24,
            text = R.string.ad_blocking_omnibar_badge_text,
        )

    private fun videoIdOrNull(url: String): String? {
        val uri = url.toUri()
        if (!domainMatcher.matches(uri)) return null
        val segments = uri.pathSegments
        return when (segments.firstOrNull()) {
            "watch" -> uri.getQueryParameter("v")?.takeIf { it.isNotBlank() }
            "shorts", "live", "clip" -> segments.getOrNull(1)?.takeIf { it.isNotBlank() }
            else -> null
        }
    }
}
