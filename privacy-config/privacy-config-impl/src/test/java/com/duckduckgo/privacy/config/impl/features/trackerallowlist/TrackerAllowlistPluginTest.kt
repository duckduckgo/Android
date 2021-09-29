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

package com.duckduckgo.privacy.config.impl.features.trackerallowlist

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.privacy.config.impl.FileUtilities
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.TrackerAllowlistEntity
import com.duckduckgo.privacy.config.store.features.trackerallowlist.TrackerAllowlistRepository
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerAllowlistPluginTest {
    lateinit var testee: TrackerAllowlistPlugin

    private val mockFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()
    private val mockAllowlistRepository: TrackerAllowlistRepository = mock()

    @Before
    fun before() {
        testee = TrackerAllowlistPlugin(mockAllowlistRepository, mockFeatureTogglesRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchTrackerAllowlistThenReturnFalse() {
        assertFalse(testee.store("test", null))
    }

    @Test
    fun whenFeatureNameMatchesTrackerAllowlistThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME, null))
    }

    @Test
    fun whenFeatureNameMatchesTrackerAllowlistAndIsEnabledThenStoreFeatureEnabled() {
        val jsonObject = FileUtilities.getJsonObjectFromFile("json/tracker_allowlist.json")

        testee.store(FEATURE_NAME, jsonObject)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, true))
    }

    @Test
    fun whenFeatureNameMatchesTrackerAllowlistAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonObject = FileUtilities.getJsonObjectFromFile("json/tracker_allowlist_disabled.json")

        testee.store(FEATURE_NAME, jsonObject)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, false))
    }

    @Test
    fun whenFeatureNameMatchesTrackerAllowlistThenUpdateAllExistingExceptions() {
        val jsonObject = FileUtilities.getJsonObjectFromFile("json/tracker_allowlist.json")

        testee.store(FEATURE_NAME, jsonObject)

        argumentCaptor<List<TrackerAllowlistEntity>>().apply {
            verify(mockAllowlistRepository).updateAll(capture())
            val trackerAllowlistEntity = this.firstValue.first()
            val rules = trackerAllowlistEntity.rules
            assertEquals(1, this.allValues.size)
            assertEquals("allowlist-tracker-1.com", trackerAllowlistEntity.domain)
            assertEquals("allowlist-tracker-1.com/videos.js", rules.first().rule)
            assertEquals("testsite.com", rules.first().domains.first())
            assertEquals("match single resource on single site", rules.first().reason)
        }
    }

    companion object {
        private const val FEATURE_NAME = "trackerAllowlist"
    }
}
