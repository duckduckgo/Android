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

package com.duckduckgo.app.statistics.user_segments

import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.statistics.user_segments.SegmentCalculation.ActivityType
import com.duckduckgo.app.statistics.user_segments.SegmentCalculation.UserSegment
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface SegmentCalculation {

    /**
     * This method computes the user segmentation for a given [ActivityType] and u
     *
     * @param activityType the activity type (search or app_use) for the user segment calculation
     * @param atbUsageHistory the atb usage history corresponding the [activityType]
     *
     * It theoretically can throw NPE but just theoretically because of how legacy models are defined
     * @throws [NullPointerException]
     */
    suspend fun computeUserSegmentForActivityType(activityType: ActivityType, atbUsageHistory: List<String>): UserSegment

    enum class ActivityType {
        APP_USE, SEARCH
    }

    data class UserSegment(
        val activityType: String,
        val cohortAtb: String?,
        val newSetAtb: String,
        val countAsWau: Boolean,
        val countAsMau: String?,
        val segmentsToday: List<String>,
        val segmentsPrevWeek: List<String>,
        val cawAndActivePrevWeek: Boolean = false,
        val segmentsPrevMonthN: Array<String?> = Array(4) { null },
    ) {
        /**
         * Convenience method to return the user segments as a map that can directly be used in pixel params
         */
        fun toPixelParams(): Map<String, String> {
            return mutableMapOf<String, String>().apply {
                put("activity_type", activityType)
                put("new_set_atb", newSetAtb)
                if (countAsWau) {
                    put("count_as_wau", countAsWau.toString())
                }
                countAsMau?.let { put("count_as_mau_n", it) }
                if (segmentsToday.isNotEmpty()) {
                    put("segments_today", segmentsToday.joinToString(","))
                }
                if (cawAndActivePrevWeek && segmentsPrevWeek.isNotEmpty()) {
                    put("segments_prev_week", segmentsPrevWeek.joinToString(","))
                }
                segmentsPrevMonthN.forEachIndexed { n, value ->
                    if (value != null) {
                        put("segments_prev_month_$n", value)
                    }
                }
            }.toMap()
        }
    }
}

// //
// This implementation is based on https://dub.duckduckgo.com/flawrence/felix-jupyter-modules/blob/master/segments_reference.py
// but has some minor differences to make it more kotlin idiomatic
// //
@ContributesBinding(AppScope::class)
class RealSegmentCalculation @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val store: StatisticsDataStore,
    private val appBuildConfig: AppBuildConfig,
) : SegmentCalculation {

    private val cohortAtb: String by lazy {
        store.atb?.version!!
    }

    private var prevWauSegments: List<String> = emptyList()
    private var prevSetAtb: String? = null
    private var previousMAUSegments = Array(4) { "" }

    private fun resetComputationState() {
        prevSetAtb = null
        prevWauSegments = emptyList()
        previousMAUSegments = Array(4) { "" }
    }

    override suspend fun computeUserSegmentForActivityType(
        activityType: ActivityType,
        usageHistory: List<String>,
    ): UserSegment = withContext((dispatcherProvider.io())) {
        var lastResult: UserSegment? = null
        for (n in 1..usageHistory.size) {
            lastResult = computeUserSegmentInternal(usageHistory.take(n), activityType.name.lowercase())
            updateState(lastResult)
        }

        return@withContext lastResult!!.also {
            resetComputationState()
        }
    }

    private suspend fun computeUserSegmentInternal(usage: List<String>, activityType: String): UserSegment {
        val newSetAtb = usage.takeLast(1).lastOrNull()
        val countAsWau = if (prevSetAtb == null) {
            true
        } else {
            countAsWau(prevSetAtb, newSetAtb, cohortAtb)
        }
        val countAsMau = if (prevSetAtb == null) {
            "tttt"
        } else {
            countAsMau(prevSetAtb, newSetAtb, cohortAtb)
        }
        val segments = getSegments(prevSetAtb, newSetAtb, usage, cohortAtb)
        val cawAndActivePrevWeek = if (newSetAtb != null && cohortAtb != null) {
            cawAndActivePrevWeek(newSetAtb, prevSetAtb, cohortAtb)
        } else {
            false
        }
        val segmentsPrevMonth = Array<String?>(4) { null }
        if (countAsMau != "ffff") {
            for (n in 0 until 4) {
                if (camAndActivePrevMonth(n, prevSetAtb, newSetAtb!!, cohortAtb) && previousMAUSegments[n] != "") {
                    segmentsPrevMonth[n] = previousMAUSegments[n]
                }
            }
        }

        return UserSegment(
            activityType = activityType,
            cohortAtb = cohortAtb,
            newSetAtb = newSetAtb.orEmpty(),
            countAsWau = countAsWau,
            countAsMau = if (countAsMau == "ffff") null else countAsMau,
            segmentsToday = segments,
            segmentsPrevWeek = prevWauSegments,
            cawAndActivePrevWeek = cawAndActivePrevWeek,
            segmentsPrevMonthN = segmentsPrevMonth,
        )
    }

    private fun updateState(userSegment: UserSegment) {
        prevSetAtb = userSegment.newSetAtb
        if (userSegment.countAsWau) {
            // Filter out segments irrelevant to wau
            prevWauSegments = userSegment.segmentsToday.filterNot { it.contains("_mau") || it == "first_month" }
        }

        for (n in 0 until 4) {
            if (userSegment.countAsMau?.toCharArray()?.get(n) == 't') {
                previousMAUSegments[n] = userSegment.segmentsToday.filter {
                    !it.contains("_wau") && !it.contains("_week") && (it.contains("_mau_$n") || !it.contains("_mau_"))
                }.joinToString(",")
            }
        }
    }

    private fun cawAndActivePrevWeek(newSetAtb: String, prevSetAtb: String?, cohortAtb: String): Boolean {
        if (newSetAtb == cohortAtb) {
            // first install day
            return false
        }
        if (prevSetAtb == null || prevSetAtb.asNumber() == cohortAtb.asNumber()) {
            // first post install activity
            return false
        }
        if (!countAsWau(prevSetAtb, newSetAtb, cohortAtb)) {
            return false
        }

        return newSetAtb.parseAtbWeek() == prevSetAtb.parseAtbWeek() + 1
    }

    private fun getSegmentsPrevWeek(
        prevSetAtb: String?,
        newSetAtb: String?,
        cohortAtb: String?,
        userSegmentSnapshot: UserSegment,
    ): List<String> {
        if (newSetAtb == null) return emptyList()
        if (cohortAtb == null) return emptyList()

        if (cawAndActivePrevWeek(newSetAtb, prevSetAtb, cohortAtb)) {
            return userSegmentSnapshot.segmentsToday
        }

        return emptyList()
    }

    private suspend fun getSegments(prevSetAtb: String?, newSetAtb: String?, usage: List<String>, cohortAtb: String?): List<String> {
        fun relevantHistoryNums(newSetAtb: String, usage: List<String>, atbCohort: String): List<Int> {
            val today = newSetAtb.asNumber()
            val numHist = usage.map { it.asNumber() }
            return numHist
                .filter { d -> d < today && d >= today - 29 && d > atbCohort.asNumber() }
                .toSet()
                .sorted()
        }
        fun segmentRegular(newSetAtb: String, usage: List<String>, atbCohort: String): Boolean {
            return relevantHistoryNums(newSetAtb, usage, atbCohort).size >= 14
        }
        fun segmentIntermittent(newSetAtb: String, usageHistory: List<String>, atbCohort: String): Boolean {
            val today = newSetAtb.asNumber()
            val relHist = relevantHistoryNums(newSetAtb, usageHistory, atbCohort)

            if (relHist.size >= 14) {
                return false
            }

            val rollingWeeksActive = relHist.map { (today - it - 1) / 7 }.toSet()

            return rollingWeeksActive.size == 4
        }

        if (newSetAtb == null || cohortAtb == null) {
            // should not be possible
            return emptyList()
        }
        val newSetAtbWeek = newSetAtb.parseAtbWeek() ?: return emptyList()
        val cohortAtbWeek = cohortAtb.parseAtbWeek() ?: return emptyList()
        val segments = mutableListOf<String>()
        if (countAsWau(prevSetAtb, newSetAtb, cohortAtb)) {
            if (newSetAtbWeek == cohortAtbWeek) {
                segments.add("first_week")
            } else if (newSetAtbWeek == cohortAtbWeek + 1) {
                segments.add("second_week")
            } else if (cawAndActivePrevWeek(newSetAtb, prevSetAtb, cohortAtb)) {
                segments.add("current_user_wau")
            } else {
                segments.add("reactivated_wau")
            }
        }

        if ((prevSetAtb == null || prevSetAtb.asNumber() == cohortAtb.asNumber()) && newSetAtbWeek < cohortAtbWeek + 4) {
            segments.add("first_month")
        } else {
            for (n in 0 until 4) {
                if (countAsMauForN(n, prevSetAtb, newSetAtb, cohortAtb)) {
                    if (camAndActivePrevMonth(n, prevSetAtb, newSetAtb, cohortAtb)) {
                        segments.add("current_user_mau_$n")
                    } else {
                        segments.add("reactivated_mau_$n")
                    }
                }
            }
        }

        if (appBuildConfig.isAppReinstall() && newSetAtb.asNumber() <= cohortAtb.asNumber() + 28) {
            segments.add("reinstaller")
        }

        if (segmentRegular(newSetAtb, usage, cohortAtb)) {
            segments.add("regular")
        }
        if (segmentIntermittent(newSetAtb, usage, cohortAtb)) {
            segments.add("intermittent")
        }

        return segments.sorted()
    }

    private fun countAsMauForN(n: Int, prevSetAtb: String?, newSetAtb: String?, cohortAtb: String?): Boolean {
        require(n < 4)

        if (newSetAtb == null) {
            // should never happen
            return false
        }
        if (newSetAtb == cohortAtb) {
            // installation day
            return false
        }
        if (prevSetAtb == null || prevSetAtb.asNumber() == cohortAtb?.asNumber()) {
            // first post-install activity
            return true
        }

        val prevSetAtbWeek = prevSetAtb.parseAtbWeek() ?: return false // should never happen
        val newSetAtbWeek = newSetAtb.parseAtbWeek() ?: return false

        return (newSetAtbWeek - n) / 4 > (prevSetAtbWeek - n) / 4
    }

    private fun countAsMau(prevSetAtb: String?, newSetAtb: String?, cohortAtb: String?): String {
        val result = StringBuffer()
        for (n in 0..3) {
            if (countAsMauForN(n, prevSetAtb, newSetAtb, cohortAtb)) {
                result.append("t")
            } else {
                result.append("f")
            }
        }
        return result.toString()
    }

    private fun countAsWau(prevSetAtb: String?, newSetAtb: String?, cohortAtb: String): Boolean {
        if (newSetAtb == null) {
            // should never happen
            return false
        }
        if (newSetAtb.asNumber() == cohortAtb.asNumber()) {
            // install day
            return false
        }
        if (prevSetAtb == null || prevSetAtb.asNumber() == cohortAtb.asNumber()) {
            // first post-install activity
            return true
        }

        val prevSetAtbWeek = prevSetAtb.parseAtbWeek() ?: return false // should never happen
        val newSetAtbWeek = newSetAtb.parseAtbWeek() ?: return false

        return newSetAtbWeek > prevSetAtbWeek
    }

    private fun camAndActivePrevMonth(n: Int, prevSetAtb: String?, newSetAtb: String, atbCohort: String): Boolean {
        require(n < 4) // This replaces the assert statement in Python

        if (newSetAtb == atbCohort) {
            // Install day - this code should not be running! Report nothing.
            return false
        }

        if (prevSetAtb == null || prevSetAtb.asNumber() == atbCohort.asNumber()) {
            // First post-install activity
            return false
        }

        if (!countAsMauForN(n, prevSetAtb, newSetAtb, atbCohort)) {
            return false
        }

        return (newSetAtb.parseAtbWeek()!! - n) / 4 == (prevSetAtb.parseAtbWeek()!! - n) / 4 + 1
    }
}
