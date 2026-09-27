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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.impl.pixels.RemoteMessagingPixels
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity
import com.squareup.anvil.annotations.ContributesBinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface RemoteMessageAutoDismissEvaluator {

    /**
     * Whether [remoteMessage] has run out the display conditions
     *
     * @return true when the message must no longer be surfaced.
     */
    fun shouldAutoDismiss(
        remoteMessage: RemoteMessage,
        entity: RemoteMessageEntity,
    ): Boolean
}

@ContributesBinding(AppScope::class)
class RealRemoteMessageAutoDismissEvaluator @Inject constructor(
    private val remoteMessagingPixels: RemoteMessagingPixels,
    private val currentTimeProvider: CurrentTimeProvider,
) : RemoteMessageAutoDismissEvaluator {

    override fun shouldAutoDismiss(
        remoteMessage: RemoteMessage,
        entity: RemoteMessageEntity,
    ): Boolean {
        if (!remoteMessage.isExpired(entity.firstShownDate) && !remoteMessage.hasReachedImpressionCap(entity.impressions)) {
            return false
        }
        remoteMessagingPixels.fireRemoteMessageAutoDismissedPixel(remoteMessage)
        return true
    }

    private fun RemoteMessage.isExpired(firstShownDate: Long?): Boolean {
        val threshold = displayConditions?.dismissAfterDaysShown?.takeIf { it > 0 } ?: return false
        val firstShown = firstShownDate ?: return false
        val elapsedDays = TimeUnit.MILLISECONDS.toDays(currentTimeProvider.currentTimeMillis() - firstShown)
        return elapsedDays >= threshold
    }

    private fun RemoteMessage.hasReachedImpressionCap(impressions: Int): Boolean {
        val cap = displayConditions?.maxImpressions?.takeIf { it > 0 } ?: return false
        return impressions >= cap
    }
}
