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

package com.duckduckgo.privacy.config.impl.features.contentblocking

import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.ContentBlockingException
import com.duckduckgo.privacy.config.store.ContentBlockingDao
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.toContentBlockingException
import com.squareup.anvil.annotations.ContributesBinding
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@ContributesBinding(AppObjectGraph::class)
@Singleton
class RealContentBlocking @Inject constructor(val database: PrivacyConfigDatabase) : ContentBlocking {

    private val contentBlockingDao: ContentBlockingDao = database.contentBlockingDao()

    private val exceptions = CopyOnWriteArrayList<ContentBlockingException>()

    override fun load() {
        exceptions.clear()
        contentBlockingDao.getAll().map {
            exceptions.add(it.toContentBlockingException())
        }
    }

    override fun isAnException(documentUrl: String): Boolean {
        return matches(documentUrl)
    }

    private fun matches(documentUrl: String): Boolean {
        return exceptions.any { sameOrSubdomain(documentUrl, it.domain) }
    }

}
