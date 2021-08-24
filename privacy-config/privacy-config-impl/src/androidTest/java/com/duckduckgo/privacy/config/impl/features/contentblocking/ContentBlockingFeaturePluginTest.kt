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

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.duckduckgo.privacy.config.impl.FileUtilities.loadText
import com.duckduckgo.privacy.config.store.ContentBlockingDao
import com.duckduckgo.privacy.config.store.ContentBlockingExceptionEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesDao
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ContentBlockingFeaturePluginTest {

    lateinit var testee: ContentBlockingPlugin

    private lateinit var db: PrivacyConfigDatabase
    private lateinit var privacyFeatureTogglesDao: PrivacyFeatureTogglesDao
    private lateinit var contentBlockingDao: ContentBlockingDao

    @Before
    fun before() {
        prepareDb()
        testee = ContentBlockingPlugin(db)
    }

    private fun prepareDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PrivacyConfigDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        privacyFeatureTogglesDao = db.privacyFeatureTogglesDao()
        contentBlockingDao = db.contentBlockingDao()
    }

    @Test
    fun whenFeatureNameDoesNotMatchContentBlockingThenReturnFalse() {
        assertFalse(testee.store("test", null))
    }

    @Test
    fun whenFeatureNameMatchesContentBlockingThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME, null))
    }

    @Test
    fun whenFeatureNameMatchesContentBlockingAndIsEnabledThenStoreFeatureEnabled() {
        val jsonObject = getJsonObjectFromFile("json/content_blocking.json")

        testee.store(FEATURE_NAME, jsonObject)

        assertTrue(privacyFeatureTogglesDao.get(FEATURE_NAME)!!.enabled)
    }

    @Test
    fun whenFeatureNameMatchesContentBlockingAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonObject = getJsonObjectFromFile("json/content_blocking_disabled.json")

        testee.store(FEATURE_NAME, jsonObject)

        assertFalse(privacyFeatureTogglesDao.get(FEATURE_NAME)!!.enabled)
    }

    @Test
    fun whenFeatureNameMatchesContentBlockingThenDeleteAllExistingExceptions() {
        contentBlockingDao.insertAll(listOf(ContentBlockingExceptionEntity("test", "test")))
        val jsonObject = getJsonObjectFromFile("json/content_blocking.json")

        testee.store(FEATURE_NAME, jsonObject)

        assertNull(contentBlockingDao.get("test"))
    }

    @Test
    fun whenFeatureNameMatchesContentBlockingThenAddAllTheExceptionsWithTheirReasons() {
        contentBlockingDao.insertAll(listOf(ContentBlockingExceptionEntity("test", "test")))
        val jsonObject = getJsonObjectFromFile("json/content_blocking.json")

        testee.store(FEATURE_NAME, jsonObject)

        assertEquals(2, contentBlockingDao.getAll().size)
        assertEquals("Adblocker wall", contentBlockingDao.get("www.livenewsnow.com").reason)
        assertEquals("Broken videos", contentBlockingDao.get("weather.com").reason)
    }

    private fun getJsonObjectFromFile(filename: String): JSONObject {
        val json = loadText(filename)
        return JSONObject(json)
    }

    companion object {
        private const val FEATURE_NAME = "contentBlocking"
    }
}
