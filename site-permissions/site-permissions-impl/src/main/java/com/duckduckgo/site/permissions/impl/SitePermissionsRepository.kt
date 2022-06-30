/*
 * Copyright (c) 2022 DuckDuckGo
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

import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SitePermissionsRepository {
    fun isDomainAllowedToAsk(url: String, permission: String): Boolean
}

@ContributesBinding(ActivityScope::class)
class SitePermissionsRepositoryImpl @Inject constructor() : SitePermissionsRepository {

    override fun isDomainAllowedToAsk(url: String, permission: String): Boolean {
        // TODO check if url is in db with any resource set to "Always Deny"
        return true
    }
}
