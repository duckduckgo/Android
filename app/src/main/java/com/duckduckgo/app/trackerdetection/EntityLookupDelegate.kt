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

package com.duckduckgo.app.trackerdetection

import android.net.Uri
import com.duckduckgo.app.pixels.remoteconfig.CachedEntityLookupRCWrapper
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import javax.inject.Provider

@ContributesBinding(AppScope::class, boundType = EntityLookup::class)
@ContributesBinding(AppScope::class, boundType = EntityLookupRefresher::class)
@SingleInstanceIn(AppScope::class)
class EntityLookupDelegate @Inject constructor(
    private val legacy: Provider<TdsEntityLookup>,
    private val cached: Provider<CachedTdsEntityLookup>,
    private val flag: CachedEntityLookupRCWrapper,
) : EntityLookup, EntityLookupRefresher {

    private val active: EntityLookupWithRefresh by lazy {
        if (flag.enabled) cached.get() else legacy.get()
    }

    override fun entityForUrl(url: String): Entity? = active.entityForUrl(url)

    override fun entityForUrl(url: Uri): Entity? = active.entityForUrl(url)

    override fun entityForName(name: String): Entity? = active.entityForName(name)

    override fun refresh() = active.refresh()
}
