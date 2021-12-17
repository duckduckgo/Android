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

package com.duckduckgo.app.fire

import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.webkit.CookieManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.ExceptionPixel
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import javax.inject.Named
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

interface CookieRemover {
    suspend fun removeCookies(): Boolean
}

class CookieManagerRemover(private val cookieManager: CookieManager) : CookieRemover {
    override suspend fun removeCookies(): Boolean {
        suspendCoroutine<Unit> { continuation ->
            cookieManager.removeAllCookies {
                Timber.v("All cookies removed; restoring DDG cookies")
                continuation.resume(Unit)
            }
        }
        return true
    }
}

class SQLCookieRemover(
    @Named("webViewDbLocator") private val webViewDatabaseLocator: DatabaseLocator,
    private val getCookieHostsToPreserve: GetCookieHostsToPreserve,
    private val offlinePixelCountDataStore: OfflinePixelCountDataStore,
    private val exceptionPixel: ExceptionPixel,
    private val dispatcherProvider: DispatcherProvider
) : CookieRemover {

    private val databaseErrorHandler = PixelSenderDatabaseErrorHandler(offlinePixelCountDataStore)

    override suspend fun removeCookies(): Boolean {
        return withContext(dispatcherProvider.io()) {
            val databasePath: String = webViewDatabaseLocator.getDatabasePath()
            if (databasePath.isNotEmpty()) {
                val excludedHosts = getCookieHostsToPreserve()
                return@withContext removeCookies(databasePath, excludedHosts)
            } else {
                offlinePixelCountDataStore.cookieDatabaseNotFoundCount += 1
            }
            return@withContext false
        }
    }

    private fun openReadableDatabase(databasePath: String): SQLiteDatabase? {
        return try {
            SQLiteDatabase.openDatabase(
                databasePath, null, SQLiteDatabase.OPEN_READWRITE, databaseErrorHandler)
        } catch (exception: Exception) {
            offlinePixelCountDataStore.cookieDatabaseOpenErrorCount += 1
            exceptionPixel.sendExceptionPixel(
                AppPixelName.COOKIE_DATABASE_EXCEPTION_OPEN_ERROR, exception)
            null
        }
    }

    private fun removeCookies(databasePath: String, excludedSites: List<String>): Boolean {
        var deleteExecuted = false
        openReadableDatabase(databasePath)?.apply {
            try {
                val whereClause = buildSQLWhereClause(excludedSites)
                val number = delete(COOKIES_TABLE_NAME, whereClause, excludedSites.toTypedArray())
                execSQL("VACUUM")
                deleteExecuted = true
                Timber.v("$number cookies removed")
            } catch (exception: Exception) {
                Timber.e(exception)
                offlinePixelCountDataStore.cookieDatabaseDeleteErrorCount += 1
                exceptionPixel.sendExceptionPixel(
                    AppPixelName.COOKIE_DATABASE_EXCEPTION_DELETE_ERROR, exception)
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
            { pos, acc, _ ->
                if (pos == 0) {
                    "host_key NOT LIKE ?"
                } else {
                    "$acc AND host_key NOT LIKE ?"
                }
            })
    }

    companion object {
        private const val COOKIES_TABLE_NAME = "cookies"
    }

    private class PixelSenderDatabaseErrorHandler(
        private val offlinePixelCountDataStore: OfflinePixelCountDataStore
    ) : DatabaseErrorHandler {

        private val delegate = DefaultDatabaseErrorHandler()

        override fun onCorruption(dbObj: SQLiteDatabase?) {
            delegate.onCorruption(dbObj)
            offlinePixelCountDataStore.cookieDatabaseCorruptedCount += 1
        }
    }
}
