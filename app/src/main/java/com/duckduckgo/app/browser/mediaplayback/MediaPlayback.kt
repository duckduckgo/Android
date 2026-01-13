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

package com.duckduckgo.app.browser.mediaplayback

import androidx.core.net.toUri
import com.duckduckgo.app.browser.UriString.Companion.sameOrSubdomain
import com.duckduckgo.app.browser.mediaplayback.store.MediaPlaybackRepository
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface MediaPlayback {
    fun doesMediaPlaybackRequireUserGestureForUrl(url: String): Boolean
}

@ContributesBinding(AppScope::class)
class RealMediaPlayback @Inject constructor(
    private val mediaPlaybackFeature: MediaPlaybackFeature,
    private val mediaPlaybackRepository: MediaPlaybackRepository,
) : MediaPlayback {

    override fun doesMediaPlaybackRequireUserGestureForUrl(url: String): Boolean {
        val uri = url.toUri()
        if (mediaPlaybackRepository.exemptedDomains.any { sameOrSubdomain(uri, it) }) return false
        return mediaPlaybackFeature.self().isEnabled() && mediaPlaybackRepository.exceptions.none { it.domain == uri.baseHost }
    }
}
