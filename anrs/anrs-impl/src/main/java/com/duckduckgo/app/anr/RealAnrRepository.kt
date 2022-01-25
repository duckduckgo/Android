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

package com.duckduckgo.app.anr

import com.duckduckgo.anrs.api.Anr
import com.duckduckgo.anrs.api.AnrRepository
import com.duckduckgo.app.anrs.store.AnrEntity
import com.duckduckgo.app.anrs.store.AnrsDatabase
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealAnrRepository @Inject constructor(private val anrDatabase: AnrsDatabase) : AnrRepository {
    override fun getAllAnrs(): List<Anr> {
        return anrDatabase.arnDao().getAnrs().asAnrs()
    }

    override fun peekMostRecentAnr(): Anr? {
        return anrDatabase.arnDao().latestAnr()?.asAnr()
    }

    override fun removeMostRecentAnr(): Anr? {
        return anrDatabase.arnDao().latestAnr()?.let { entity ->
            anrDatabase.arnDao().deleteAnr(entity.hash)
            entity
        }?.asAnr()
    }
}

private fun AnrEntity.asAnr(): Anr {
    return Anr(
        message = message,
        name = name,
        file = file,
        lineNumber = lineNumber,
        stackTrace = stackTrace,
        timestamp = timestamp
    )
}

private fun List<AnrEntity>.asAnrs(): List<Anr> {
    val anrs = mutableListOf<Anr>()
    forEach { anrEntity ->
        anrs.add(anrEntity.asAnr())
    }

    return anrs
}
