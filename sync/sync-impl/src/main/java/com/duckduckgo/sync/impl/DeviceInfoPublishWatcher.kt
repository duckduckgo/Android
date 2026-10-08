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

package com.duckduckgo.sync.impl

import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Tracks first publishes of this device's `device_info` so a `getDevices` read can tell whether it was in flight before we published.
 * Without this, a response that raced our first write sees its own blob missing and mistakes a stale read for a server-side gap.
 */
@SingleInstanceIn(AppScope::class)
class DeviceInfoPublishWatcher @Inject constructor() {

    // a counter, not a boolean: a snapshot compares the count before and after a fetch, so publishes that happen and are consumed
    // between two reads still register as "something changed" rather than being lost to a flag that was already true
    private val publishes = AtomicInteger(0)

    fun snapshot(): Int = publishes.get()

    fun markPublished() {
        publishes.incrementAndGet()
    }

    fun publishedSince(snapshot: Int): Boolean = publishes.get() != snapshot
}
