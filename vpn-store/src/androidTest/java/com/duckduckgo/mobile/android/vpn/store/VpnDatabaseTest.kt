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

package com.duckduckgo.mobile.android.vpn.store

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class VpnDatabaseTest {
    @get:Rule @Suppress("unused") var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val testHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            VpnDatabase::class.qualifiedName,
            FrameworkSQLiteOpenHelperFactory())

    @Test
    fun whenTestingAllMigrationsThenSucceeds() {
        createDatabase(18)

        Room.databaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                VpnDatabase::class.java,
                TEST_DB_NAME)
            .addMigrations(*VpnDatabase.ALL_MIGRATIONS.toTypedArray())
            .build()
            .apply { openHelper.writableDatabase.close() }
    }

    private fun createDatabase(version: Int) {
        testHelper.createDatabase(TEST_DB_NAME, version).apply { close() }
    }

    companion object {
        private const val TEST_DB_NAME = "TEST_DB_NAME"
    }
}
