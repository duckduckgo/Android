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
import logcat.logcat
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
            logcat { "PIR-CUSTOM-STATS: Attempt to fire weekly pixels" }
            val now = currentTimeProvider.currentTimeMillis()
            val weeklyStatLastSentMs = pirRepository.getWeeklyStatLastSentMs()
            if (weeklyStatLastSentMs == 0L || didWeekPassedBetweenDates(weeklyStatLastSentMs, now)) {
                logcat { "PIR-CUSTOM-STATS: Calculating weekly pixels" }
                attemptFireWeeklyChildBrokerOrphanedOptOutsPixel(now)
                pirRepository.setWeeklyStatLastSentMs(now)
            }
            logcat { "PIR-CUSTOM-STATS: Done attempting to fire weekly pixels" }
        }
    }

    private suspend fun attemptFireWeeklyChildBrokerOrphanedOptOutsPixel(nowMs: Long) {
        val activeBrokers = pirRepository.getAllActiveBrokerObjects()

        // Map broker names to extracted profiles created in the last week
        val groupedWeekExtractedProfiles = pirRepository.getAllExtractedProfiles().filter {
            !it.deprecated && it.lessThan7DaysSinceCreation(nowMs)
        }.groupBy { it.brokerName }

        // Take only child brokers (Brokers with parent)
        val groupedWeekChildExtractedProfiles = groupedWeekExtractedProfiles.filter {
            val broker = activeBrokers.getBrokerWithName(it.key) ?: return@filter false
            return@filter !broker.parent.isNullOrEmpty()
        }

        logcat { "PIR-CUSTOM-STATS: Child broker created past week: ${groupedWeekChildExtractedProfiles.size}" }
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

            logcat { "PIR-CUSTOM-STATS: Child broker ${broker.name} -> Parent ${parent?.name}" }
            logcat { "PIR-CUSTOM-STATS: childParentRecordDifference: $recordDiff orphanedRecordsCount: $orphanedRecordsCount" }
            if (recordDiff <= 0 && orphanedRecordsCount == 0) return@forEach

            logcat { "PIR-CUSTOM-STATS: Emitting pixel for ${broker.name}" }
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
        return !didWeekPassedBetweenDates(this.dateAddedInMillis, timeMs)
    }

    private fun didWeekPassedBetweenDates(
        startMs: Long,
        endMs: Long,
    ): Boolean {
        return endMs - startMs >= 7.days.inWholeMilliseconds
    }
}
