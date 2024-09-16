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

import androidx.lifecycle.LiveData
import androidx.room.*
import com.duckduckgo.app.survey.model.Survey

@Dao
interface SurveyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(survey: Survey)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(survey: Survey)

    @Query("select * from survey where surveyId = :surveyId")
    fun get(surveyId: String): Survey?

    @Query("""select * from survey where status = "SCHEDULED" limit 1""")
    fun getLiveScheduled(): LiveData<Survey>

    @Query("""select * from survey where status = "SCHEDULED"""")
    fun getScheduled(): List<Survey>

    @Query("""delete from survey where status = "SCHEDULED" or status = "NOT_ALLOCATED"""")
    fun deleteUnusedSurveys()

    @Transaction
    fun cancelScheduledSurveys() {
        getScheduled().forEach {
            it.status = Survey.Status.CANCELLED
            update(it)
        }
    }
}
