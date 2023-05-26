/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.featureusage

import com.duckduckgo.app.featureusage.FeatureSegmentType.APP_TP_ENABLED
import com.duckduckgo.app.featureusage.FeatureSegmentType.BOOKMARKS_IMPORTED
import com.duckduckgo.app.featureusage.FeatureSegmentType.EMAIL_PROTECTION_SET
import com.duckduckgo.app.featureusage.FeatureSegmentType.FAVOURITE_SET
import com.duckduckgo.app.featureusage.FeatureSegmentType.FIRE_BUTTON_USED
import com.duckduckgo.app.featureusage.FeatureSegmentType.FIVE_SEARCHES_MADE
import com.duckduckgo.app.featureusage.FeatureSegmentType.LOGIN_SAVED
import com.duckduckgo.app.featureusage.FeatureSegmentType.SET_AS_DEFAULT
import com.duckduckgo.app.featureusage.FeatureSegmentType.TEN_SEARCHES_MADE
import com.duckduckgo.app.featureusage.FeatureSegmentType.TWO_SEARCHES_MADE
import com.duckduckgo.app.featureusage.db.FeatureSegmentsDataStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.UserBrowserProperties
import javax.inject.Inject

interface FeatureSegmentsManager {
    fun addUserToFeatureSegment(segment: FeatureSegmentType)
    fun searchMade()
    fun shouldFireSegmentsPixel(): Boolean
    fun fireFeatureSegmentsPixel()
    fun restartDailySearchCount()
}

class FeatureSegmentManagerImpl @Inject constructor(
    private val featureSegmentsDataStore: FeatureSegmentsDataStore,
    private val pixel: Pixel,
    private val userBrowserProperties: UserBrowserProperties,
) : FeatureSegmentsManager {

    override fun addUserToFeatureSegment(segment: FeatureSegmentType) {
        if (isFeatureSegmentsAllowed()) {
            when (segment) {
                BOOKMARKS_IMPORTED -> featureSegmentsDataStore.bookmarksImported = true
                FAVOURITE_SET -> featureSegmentsDataStore.favouriteSet = true
                SET_AS_DEFAULT -> featureSegmentsDataStore.setAsDefault = true
                LOGIN_SAVED -> featureSegmentsDataStore.loginSaved = true
                FIRE_BUTTON_USED -> featureSegmentsDataStore.fireButtonUsed = true
                APP_TP_ENABLED -> featureSegmentsDataStore.appTpEnabled = true
                EMAIL_PROTECTION_SET -> featureSegmentsDataStore.emailProtectionSet = true
                TWO_SEARCHES_MADE -> featureSegmentsDataStore.twoSearchesMade = true
                FIVE_SEARCHES_MADE -> featureSegmentsDataStore.fiveSearchesMade = true
                TEN_SEARCHES_MADE -> featureSegmentsDataStore.tenSearchesMade = true
            }
            featureSegmentsDataStore.userAddedToSegmentsEvents = true
        }
    }

    override fun searchMade() {
        if (isFeatureSegmentsAllowed()) {
            val updatedSearchesMade = featureSegmentsDataStore.dailySearchesCount++
            when (updatedSearchesMade) {
                2 -> addUserToFeatureSegment(TWO_SEARCHES_MADE)
                5 -> addUserToFeatureSegment(FIVE_SEARCHES_MADE)
                10 -> addUserToFeatureSegment(TEN_SEARCHES_MADE)
            }
            featureSegmentsDataStore.dailySearchesCount = updatedSearchesMade
        }
    }

    override fun shouldFireSegmentsPixel(): Boolean {
        val listOfSegments = getUserFeatureSegments()
        return listOfSegments.any { it.value } && isFeatureSegmentsAllowed()
    }

    override fun fireFeatureSegmentsPixel() {
        val params = getUserFeatureSegments().map { it.key to it.value.toString() }.toMap()
        pixel.fire(AppPixelName.DAILY_USER_EVENT_SEGMENT, params)
    }

    override fun restartDailySearchCount() {
        featureSegmentsDataStore.dailySearchesCount = 0
    }

    private fun isFeatureSegmentsAllowed(): Boolean {
        return userBrowserProperties.daysSinceInstalled() < 8 || featureSegmentsDataStore.userAddedToSegmentsEvents
    }

    private fun getUserFeatureSegments(): Map<String, Boolean> {
        return mapOf(
            BOOKMARKS_IMPORTED.name.lowercase() to featureSegmentsDataStore.bookmarksImported,
            FAVOURITE_SET.name.lowercase() to featureSegmentsDataStore.favouriteSet,
            SET_AS_DEFAULT.name.lowercase() to featureSegmentsDataStore.setAsDefault,
            LOGIN_SAVED.name.lowercase() to featureSegmentsDataStore.loginSaved,
            FIRE_BUTTON_USED.name.lowercase() to featureSegmentsDataStore.fireButtonUsed,
            APP_TP_ENABLED.name.lowercase() to featureSegmentsDataStore.appTpEnabled,
            EMAIL_PROTECTION_SET.name.lowercase() to featureSegmentsDataStore.emailProtectionSet,
            TWO_SEARCHES_MADE.name.lowercase() to featureSegmentsDataStore.twoSearchesMade,
            FIVE_SEARCHES_MADE.name.lowercase() to featureSegmentsDataStore.fiveSearchesMade,
            TEN_SEARCHES_MADE.name.lowercase() to featureSegmentsDataStore.tenSearchesMade,
        )
    }
}
