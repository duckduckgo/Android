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

package com.duckduckgo.app.generalsettings.showonapplaunch.rmf

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.duckduckgo.remote.messaging.api.MessageTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Resolves the after-idle NTP context into [MessageTrigger.AFTER_IDLE]
 */
class AfterIdleMessageTriggerProvider @Inject constructor(
    private val ntpAfterIdleManager: NtpAfterIdleManager,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
) {

    fun activeTrigger(): Flow<MessageTrigger?> {
        val rolloutEnabled = androidBrowserConfigFeature.showNTPAfterIdleReturn().isEnabled() &&
            androidBrowserConfigFeature.ntpAsDefaultAfterIdleReturn().isEnabled()
        if (!rolloutEnabled) return flowOf(null)

        return ntpAfterIdleManager.isAfterIdleReturn.map { afterIdle ->
            if (afterIdle) MessageTrigger.AFTER_IDLE else null
        }
    }
}
