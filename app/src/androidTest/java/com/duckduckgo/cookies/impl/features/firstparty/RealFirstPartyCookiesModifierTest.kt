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
import android.os.Build
import android.webkit.CookieManager
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.fire.WebViewDatabaseLocator
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.exception.RootExceptionFinder
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.statistics.pixels.ExceptionPixel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.cookies.api.CookieException
import com.duckduckgo.cookies.impl.SQLCookieRemover
import com.duckduckgo.cookies.store.CookiesRepository
import com.duckduckgo.cookies.store.FirstPartyCookiePolicyEntity
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.config.api.UnprotectedTemporaryException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@ExperimentalCoroutinesApi
class RealFirstPartyCookiesModifierTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val cookieManager = CookieManager.getInstance()
    private val mockPixel: Pixel = mock()
    private val mockCookiesRepository: CookiesRepository = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()
    private val webViewDatabaseLocator = WebViewDatabaseLocator(context)

    @Before
    fun before() {
        val policy = FirstPartyCookiePolicyEntity(threshold = THRESHOLD, maxAge = MAX_AGE)
        whenever(mockCookiesRepository.firstPartyCookiePolicy).thenReturn(policy)
        whenever(mockCookiesRepository.exceptions).thenReturn(emptyList())
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(emptyList())
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(emptyList())
    }

    @After
    fun after() = runBlocking {
        removeExistingCookies()
        db.close()
    }

    @Test
    fun when1stPartyCookiesExistAndThresholdIsHigherThenNewExpiryDateMatchesMaxAge() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // these tests fail on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        val expectedValue: Long = (
            (
                Instant.now()
                    .plus(MAX_AGE.toLong(), ChronoUnit.SECONDS)
                    .atOffset(ZoneOffset.UTC)
                    .toEpochSecond() * MULTIPLIER
                ) + TIME_1601_IN_MICRO
            ) * MULTIPLIER

        givenDatabaseWithCookies((THRESHOLD + 1).toLong())
        val sqlCookieRemover = givenRealFirstPartyCookiesModifier()

        sqlCookieRemover.expireFirstPartyCookies()
        val expires = queryCookiesDB("example.com")

        assertNotNull(expires)
        val diffInSeconds = (expires!! - expectedValue) / 1000000 // initially in microseconds
        assertTrue(diffInSeconds < 1) // There should be less than 1s difference between the expected and the actual
    }

    @Test
    fun when1stPartyCookiesExistAndThresholdIsNotHigherThenExpiryDateDoesNotChange() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        givenDatabaseWithCookies((THRESHOLD).toLong())
        val sqlCookieRemover = givenRealFirstPartyCookiesModifier()

        val initialValue = queryCookiesDB("example.com")
        sqlCookieRemover.expireFirstPartyCookies()
        val finalValue = queryCookiesDB("example.com")

        assertNotNull(initialValue)
        assertNotNull(finalValue)
        assertEquals(initialValue, finalValue)
    }

    @Test
    fun when1stPartyCookiesExistAndDomainInUserAllowListThenExpiryDateDoesNotChange() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        givenDatabaseWithCookies((THRESHOLD + 1).toLong())
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(listOf("example.com"))
        val sqlCookieRemover = givenRealFirstPartyCookiesModifier()

        val initialValue = queryCookiesDB("example.com")
        sqlCookieRemover.expireFirstPartyCookies()
        val finalValue = queryCookiesDB("example.com")

        assertNotNull(initialValue)
        assertNotNull(finalValue)
        assertEquals(initialValue, finalValue)
    }

    @Test
    fun when1stPartyCookiesExistAsSubdomainAndDomainInUserAllowListThenExpiryDateDoesNotChange() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        givenDatabaseWithCookies((THRESHOLD + 1).toLong(), "test.example.com")
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(listOf("example.com"))
        val sqlCookieRemover = givenRealFirstPartyCookiesModifier()

        val initialValue = queryCookiesDB("test.example.com")
        sqlCookieRemover.expireFirstPartyCookies()
        val finalValue = queryCookiesDB("test.example.com")

        assertNotNull(initialValue)
        assertNotNull(finalValue)
        assertEquals(initialValue, finalValue)
    }

    @Test
    fun when1stPartyCookiesExistAndDomainInUnprotectedTemporaryThenExpiryDateDoesNotChange() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        givenDatabaseWithCookies((THRESHOLD + 1).toLong())
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(listOf(UnprotectedTemporaryException("example.com", "reason")))
        val sqlCookieRemover = givenRealFirstPartyCookiesModifier()

        val initialValue = queryCookiesDB("example.com")
        sqlCookieRemover.expireFirstPartyCookies()
        val finalValue = queryCookiesDB("example.com")

        assertNotNull(initialValue)
        assertNotNull(finalValue)
        assertEquals(initialValue, finalValue)
    }

    @Test
    fun when1stPartyCookiesExistAndDomainInExceptionsThenExpiryDateDoesNotChange() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        givenDatabaseWithCookies((THRESHOLD + 1).toLong())
        whenever(mockCookiesRepository.exceptions).thenReturn(
            listOf(CookieException(domain = "example.com", reason = "test"))
        )
        val sqlCookieRemover = givenRealFirstPartyCookiesModifier()

        val initialValue = queryCookiesDB("example.com")
        sqlCookieRemover.expireFirstPartyCookies()
        val finalValue = queryCookiesDB("example.com")

        assertNotNull(initialValue)
        assertNotNull(finalValue)
        assertEquals(initialValue, finalValue)
    }

    private suspend fun queryCookiesDB(host: String): Long? {
        return withContext(DefaultDispatcherProvider().io()) {
            val databasePath: String = webViewDatabaseLocator.getDatabasePath()
            if (databasePath.isNotEmpty()) {
                return@withContext query(databasePath, host)
            }
            return@withContext null
        }
    }

    private fun query(
        databasePath: String,
        host: String,
    ): Long? {
        var value: Long? = null
        openReadableDatabase(databasePath)?.apply {
            use {
                query(
                    SQLCookieRemover.COOKIES_TABLE_NAME,
                    arrayOf("expires_utc"),
                    "host_key ='$host'",
                    null, null, null, null
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        value = cursor.getLong(cursor.getColumnIndex("expires_utc"))
                    }
                }
            }
        }
        return value
    }

    private fun openReadableDatabase(databasePath: String): SQLiteDatabase? {
        return try {
            SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE, null)
        } catch (exception: Exception) {
            null
        }
    }

    private fun givenDatabaseWithCookies(expiryTimeInSeconds: Long, cookieName: String = "example.com") {
        val time = Instant.now()
            .plus(expiryTimeInSeconds, ChronoUnit.SECONDS)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("EEE, d MMMM yyyy HH:mm:ss Z"))
        cookieManager.setCookie(cookieName, "da=da;expires=$time")
        cookieManager.flush()
    }

    private suspend fun removeExistingCookies() {
        withContext(Dispatchers.Main) {
            suspendCoroutine { continuation ->
                cookieManager.removeAllCookies { continuation.resume(Unit) }
            }
        }
    }

    private fun givenRealFirstPartyCookiesModifier(): RealFirstPartyCookiesModifier {
        return RealFirstPartyCookiesModifier(
            mockCookiesRepository,
            mockUnprotectedTemporary,
            mockUserAllowListRepository,
            webViewDatabaseLocator,
            ExceptionPixel(mockPixel, RootExceptionFinder()),
            DefaultDispatcherProvider()
        )
    }

    companion object {
        const val MAX_AGE = 86400 // 1 day
        const val THRESHOLD = 172800 // 2 days
        const val TIME_1601_IN_MICRO = 11644473600000
        const val MULTIPLIER = 1000
    }
}
