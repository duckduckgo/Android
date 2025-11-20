/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.pixels

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.matches
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

interface WeeklyPixelReporter {
    suspend fun attemptFirePixel()
}

@ContributesBinding(AppScope::class)
class RealWeeklyPixelReporter @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val pirRepository: PirRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pixelSender: PirPixelSender,
) : WeeklyPixelReporter {
    override suspend fun attemptFirePixel() {
        withContext(dispatcherProvider.io()) {
            attemptFireWeeklyChildBrokerOrphanedOptOutsPixel()
        }
    }

    private suspend fun attemptFireWeeklyChildBrokerOrphanedOptOutsPixel() {
        val now = currentTimeProvider.currentTimeMillis()
        val activeBrokers = pirRepository.getAllActiveBrokerObjects()
        val groupedWeekExtractedProfiles = pirRepository.getAllExtractedProfiles().filter {
            !it.deprecated && it.lessThan7DaysSinceCreation(now)
        }.groupBy { it.brokerName }

        val groupedWeekChildExtractedProfiles = groupedWeekExtractedProfiles.filter {
            val broker = activeBrokers.getBrokerWithName(it.key) ?: return@filter false
            return@filter !broker.parent.isNullOrEmpty()
        }

        groupedWeekChildExtractedProfiles.forEach {
            val broker = activeBrokers.getBrokerWithName(it.key)!!
            val parent = activeBrokers.getBrokerWithUrl(broker.parent!!)

            val weekParentExtractedProfiles = groupedWeekExtractedProfiles[parent?.name] ?: emptyList()
            val recordDiff = (it.value.size - weekParentExtractedProfiles.size)

            val orphanedRecordsCount = if (weekParentExtractedProfiles.isEmpty()) {
                // No parent extracted profiles mean, all are orphaned
                it.value.size
            } else {
                // IF there are, we find the child extracted profiles that do not have a match on parent
                it.value.filter { profile ->
                    weekParentExtractedProfiles.none { parentExtractedProfile ->
                        profile.matches(parentExtractedProfile)
                    }
                }.size
            }

            if (recordDiff <= 0 && orphanedRecordsCount == 0) return@forEach

            pixelSender.reportWeeklyChildOrphanedOptOuts(
                brokerUrl = broker.url,
                childParentRecordDifference = recordDiff,
                orphanedRecordsCount = orphanedRecordsCount,
            )
        }
    }

    private fun List<Broker>.getBrokerWithUrl(url: String): Broker? {
        return this.find { it.url == url }
    }

    private fun List<Broker>.getBrokerWithName(name: String): Broker? {
        return this.find { it.name == name }
    }

    private fun ExtractedProfile.lessThan7DaysSinceCreation(timeMs: Long): Boolean {
        return (timeMs - this.dateAddedInMillis) < 7.days.inWholeMilliseconds
    }
}
