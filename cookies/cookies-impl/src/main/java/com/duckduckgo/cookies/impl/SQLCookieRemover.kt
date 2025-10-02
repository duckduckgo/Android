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

package com.duckduckgo.cookies.impl

import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.duckduckgo.app.fire.DatabaseLocator
import com.duckduckgo.app.fire.FireproofRepository
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.cookies.api.CookieRemover
import com.duckduckgo.cookies.impl.CookiesPixelName.COOKIE_DELETE_ERROR
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.LogPriority.VERBOSE
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@ContributesBinding(AppScope::class)
@Named("cookieManagerRemover")
class CookieManagerRemover @Inject constructor(private val cookieManagerProvider: CookieManagerProvider) : CookieRemover {
    override suspend fun removeCookies(): Boolean {
        suspendCoroutine { continuation ->
            cookieManagerProvider.get()?.removeAllCookies {
                logcat(VERBOSE) { "All cookies removed; restoring DDG cookies" }
                continuation.resume(Unit)
            }
        }
        return true
    }
}

@ContributesBinding(AppScope::class)
@Named("sqlCookieRemover")
class SQLCookieRemover @Inject constructor(
    @Named("webViewDbLocator") private val webViewDatabaseLocator: DatabaseLocator,
    private val fireproofRepository: FireproofRepository,
    private val pixel: Pixel,
    private val dispatcherProvider: DispatcherProvider,
) : CookieRemover {

    private val databaseErrorHandler = PixelSenderDatabaseErrorHandler()

    override suspend fun removeCookies(): Boolean {
        return withContext(dispatcherProvider.io()) {
            val databasePath: String = webViewDatabaseLocator.getDatabasePath()
            if (databasePath.isNotEmpty()) {
                val excludedHosts = fireproofRepository.fireproofWebsites()
                return@withContext removeCookies(databasePath, excludedHosts)
            }
            return@withContext false
        }
    }

    private fun openReadableDatabase(databasePath: String): SQLiteDatabase? {
        return try {
            SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE, databaseErrorHandler)
        } catch (exception: Exception) {
            pixel.fire(CookiesPixelName.COOKIE_DB_OPEN_ERROR)
            null
        }
    }

    private fun removeCookies(
        databasePath: String,
        excludedSites: List<String>,
    ): Boolean {
        var deleteExecuted = false
        openReadableDatabase(databasePath)?.apply {
            try {
                // check table exists before executing query
                val tableExists =
                    rawQuery("PRAGMA table_info('$COOKIES_TABLE_NAME')", null).use {
                        return@use it.count
                    }
                if (tableExists > 0) {
                    val whereClause = buildSQLWhereClause(excludedSites)
                    delete(COOKIES_TABLE_NAME, whereClause, excludedSites.toTypedArray())
                    execSQL("VACUUM")
                    deleteExecuted = true
                }
            } catch (exception: Exception) {
                val stacktrace = redactStacktraceInBase64(exception.asLog())
                val params = mapOf(
                    "ss" to stacktrace,
                )
                pixel.fire(COOKIE_DELETE_ERROR, params)
            } finally {
                close()
            }
        }
        return deleteExecuted
    }

    private fun buildSQLWhereClause(excludedSites: List<String>): String {
        if (excludedSites.isEmpty()) {
            return ""
        }
        return excludedSites.foldIndexed(
            "",
        ) { pos, acc, _ ->
            if (pos == 0) {
                "host_key NOT LIKE ?"
            } else {
                "$acc AND host_key NOT LIKE ?"
            }
        }
    }

    companion object {
        const val COOKIES_TABLE_NAME = "cookies"
    }

    private class PixelSenderDatabaseErrorHandler : DatabaseErrorHandler {

        private val delegate = DefaultDatabaseErrorHandler()

        override fun onCorruption(dbObj: SQLiteDatabase?) {
            delegate.onCorruption(dbObj)
        }
    }
}
