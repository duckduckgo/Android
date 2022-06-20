/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.config.store.features.trackingparameters

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.duckduckgo.privacy.config.store.TrackingParameterEntity
import com.duckduckgo.privacy.config.store.TrackingParameterExceptionEntity

@Dao
abstract class TrackingParametersDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAllExceptions(domains: List<TrackingParameterExceptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAllParameters(parameters: List<TrackingParameterEntity>)

    @Transaction
    open fun updateAll(domains: List<TrackingParameterExceptionEntity>, parameters: List<TrackingParameterEntity>) {
        deleteAllExceptions()
        insertAllExceptions(domains)

        deleteAllTrackingParameters()
        insertAllParameters(parameters)
    }

    @Query("select * from tracking_parameter_exceptions")
    abstract fun getAllExceptions(): List<TrackingParameterExceptionEntity>

    @Query("select * from tracking_parameters")
    abstract fun getAllTrackingParameters(): List<TrackingParameterEntity>

    @Query("delete from tracking_parameter_exceptions")
    abstract fun deleteAllExceptions()

    @Query("delete from tracking_parameters")
    abstract fun deleteAllTrackingParameters()
}
