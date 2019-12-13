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

package com.duckduckgo.app.trackerdetection.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tds_entity")
data class TdsEntity(
    @PrimaryKey override val name: String,
    override val displayName: String,
    override val prevalence: Double
) : com.duckduckgo.app.trackerdetection.model.Entity {

    /**
     * Override required as hashcode implementation for Double (and other numbers) changed in kotlin and is
     * not supported in API <24. Can delete this and equals once we drop support for APIs <24
     */
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + displayName.hashCode()

        // We call prevalence.toString().hashCode() rather than prevalence.hashCode()
        // as the latter changed implementation and does not work on APIs <24
        // https://stackoverflow.com/questions/45935788/nosuchmethoderror-java-lang-long-hashcode
        result = 31 * result + prevalence.toString().hashCode()

        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TdsEntity

        if (name != other.name) return false
        if (displayName != other.displayName) return false
        if (prevalence != other.prevalence) return false

        return true
    }
}


