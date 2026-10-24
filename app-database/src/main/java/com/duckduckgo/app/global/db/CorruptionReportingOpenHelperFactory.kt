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

package com.duckduckgo.app.global.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * Runs [onCorruption] whenever SQLite reports the database as corrupt, before the default handling in
 * [SupportSQLiteOpenHelper.Callback.onCorruption] deletes the database file. Room doesn't override that
 * default, so without this the database is silently recreated empty.
 */
class CorruptionReportingOpenHelperFactory(
    private val onCorruption: (SupportSQLiteDatabase) -> Unit,
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        return delegate.create(
            SupportSQLiteOpenHelper.Configuration(
                context = configuration.context,
                name = configuration.name,
                callback = CorruptionReportingCallback(configuration.callback, onCorruption),
                useNoBackupDirectory = configuration.useNoBackupDirectory,
                allowDataLossOnRecovery = configuration.allowDataLossOnRecovery,
            ),
        )
    }
}

private class CorruptionReportingCallback(
    private val delegate: SupportSQLiteOpenHelper.Callback,
    private val onCorruption: (SupportSQLiteDatabase) -> Unit,
) : SupportSQLiteOpenHelper.Callback(delegate.version) {

    override fun onConfigure(db: SupportSQLiteDatabase) = delegate.onConfigure(db)

    override fun onCreate(db: SupportSQLiteDatabase) = delegate.onCreate(db)

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = delegate.onUpgrade(db, oldVersion, newVersion)

    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = delegate.onDowngrade(db, oldVersion, newVersion)

    override fun onOpen(db: SupportSQLiteDatabase) = delegate.onOpen(db)

    override fun onCorruption(db: SupportSQLiteDatabase) {
        // Reporting must never stop the delegate from recovering the database.
        runCatching { onCorruption.invoke(db) }
        delegate.onCorruption(db)
    }
}
