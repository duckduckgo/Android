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

package com.duckduckgo.savedsites.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface RelationsReconciler {
    fun reconcileRelations(
        originalRelations: List<String>,
        newFolderRelations: List<String>,
    ): List<String>
}

@ContributesBinding(AppScope::class)
class MissingEntitiesRelationReconciler @Inject constructor(
    val savedSitesEntitiesDao: SavedSitesEntitiesDao,
) : RelationsReconciler {
    override fun reconcileRelations(
        originalRelations: List<String>,
        newFolderRelations: List<String>,
    ): List<String> {
        val missingEntities = (originalRelations - newFolderRelations.toSet()).filter {
            savedSitesEntitiesDao.entityById(it) == null
        }.toMutableList()
        if (missingEntities.isEmpty()) return newFolderRelations

        val result = mutableListOf<String>()
        val originalMap: Map<String, Int> = originalRelations.withIndex().associateBy({ it.value }, { it.index })
        // if missing entities at the top of the list
        // find the firstNotMissingItem, add anything before it
        originalRelations
            .find { !missingEntities.contains(it) }
            ?.let { firstNotMissingItem -> originalMap[firstNotMissingItem] }
            ?.let { firstNotMissingItemPosition ->
                result.addAll(missingEntities.take(firstNotMissingItemPosition))
                missingEntities.removeAll(result)
            }

        val missingEntitiesMap = missingEntities.associateBy({ originalRelations.indexOf(it) }, { it }).toMutableMap()
        val missingEntitiesPositions = missingEntities.map { originalRelations.indexOf(it) }

        var index = -1
        for (entityId in newFolderRelations) {
            val originalIndex = originalMap[entityId]
            if (originalIndex != null) {
                if (index == -1) { // first item we add it directly
                    result.add(entityId)
                } else {
                    if (originalIndex > index) {
                        result.addAll(findMissingEntitiesBetween(index, originalIndex, missingEntitiesMap, missingEntitiesPositions))
                    } else if (originalIndex < index) {
                        result.addAll(findMissingEntitiesBetween(index, originalRelations.size, missingEntitiesMap, missingEntitiesPositions))
                        result.addAll(findMissingEntitiesBetween(0, originalIndex, missingEntitiesMap, missingEntitiesPositions))
                    }
                    result.add(entityId)
                }
                index = originalIndex
            } else {
                result.add(entityId) // add if not present in originalMap
            }
        }

        if (missingEntitiesMap.isNotEmpty()) {
            result.addAll(missingEntitiesMap.values.toList())
        }
        return result.distinct()
    }

    private fun findMissingEntitiesBetween(
        from: Int,
        to: Int,
        missingEntitiesMap: MutableMap<Int, String>,
        missingEntitiesPositions: List<Int>,
    ): List<String> {
        val result = mutableListOf<String>()
        missingEntitiesPositions.forEach { position ->
            if (position in from..to) {
                missingEntitiesMap[position]?.let { id ->
                    result.add(id)
                }
                missingEntitiesMap.remove(position)
            }
        }
        return result
    }
}
