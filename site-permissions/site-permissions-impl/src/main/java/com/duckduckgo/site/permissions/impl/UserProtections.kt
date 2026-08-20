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

package com.duckduckgo.site.permissions.impl

import android.net.Uri
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.common.utils.extensions.toTldPlusOneOrSelf

/**
 * Whether the user has turned protections off for this URI.
 *
 * Protections are stored for the host of the page the user was looking at, while a DRM request usually
 * arrives from a subresource origin, so the registrable domain is checked too. Shared by the block list
 * and the policy so both answer the question the same way.
 */
internal fun UserAllowListRepository.isSiteUnprotectedByUser(uri: Uri): Boolean {
    if (isUriInUserAllowList(uri)) return true
    val registrableDomain = uri.host?.toTldPlusOneOrSelf() ?: return false
    return listOf(registrableDomain, "www.$registrableDomain").any { isDomainInUserAllowList(it) }
}
