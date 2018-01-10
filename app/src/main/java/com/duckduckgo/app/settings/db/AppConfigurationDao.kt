/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.settings.db

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import android.arch.persistence.room.Query
import android.support.annotation.CheckResult
import com.duckduckgo.app.settings.db.AppConfigurationDao.Companion.KEY
import org.intellij.lang.annotations.Language


@Dao
interface AppConfigurationDao {

    @Language("RoomSql")
    @Query("UPDATE app_configuration SET appConfigurationDownloaded=1")
    fun configurationDownloadSuccessful()

    @Language("RoomSql")
    @CheckResult
    @Query("SELECT * FROM app_configuration WHERE key='$KEY'")
    fun appConfigurationStatus(): LiveData<AppConfigurationEntity>

    companion object {
        const val KEY = "SINGLETON_KEY"
    }

}

@Entity(tableName = "app_configuration")
data class AppConfigurationEntity(

        @PrimaryKey val key: String = KEY,
        val appConfigurationDownloaded: Boolean

)