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

import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.core.net.toUri
import com.duckduckgo.app.fire.DatabaseLocator
import com.duckduckgo.app.fire.FireproofRepository
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.impl.CookiesPixelName.COOKIE_EXPIRE_ERROR
import com.duckduckgo.cookies.impl.SQLCookieRemover
import com.duckduckgo.cookies.impl.WebViewCookieManager.Companion.DDG_COOKIE_DOMAINS
import com.duckduckgo.cookies.impl.redactStacktraceInBase64
import com.duckduckgo.cookies.store.CookiesRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.asLog
import javax.inject.Inject
import javax.inject.Named

interface FirstPartyCookiesModifier {
    suspend fun expireFirstPartyCookies()
}

@ContributesBinding(AppScope::class)
class RealFirstPartyCookiesModifier @Inject constructor(
    private val cookiesRepository: CookiesRepository,
    private val unprotectedTemporary: UnprotectedTemporary,
    private val userAllowListRepository: UserAllowListRepository,
    @Named("webViewDbLocator") private val webViewDatabaseLocator: DatabaseLocator,
    private val pixel: Pixel,
    private val fireproofRepository: FireproofRepository,
    private val dispatcherProvider: DispatcherProvider,
) : FirstPartyCookiesModifier {

    private val databaseErrorHandler = DatabaseErrorHandler()

    override suspend fun expireFirstPartyCookies() {
        withContext(dispatcherProvider.io()) {
            val databasePath: String = webViewDatabaseLocator.getDatabasePath()
            if (databasePath.isNotEmpty()) {
                expireFirstPartyCookies(databasePath)
            }
        }
    }

    // The First Party Cookies expiry feature does not expire the user's DuckDuckGo search settings, which are saved as cookies.
    // Expiring these cookies would reset them and have undesired consequences, i.e. changing the theme, default language, etc.
    // This feature also does not expire temporary cookies associated with 'surveys.duckduckgo.com'.
    // When we launch surveys to help us understand issues that impact users over time, we use this cookie to temporarily store anonymous
    // survey answers, before deleting the cookie. Cookie storage duration is communicated to users before they opt to submit survey answers.
    // These cookies are not stored in a personally identifiable way. For example, the large size setting is stored as 's=l.'
    // More info in https://duckduckgo.com/privacy
    private fun excludedSites(): List<String> =
        cookiesRepository.exceptions.map { it.domain } +
            userAllowListRepository.domainsInUserAllowList() +
            unprotectedTemporary.unprotectedTemporaryExceptions.map { it.domain } +
            fireproofRepository.fireproofWebsites() +
            DDG_COOKIE_DOMAINS.map { it.toUri().host!! }

    private fun buildSQLWhereClause(timestampThreshold: Long, isOldDb: Boolean, excludedSites: List<String>): String {
        val httpOnly = if (isOldDb) "httponly" else "is_httponly"
        if (excludedSites.isEmpty()) {
            return "expires_utc > $timestampThreshold AND $httpOnly = 0"
        }
        return excludedSites.foldIndexed(
            "",
        ) { pos, acc, site ->
            if (pos == 0) {
                "expires_utc > $timestampThreshold AND $httpOnly = 0 AND host_key != '$site' AND host_key NOT LIKE '%.$site'"
            } else {
                "$acc AND host_key != '$site' AND host_key NOT LIKE '%.$site'"
            }
        }
    }
    private fun expireFirstPartyCookies(
        databasePath: String,
    ) {
        openReadableDatabase(databasePath)?.apply {
            try {
                val maxAge = cookiesRepository.firstPartyCookiePolicy.maxAge // in seconds
                val threshold = cookiesRepository.firstPartyCookiePolicy.threshold // in seconds

                // both timestamps in micro seconds from 1601
                val timestampThreshold = (System.currentTimeMillis() + TIME_1601_IN_MICRO + (threshold * MULTIPLIER)) * MULTIPLIER
                val timestampMaxAge = (System.currentTimeMillis() + TIME_1601_IN_MICRO + (maxAge * MULTIPLIER)) * MULTIPLIER

                // check table and column exist before executing query
                // old WebView versions use httponly, newer use is_httponly
                val columnExists =
                    rawQuery("PRAGMA table_info('${SQLCookieRemover.COOKIES_TABLE_NAME}')", null).use {
                        while (it.moveToNext()) {
                            val index = it.getColumnIndex("name")
                            if (it.getString(index).equals("is_httponly")) {
                                return@use 1
                            }
                            if (it.getString(index).equals("httponly")) {
                                return@use 2
                            }
                        }
                        return@use 0
                    }

                if (columnExists > 0) {
                    val isOldDb = (columnExists == 2)
                    val excludedSites = excludedSites()
                    excludedSites.chunked(CHUNKS).map {
                        execSQL(
                            """
                             UPDATE ${SQLCookieRemover.COOKIES_TABLE_NAME}
                             SET expires_utc=$timestampMaxAge
                             WHERE ${buildSQLWhereClause(timestampThreshold, isOldDb, it)}
                            """.trimIndent(),
                        )
                    }
                }
            } catch (exception: Exception) {
                val stacktrace = redactStacktraceInBase64(exception.asLog())
                val params = mapOf(
                    "ss" to stacktrace,
                )
                pixel.fire(COOKIE_EXPIRE_ERROR, params)
            } finally {
                close()
            }
        }
    }

    private fun openReadableDatabase(databasePath: String): SQLiteDatabase? {
        return try {
            SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE, databaseErrorHandler)
        } catch (exception: Exception) {
            null
        }
    }

    companion object {
        private const val TIME_1601_IN_MICRO = 11644473600000
        private const val MULTIPLIER = 1000
        private const val CHUNKS = 450 // Max depth is 1000, each filter happens twice plus hardcoded filters, we use 450 to give some wiggle room
    }
}

private class DatabaseErrorHandler : DatabaseErrorHandler {

    private val delegate = DefaultDatabaseErrorHandler()

    override fun onCorruption(dbObj: SQLiteDatabase?) {
        delegate.onCorruption(dbObj)
    }
}
