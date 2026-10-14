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

package com.duckduckgo.site.permissions.impl.drm

import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class DrmSessionStore @Inject constructor() {
    private val sessions = ConcurrentHashMap<String, Boolean>()
    private val autoGrantsReported = ConcurrentHashMap<String, Boolean>()

    fun get(tabId: String, domain: String): Boolean? = sessions[key(tabId, domain)]

    fun save(tabId: String, domain: String, allowed: Boolean) {
        sessions[key(tabId, domain)] = allowed
    }

    /**
     * True the first time this tab and domain are auto-granted. A page can issue several DRM requests, and
     * an automatic grant records no session choice to short-circuit them, so the caller has to count once.
     */
    fun markAutoGrantReported(tabId: String, domain: String): Boolean = autoGrantsReported.putIfAbsent(key(tabId, domain), true) == null

    fun clear() {
        sessions.clear()
        autoGrantsReported.clear()
    }

    private fun key(tabId: String, domain: String) = "$tabId/$domain"
}
