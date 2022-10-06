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

package com.duckduckgo.cookies.impl.features.firstparty

import android.database.sqlite.SQLiteDatabase
import com.duckduckgo.app.fire.DatabaseLocator
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.statistics.pixels.ExceptionPixel
import com.duckduckgo.cookies.impl.CookiesPixelName
import com.duckduckgo.cookies.impl.SQLCookieRemover
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
    private val exceptionPixel: ExceptionPixel,
    private val dispatcherProvider: DispatcherProvider,
) : FirstPartyCookiesModifier {

    override suspend fun expireFirstPartyCookies(): Boolean {
        return withContext(dispatcherProvider.io()) {
            val databasePath: String = webViewDatabaseLocator.getDatabasePath()
            if (databasePath.isNotEmpty()) {
                return@withContext expireFirstPartyCookies(databasePath)
            }
            return@withContext false
        }
    }

    private fun excludedSites(): List<String> =
        cookiesRepository.exceptions.map { it.domain } +
            userAllowListRepository.domainsInUserAllowList() +
            unprotectedTemporary.unprotectedTemporaryExceptions.map { it.domain }

    private fun buildSQLWhereClause(timestampThreshold: Long): String {
        val excludedSites: List<String> = excludedSites()
        if (excludedSites.isEmpty()) {
            return "expires_utc > $timestampThreshold AND is_httponly = 0"
        }
        return excludedSites.foldIndexed(
            ""
        ) { pos, acc, site ->
            if (pos == 0) {
                "expires_utc > $timestampThreshold AND is_httponly = 0 AND host_key != '$site' AND host_key NOT LIKE '%.$site'"
            } else {
                "$acc AND host_key != '$site' AND host_key NOT LIKE '%.$site'"
            }
        }
    }
    private fun expireFirstPartyCookies(
        databasePath: String,
    ): Boolean {
        var updateExecuted = false
        openReadableDatabase(databasePath)?.apply {
            try {
                val maxAge = cookiesRepository.firstPartyCookiePolicy.maxAge // in seconds
                val threshold = cookiesRepository.firstPartyCookiePolicy.threshold // in seconds

                // both timestamps in micro seconds from 1601
                val timestampThreshold = (System.currentTimeMillis() + TIME_1601_IN_MICRO + (threshold * MULTIPLIER)) * MULTIPLIER
                val timestampMaxAge = (System.currentTimeMillis() + TIME_1601_IN_MICRO + (maxAge * MULTIPLIER)) * MULTIPLIER

                execSQL(
                    """
                     UPDATE ${SQLCookieRemover.COOKIES_TABLE_NAME}
                     SET expires_utc=$timestampMaxAge
                     WHERE ${buildSQLWhereClause(timestampThreshold)}
                    """.trimIndent()
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
            SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE, null)
        } catch (exception: Exception) {
            null
        }
    }

    companion object {
        const val TIME_1601_IN_MICRO = 11644473600000
        const val MULTIPLIER = 1000
    }
}
