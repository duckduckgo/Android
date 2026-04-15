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

package com.duckduckgo.app.anr.ndk

import com.duckduckgo.android_crashkit.Crashpad
import com.duckduckgo.anrs.api.CrashBreadcrumbs
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealCrashBreadcrumbs @Inject constructor() : CrashBreadcrumbs {

    private val index = AtomicInteger(0)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    override fun add(tag: String, message: String) {
        val slot = index.getAndUpdate { (it + 1) % BreadcrumbKeys.RING_SIZE }
        val timestamp = LocalTime.now().format(timeFormatter)
        val entry = "[$timestamp][$tag] $message".take(255)
        Crashpad.setAnnotation(BreadcrumbKeys.SLOTS[slot], entry)
        Crashpad.setAnnotation(BreadcrumbKeys.INDEX, "${(slot + 1) % BreadcrumbKeys.RING_SIZE}")
    }
}
