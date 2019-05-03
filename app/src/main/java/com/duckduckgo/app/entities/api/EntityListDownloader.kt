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

package com.duckduckgo.app.entities.api

import com.duckduckgo.app.entities.db.EntityListDao
import com.duckduckgo.app.entities.db.EntityListEntity
import com.duckduckgo.app.global.api.isCached
import com.duckduckgo.app.global.db.AppDatabase
import io.reactivex.Completable
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

typealias EntityListJson = Map<String, NetworkEntityJson>

class NetworkEntityJson(val properties: Array<String>, val resources: Array<String>)

class EntityListDownloader @Inject constructor(
    private val entityListService: EntityListService,
    private val entityListDao: EntityListDao,
    private val appDatabase: AppDatabase
) {

    fun download(): Completable {
        return Completable.fromAction {

            val call = entityListService.fetchEntityList()
            val response = call.execute()

            if (response.isCached && entityListDao.count() != 0) {
                Timber.d("Entity list data already cached and stored")
                return@fromAction
            }

            if (response.isSuccessful) {
                Timber.d("Updating entity list data from server")
                val body = response.body()!!

                val entities = mutableListOf<EntityListEntity>()
                body.entries.forEach {
                    val entity = it.key

                    (it.value.properties + it.value.resources).forEach {
                        entities.add(EntityListEntity(entityName = entity, domainName = it))
                    }
                }

                appDatabase.runInTransaction {
                    Timber.d("Updating entity list with ${entities.size} entries")
                    entityListDao.updateAll(entities)
                }

            } else {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }

        }
    }

}