/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.health

data class Tracer(
    val tracerId: String,
    val creationTimestampMillis: Long,
    val events: List<TracerEvent>
)

data class TracerEvent(
    val tracerId: String,
    val event: TracedState,
    val timestampNanos: Long = System.nanoTime()
)

enum class TracedState {
    CREATED,
    ADDED_TO_DEVICE_TO_NETWORK_QUEUE,
    REMOVED_FROM_DEVICE_TO_NETWORK_QUEUE,
    ADDED_TO_NETWORK_TO_DEVICE_QUEUE,
    REMOVED_FROM_NETWORK_TO_DEVICE_QUEUE,
}
