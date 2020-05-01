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

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.webkit.CookieManager
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource
import com.duckduckgo.app.statistics.pixels.Pixel
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
    private val webViewDatabaseLocator: DatabaseLocator,
    private val getHostsToPreserve: GetHostsToPreserve,
    private val pixel: Pixel,
    private val uncaughtExceptionRepository: UncaughtExceptionRepository,
    private val dispatcherProvider: DispatcherProvider
) : CookieRemover {

    override suspend fun removeCookies(): Boolean {
        return withContext(dispatcherProvider.io()) {
            val databasePath: String = webViewDatabaseLocator.getDatabasePath()
            if (databasePath.isNotEmpty()) {
                val excludedHosts = getHostsToPreserve()
                return@withContext removeCookies(databasePath, excludedHosts)
            } else {
                pixel.fire(Pixel.PixelName.COOKIE_DATABASE_NOT_FOUND)
            }
            return@withContext false
        }
    }

    private suspend fun openReadableDatabase(databasePath: String): SQLiteDatabase? {
        return try {
            SQLiteDatabase.openDatabase(
                databasePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
                DatabaseErrorHandler { Timber.d("COOKIE: onCorruption") })
        } catch (exception: Exception) {
            pixel.fire(Pixel.PixelName.COOKIE_DATABASE_OPEN_ERROR)
            uncaughtExceptionRepository.recordUncaughtException(exception, UncaughtExceptionSource.COOKIE_DATABASE)
            null
        }
    }

    private suspend fun removeCookies(databasePath: String, excludedSites: List<String>): Boolean {
        var deleteExecuted = false
        openReadableDatabase(databasePath)?.apply {
            try {
                val whereClause = buildSQLWhereClause(excludedSites)
                val number = delete(COOKIES_TABLE_NAME, whereClause, excludedSites.toTypedArray())
                deleteExecuted = true
                Timber.v("$number cookies removed")
            } catch (exception: Exception) {
                Timber.e(exception)
                pixel.fire(Pixel.PixelName.COOKIE_DATABASE_DELETE_ERROR)
                uncaughtExceptionRepository.recordUncaughtException(exception, UncaughtExceptionSource.COOKIE_DATABASE)
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
        return excludedSites.foldIndexed("", { pos, acc, _ ->
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
}
