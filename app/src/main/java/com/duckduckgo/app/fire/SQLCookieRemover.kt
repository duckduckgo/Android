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
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource
import com.duckduckgo.app.statistics.pixels.Pixel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

private const val COOKIES_TABLE_NAME = "cookies"

class SQLCookieRemover(
    private val context: Context,
    private val fireproofWebsiteDao: FireproofWebsiteDao,
    private val pixel: Pixel,
    private val uncaughtExceptionRepository: UncaughtExceptionRepository
) {
    suspend fun removeCookies(): Boolean {
        return withContext(Dispatchers.IO) {
            var deleteExecuted = false
            val excludedSites = getHostsToPreserve()
            val databasePath: String = getDatabasePath()
            if (databasePath.isNotEmpty()) {
                val readableDatabase = openReadableDatabase(databasePath)
                if (readableDatabase != null) {
                    try {
                        val whereClause = buildSQLWhereClause(excludedSites)
                        val number = readableDatabase.delete(COOKIES_TABLE_NAME, whereClause, excludedSites.toTypedArray())
                        deleteExecuted = true
                        Timber.v("$number cookies removed")
                    } catch (exception: Exception) {
                        pixel.fire(Pixel.PixelName.COOKIE_DATABASE_DELETE_ERROR)
                        uncaughtExceptionRepository.recordUncaughtException(exception, UncaughtExceptionSource.COOKIE_DATABASE)
                    } finally {
                        readableDatabase.close()
                    }
                }
            } else {
                pixel.fire(Pixel.PixelName.COOKIE_DATABASE_NOT_FOUND)
            }
            return@withContext deleteExecuted
        }
    }

    private suspend fun openReadableDatabase(databasePath: String): SQLiteDatabase? {
        val databaseFile = File(context.applicationInfo.dataDir, databasePath)
        return try {
            SQLiteDatabase.openDatabase(
                databaseFile.toString(),
                null,
                SQLiteDatabase.OPEN_READWRITE,
                DatabaseErrorHandler { Timber.d("COOKIE: onCorruption") })
        } catch (exception: Exception) {
            pixel.fire(Pixel.PixelName.COOKIE_DATABASE_OPEN_ERROR)
            uncaughtExceptionRepository.recordUncaughtException(exception, UncaughtExceptionSource.COOKIE_DATABASE)
            null
        }
    }

    private fun getDatabasePath(): String {
        val knownLocations = listOf("app_webview/Default/Cookies", "app_webview/Cookies")
        val filePath: String = knownLocations.find { knownPath ->
            val file = File(context.applicationInfo.dataDir, knownPath)
            file.exists()
        } ?: ""
        return filePath
    }

    private fun buildSQLWhereClause(excludedSites: List<String>): String {
        val whereArg = excludedSites.foldIndexed("", { pos, acc, _ ->
            if (pos == 0) {
                "host_key NOT LIKE ?"
            } else {
                "$acc AND host_key NOT LIKE ?"
            }
        })
        return whereArg
    }

    private fun getHostsToPreserve(): List<String> {
        val bookmarksList = fireproofWebsiteDao.fireproofWebsitesSync()
        return bookmarksList.flatMap { entity ->
            val acceptedHosts = mutableListOf<String>()
            val host = entity.domain
            host.split(".")
                .foldRight("", { next, acc ->
                    val next = ".$next$acc"
                    acceptedHosts.add(next)
                    next
                })
            acceptedHosts.add(host)
            acceptedHosts
        }
    }
}