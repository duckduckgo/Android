/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.global.useourapp

import com.duckduckgo.app.statistics.IndexRandomizer
import com.duckduckgo.app.statistics.Probabilistic

interface MigrationManager {
    fun shouldRunMigration(): Boolean
}

class UseOurAppMigrationManager constructor(private val indexRandomizer: IndexRandomizer) : MigrationManager {

    override fun shouldRunMigration(): Boolean {
        val randomizedIndex = indexRandomizer.random(MIGRATION_VARIANTS)
        val variant = MIGRATION_VARIANTS[randomizedIndex]
        return (variant == useOurAppMigration)
    }

    companion object {
        private val defaultMigration = MigrationWeight(0.9) // do not migrate 90% of old users
        private val useOurAppMigration = MigrationWeight(0.1) // migrate 10% of old users

        val MIGRATION_VARIANTS = listOf(
            defaultMigration,
            useOurAppMigration
        )
    }
}

data class MigrationWeight(override val weight: Double) : Probabilistic
