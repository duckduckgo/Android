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

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.webkit.CookieManager
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.fire.FireproofRepository
import com.duckduckgo.app.fire.WebViewDatabaseLocator
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.cookies.impl.SQLCookieRemover
import com.duckduckgo.cookies.store.CookiesRepository
import com.duckduckgo.cookies.store.FirstPartyCookiePolicyEntity
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("NoHardcodedCoroutineDispatcher")
class RealFirstPartyCookiesModifierTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cookieManager = CookieManager.getInstance()
    private val mockCookiesRepository: CookiesRepository = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()
    private val mockFireproofRepository: FireproofRepository = mock()
    private val mockPixel: Pixel = mock()
    private val webViewDatabaseLocator = WebViewDatabaseLocator(context)

    @Before
    fun before() {
        val policy = FirstPartyCookiePolicyEntity(threshold = THRESHOLD, maxAge = MAX_AGE)
        whenever(mockCookiesRepository.firstPartyCookiePolicy).thenReturn(policy)
        whenever(mockCookiesRepository.exceptions).thenReturn(emptyList())
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(emptyList())
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(emptyList())
        whenever(mockFireproofRepository.fireproofWebsites()).thenReturn(emptyList())
    }

    @After
    fun after() = runTest {
        removeExistingCookies()
    }

    @Test
    fun when1stPartyCookiesExistAndThresholdIsNotHigherThenExpiryDateDoesNotChange() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        withContext(Dispatchers.Main) {
            givenDatabaseWithCookies((THRESHOLD).toLong())
            val sqlCookieRemover = givenRealFirstPartyCookiesModifier()

            val initialValue = queryCookiesDB("example.com")
            sqlCookieRemover.expireFirstPartyCookies()
            val finalValue = queryCookiesDB("example.com")

            assertNotNull(initialValue)
            assertNotNull(finalValue)
            assertEquals(initialValue, finalValue)
        }
    }

    @Test
    fun when1stPartyCookiesExistAndDomainInUserAllowListThenExpiryDateDoesNotChange() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        withContext(Dispatchers.Main) {
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
    }

    @Test
    fun when1stPartyCookiesExistAsSubdomainAndDomainInUserAllowListThenExpiryDateDoesNotChange() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        withContext(Dispatchers.Main) {
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
    }

    @Test
    fun when1stPartyCookiesExistAndDomainInUnprotectedTemporaryThenExpiryDateDoesNotChange() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        withContext(Dispatchers.Main) {
            givenDatabaseWithCookies((THRESHOLD + 1).toLong())
            whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(
                listOf(
                    FeatureException(
                        "example.com",
                        "reason",
                    ),
                ),
            )
            val sqlCookieRemover = givenRealFirstPartyCookiesModifier()

            val initialValue = queryCookiesDB("example.com")
            sqlCookieRemover.expireFirstPartyCookies()
            val finalValue = queryCookiesDB("example.com")

            assertNotNull(initialValue)
            assertNotNull(finalValue)
            assertEquals(initialValue, finalValue)
        }
    }

    @Test
    fun when1stPartyCookiesExistAndDomainInExceptionsThenExpiryDateDoesNotChange() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        withContext(Dispatchers.Main) {
            givenDatabaseWithCookies((THRESHOLD + 1).toLong())
            whenever(mockCookiesRepository.exceptions).thenReturn(
                listOf(FeatureException(domain = "example.com", reason = "test")),
            )
            val sqlCookieRemover = givenRealFirstPartyCookiesModifier()

            val initialValue = queryCookiesDB("example.com")
            sqlCookieRemover.expireFirstPartyCookies()
            val finalValue = queryCookiesDB("example.com")

            assertNotNull(initialValue)
            assertNotNull(finalValue)
            assertEquals(initialValue, finalValue)
        }
    }

    @Test
    fun when1stPartyCookiesExistAndDomainIsFireproofedThenExpiryDateDoesNotChange() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        withContext(Dispatchers.Main) {
            givenDatabaseWithCookies((THRESHOLD + 1).toLong())
            whenever(mockFireproofRepository.fireproofWebsites()).thenReturn(
                listOf("example.com"),
            )
            val sqlCookieRemover = givenRealFirstPartyCookiesModifier()

            val initialValue = queryCookiesDB("example.com")
            sqlCookieRemover.expireFirstPartyCookies()
            val finalValue = queryCookiesDB("example.com")

            assertNotNull(initialValue)
            assertNotNull(finalValue)
            assertEquals(initialValue, finalValue)
        }
    }

    @Test
    fun when1stPartyCookiesExistAndDomainIsDuckDuckGoThenExpiryDateDoesNotChange() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        withContext(Dispatchers.Main) {
            givenDatabaseWithCookies((THRESHOLD + 1).toLong(), "duckduckgo.com")
            val sqlCookieRemover = givenRealFirstPartyCookiesModifier()

            val initialValue = queryCookiesDB("duckduckgo.com")
            sqlCookieRemover.expireFirstPartyCookies()
            val finalValue = queryCookiesDB("duckduckgo.com")

            assertNotNull(initialValue)
            assertNotNull(finalValue)
            assertEquals(initialValue, finalValue)
        }
    }

    @Test
    fun when1stPartyCookiesExistAndDomainIsDuckDuckGoSurveysThenExpiryDateDoesNotChange() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        withContext(Dispatchers.Main) {
            givenDatabaseWithCookies((THRESHOLD + 1).toLong(), "surveys.duckduckgo.com")
            val sqlCookieRemover = givenRealFirstPartyCookiesModifier()

            val initialValue = queryCookiesDB("surveys.duckduckgo.com")
            sqlCookieRemover.expireFirstPartyCookies()
            val finalValue = queryCookiesDB("surveys.duckduckgo.com")

            assertNotNull(initialValue)
            assertNotNull(finalValue)
            assertEquals(initialValue, finalValue)
        }
    }

    @Test
    fun whenFiltersExceedOneThousandQueryShouldNotCrashAndPixelShouldNotBeSent() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // this test fails on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }
        givenOneThousandFilters()
        val sqlCookieRemover = givenRealFirstPartyCookiesModifier()
        sqlCookieRemover.expireFirstPartyCookies()
        verifyNoInteractions(mockPixel)
    }

    private fun queryCookiesDB(host: String): Long? {
        val databasePath: String = webViewDatabaseLocator.getDatabasePath()
        if (databasePath.isNotEmpty()) {
            return query(databasePath, host)
        }
        return null
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
                    null,
                    null,
                    null,
                    null,
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
            .format(DateTimeFormatter.ofPattern("EEE, d MMMM yyyy HH:mm:ss Z").withLocale(Locale.US))

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
            mockPixel,
            mockFireproofRepository,
            DefaultDispatcherProvider(),
        )
    }

    private fun givenOneThousandFilters() {
        val list = mutableListOf<String>()
        (0..1000).forEach {
            list.add("Element$it")
        }
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(list)
    }

    companion object {
        const val MAX_AGE = 86400 // 1 day
        const val THRESHOLD = 172800 // 2 days
        const val TIME_1601_IN_MICRO = 11644473600000
        const val MULTIPLIER = 1000
    }
}
