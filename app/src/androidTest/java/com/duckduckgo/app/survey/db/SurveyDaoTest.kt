/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.survey.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.model.Survey.Status.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SurveyDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SurveyDao

    @Before
    fun before() {
        db =
            Room.inMemoryDatabaseBuilder(
                    getInstrumentation().targetContext, AppDatabase::class.java)
                .build()
        dao = db.surveyDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenSurveyNotInsertedThenItDoesNotExist() {
        assertFalse(dao.exists("1"))
    }

    @Test
    fun whenSurveyInsertedThenItExists() {
        val survey = Survey("1", "", null, SCHEDULED)
        dao.insert(survey)
        assertTrue(dao.exists("1"))
        assertEquals(survey, dao.get("1"))
    }

    @Test
    fun whenSurveyUpdatedThenRecordIsUpdated() {
        val original = Survey("1", "https://abc.com", null, SCHEDULED)
        val updated = Survey("1", "https://xyz.com", null, CANCELLED)

        dao.insert(original)
        dao.update(updated)

        assertNotSame(original, dao.get("1"))
        assertEquals(updated, dao.get("1"))
    }

    @Test
    fun whenNoSurveysThenGetScheduledIsEmpty() {
        assertEquals(0, dao.getScheduled().size)
    }

    @Test
    fun whenScheduledSurveysExistThenGetScheduledContainsThem() {
        dao.insert(Survey("1", "", null, SCHEDULED))
        dao.insert(Survey("2", "", null, SCHEDULED))
        dao.insert(Survey("3", "", null, DONE))
        dao.insert(Survey("4", "", null, CANCELLED))
        assertEquals(2, dao.getScheduled().size)
    }

    @Test
    fun whenScheduledSurveysAreCancelledTheirStatusIsUpdatedAndGetScheduledIsEmpty() {
        dao.insert(Survey("1", "", null, SCHEDULED))
        dao.insert(Survey("2", "", null, SCHEDULED))
        dao.cancelScheduledSurveys()
        assertEquals(CANCELLED, dao.get("1")?.status)
        assertEquals(CANCELLED, dao.get("2")?.status)
        assertTrue(dao.getScheduled().isEmpty())
    }

    @Test
    fun whenUnusedSurveysAreDeletedThenTheyNoLongerExistAndGetScheduledIsEmpty() {
        dao.insert(Survey("1", "", null, SCHEDULED))
        dao.insert(Survey("2", "", null, NOT_ALLOCATED))
        dao.deleteUnusedSurveys()
        assertFalse(dao.exists("1"))
        assertFalse(dao.exists("2"))
        assertTrue(dao.getScheduled().isEmpty())
    }
}
