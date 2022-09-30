/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.cookies.impl.firstparty

import android.database.sqlite.SQLiteDatabase
import com.duckduckgo.app.fire.DatabaseLocator
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.pixels.ExceptionPixel
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.cookies.impl.CookiesPixelName
import com.duckduckgo.cookies.impl.SQLCookieRemover
import com.duckduckgo.cookies.impl.db.PixelSenderDatabaseErrorHandler
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

interface FirstPartyCookiesModifier {
    suspend fun expireFirstPartyCookies(): Boolean
}

@ContributesBinding(AppScope::class)
class RealFirstPartyCookiesModifier @Inject constructor(
    @Named("webViewDbLocator") private val webViewDatabaseLocator: DatabaseLocator,
    private val offlinePixelCountDataStore: OfflinePixelCountDataStore,
    private val exceptionPixel: ExceptionPixel,
    private val dispatcherProvider: DispatcherProvider
) : FirstPartyCookiesModifier {

    private val databaseErrorHandler = PixelSenderDatabaseErrorHandler(offlinePixelCountDataStore)

    override suspend fun expireFirstPartyCookies(): Boolean {
        return withContext(dispatcherProvider.io()) {
            val databasePath: String = webViewDatabaseLocator.getDatabasePath()
            if (databasePath.isNotEmpty()) {
                return@withContext expireFirstPartyCookies(databasePath)
            } else {
                offlinePixelCountDataStore.cookieDatabaseNotFoundCount += 1
            }
            return@withContext false
        }
    }

    private fun expireFirstPartyCookies(
        databasePath: String,
    ): Boolean {
        var updateExecuted = false
        openReadableDatabase(databasePath)?.apply {
            try {
                val timestamp = (System.currentTimeMillis() + TIME_1601_IN_MICRO + SEVEN_DAYS_IN_MS) * MICROSECONDS
                execSQL(
                    """
                    UPDATE ${SQLCookieRemover.COOKIES_TABLE_NAME}
                        SET expires_utc=$timestamp
                    WHERE expires_utc > $timestamp AND is_httponly = 0
                    """
                )
                updateExecuted = true
            } catch (exception: Exception) {
                Timber.e(exception)
                exceptionPixel.sendExceptionPixel(CookiesPixelName.COOKIE_DATABASE_EXCEPTION_EXPIRE_ERROR, exception)
            } finally {
                close()
            }
        }
        return updateExecuted
    }

    private fun openReadableDatabase(databasePath: String): SQLiteDatabase? {
        return try {
            SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE, databaseErrorHandler)
        } catch (exception: Exception) {
            offlinePixelCountDataStore.cookieDatabaseOpenErrorCount += 1
            exceptionPixel.sendExceptionPixel(CookiesPixelName.COOKIE_DATABASE_EXCEPTION_OPEN_ERROR, exception)
            null
        }
    }

    companion object {
        const val TIME_1601_IN_MICRO = 11644473600000
        const val MICROSECONDS = 1000
        const val SEVEN_DAYS_IN_MS = 604800000
    }
}
