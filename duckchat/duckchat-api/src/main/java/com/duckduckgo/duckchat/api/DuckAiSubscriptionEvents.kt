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

package com.duckduckgo.duckchat.api

import com.duckduckgo.js.messaging.api.SubscriptionEventData
import kotlinx.coroutines.flow.Flow

/**
 * Subscription events that native Duck.ai features push to Duck.ai pages open in a browser tab.
 */
interface DuckAiSubscriptionEvents {
    /**
     * Emits each event to send to Duck.ai pages open in a browser tab.
     * Events emitted while a tab isn't showing Duck.ai aren't delivered to it.
     */
    fun observe(): Flow<SubscriptionEventData>
}
