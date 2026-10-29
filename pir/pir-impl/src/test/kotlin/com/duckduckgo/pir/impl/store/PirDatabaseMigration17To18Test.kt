/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pir.impl.store

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PirDatabaseMigration17To18Test {

    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PirDatabase::class.java,
        emptyList(),
    )

    @Test
    fun whenMigratingFrom17To18ThenRequestedOptOutGetsFirstRequestedDateFromItsRequestedDate() {
        testHelper.createDatabase(TEST_DB_NAME, 17).use { db ->
            insertOptOutJobRecord(db, extractedProfileId = 1, status = "REQUESTED", optOutRequestedDate = 2000)
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 18, true).use { db ->
            db.query("SELECT optOutFirstRequestedDate, optOutRequestedDate FROM pir_optout_job_record WHERE extractedProfileId = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2000L, cursor.getLong(0))
                assertEquals(2000L, cursor.getLong(1))
            }
        }
    }

    @Test
    fun whenMigratingFrom17To18ThenNeverRequestedOptOutHasNoFirstRequestedDate() {
        testHelper.createDatabase(TEST_DB_NAME, 17).use { db ->
            insertOptOutJobRecord(db, extractedProfileId = 1, status = "REMOVED", optOutRequestedDate = 0)
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 18, true).use { db ->
            db.query("SELECT optOutFirstRequestedDate FROM pir_optout_job_record WHERE extractedProfileId = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
        }
    }

    private fun insertOptOutJobRecord(
        db: SupportSQLiteDatabase,
        extractedProfileId: Long,
        status: String,
        optOutRequestedDate: Long,
    ) {
        db.execSQL(
            """
            INSERT INTO pir_optout_job_record
                (extractedProfileId, brokerName, userProfileId, status, attemptCount, lastOptOutAttemptDate,
                 optOutRequestedDate, optOutFormSubmittedDate, optOutRemovedDate, deprecated, dateCreatedInMillis,
                 reporting_sevenDayConfirmationReportSentDateMs, reporting_fourteenDayConfirmationReportSentDateMs,
                 reporting_twentyOneDayConfirmationReportSentDateMs, reporting_fortyTwoDayConfirmationReportSentDateMs)
            VALUES
                ($extractedProfileId, 'broker.example', 1, '$status', 1, 1000,
                 $optOutRequestedDate, NULL, 0, 0, 100,
                 0, 0, 0, 0)
            """.trimIndent(),
        )
    }

    private companion object {
        const val TEST_DB_NAME = "pir_migration_test"
    }
}
