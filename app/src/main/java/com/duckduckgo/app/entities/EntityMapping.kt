/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.entities

import com.duckduckgo.app.entities.db.EntityListEntity
import com.duckduckgo.app.global.UriString
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntityMapping @Inject constructor() {

    var entities: List<EntityListEntity> = emptyList()

    fun updateEntities(entities: List<EntityListEntity>) {
        Timber.d("updateEntities")
        this.entities = entities
    }

    fun entityForUrl(url: String): EntityListEntity? {
        return entities.find { UriString.sameOrSubdomain(url, it.domainName) }
    }

}