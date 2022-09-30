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

package com.duckduckgo.cookies.impl.trackingcookies1p

import android.database.sqlite.SQLiteDatabase
import com.duckduckgo.app.fire.DatabaseLocator
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.statistics.pixels.ExceptionPixel
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.cookies.impl.CookiesPixelName
import com.duckduckgo.cookies.impl.SQLCookieRemover
import com.duckduckgo.cookies.impl.db.PixelSenderDatabaseErrorHandler
import com.duckduckgo.cookies.store.CookiesRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
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
    private val cookiesRepository: CookiesRepository,
    private val unprotectedTemporary: UnprotectedTemporary,
    private val userAllowListRepository: UserAllowListRepository,
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

    private fun buildSQLWhereClause(): String {
        val excludedSites: List<String> =
            cookiesRepository.exceptions.map { it.domain } + userAllowListRepository.domainsInUserAllowList() + unprotectedTemporary.allExceptions()

        if (excludedSites.isEmpty()) {
            return ""
        }
        return excludedSites.foldIndexed(
            "AND "
        ) { pos, acc, _ ->
            if (pos == 0) {
                "host_key NOT LIKE ?"
            } else {
                "$acc AND host_key NOT LIKE ?"
            }
        }
    }
    private fun expireFirstPartyCookies(
        databasePath: String,
    ): Boolean {
        var updateExecuted = false
        openReadableDatabase(databasePath)?.apply {
            try {
                val maxAge = cookiesRepository.firstPartyCookieTrackerPolicy.maxAge
                val threshold = cookiesRepository.firstPartyCookieTrackerPolicy.threshold
                val timestampThreshold = (System.currentTimeMillis() + TIME_1601_IN_MICRO + threshold) * MICROSECONDS
                val timestampMaxAge = (System.currentTimeMillis() + TIME_1601_IN_MICRO + maxAge) * MICROSECONDS
                execSQL(
                    """
                    UPDATE ${SQLCookieRemover.COOKIES_TABLE_NAME}
                        SET expires_utc=$timestampMaxAge
                    WHERE expires_utc > $timestampThreshold AND is_httponly = 0 ${buildSQLWhereClause()}
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
    }
}
