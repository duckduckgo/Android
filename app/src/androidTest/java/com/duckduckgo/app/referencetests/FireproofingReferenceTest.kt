/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.referencetests

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.browser.cookies.DefaultCookieManagerProvider
import com.duckduckgo.app.fire.CookieManagerRemover
import com.duckduckgo.app.fire.GetCookieHostsToPreserve
import com.duckduckgo.app.fire.RemoveCookies
import com.duckduckgo.app.fire.SQLCookieRemover
import com.duckduckgo.app.fire.WebViewCookieManager
import com.duckduckgo.app.fire.WebViewDatabaseLocator
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.domain
import com.duckduckgo.app.global.exception.RootExceptionFinder
import com.duckduckgo.app.statistics.pixels.ExceptionPixel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import org.mockito.kotlin.mock
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@ExperimentalCoroutinesApi
@RunWith(Parameterized::class)
class FireproofingReferenceTest(private val testCase: TestCase) {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val cookieManagerProvider = DefaultCookieManagerProvider()
    private val cookieManager = cookieManagerProvider.get()
    private val fireproofWebsiteDao = db.fireproofWebsiteDao()
    private val mockPixel = mock<Pixel>()
    private val mockOfflinePixelCountDataStore = mock<OfflinePixelCountDataStore>()
    private val webViewDatabaseLocator = WebViewDatabaseLocator(context)
    private val getHostsToPreserve = GetCookieHostsToPreserve(fireproofWebsiteDao)
    private lateinit var testee: WebViewCookieManager

    companion object {
        private lateinit var fireproofedSites: List<String>
        private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)

        @JvmStatic
        @Parameterized.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val referenceTest = adapter.fromJson(
                FileUtilities.loadText(
                    FireproofingReferenceTest::class.java.classLoader!!,
                    "reference_tests/fireproofing/tests.json"
                )
            )
            fireproofedSites = referenceTest?.fireButtonFireproofing?.fireproofedSites.orEmpty()
            return referenceTest?.fireButtonFireproofing?.tests?.filterNot { it.exceptPlatforms.contains("android-browser") } ?: emptyList()
        }
    }

    @Before
    fun before() {
        val sqlCookieRemover = SQLCookieRemover(
            webViewDatabaseLocator,
            getHostsToPreserve,
            mockOfflinePixelCountDataStore,
            ExceptionPixel(mockPixel, RootExceptionFinder()),
            DefaultDispatcherProvider()
        )

        val removeCookiesStrategy = RemoveCookies(CookieManagerRemover(cookieManagerProvider), sqlCookieRemover)

        testee = WebViewCookieManager(cookieManagerProvider, "duckduckgo.com", removeCookiesStrategy, DefaultDispatcherProvider())

        fireproofedSites.map { url ->
            fireproofWebsiteDao.insert(FireproofWebsiteEntity(url.toUri().domain().orEmpty()))
        }
    }

    @After
    fun after() = runTest {
        removeExistingCookies()
        db.close()
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // these tests fail on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        withContext(Dispatchers.Main) {
            givenDatabaseWithCookies(testCase.cookieDomain, testCase.cookieName)

            testee.removeExternalCookies()

            openReadableDatabase(webViewDatabaseLocator.getDatabasePath())?.apply {
                use {
                    rawQuery("SELECT * FROM cookies WHERE host_key='${testCase.cookieDomain}' LIMIT 1", null).use { cursor ->
                        cursor.moveToFirst()
                        if (testCase.expectCookieRemoved) {
                            assertTrue(cursor.count == 0)
                        } else {
                            assertTrue(cursor.count == 1)
                        }
                    }
                }
            }
        }
    }

    private fun openReadableDatabase(databasePath: String): SQLiteDatabase? {
        return try {
            SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE, null)
        } catch (exception: Exception) {
            null
        }
    }

    private fun givenDatabaseWithCookies(domain: String, name: String) {
        cookieManager.setCookie(domain, "$name=test")
        cookieManager.flush()
    }

    private suspend fun removeExistingCookies() {
        withContext(Dispatchers.Main) {
            suspendCoroutine<Unit> { continuation ->
                cookieManager.removeAllCookies { continuation.resume(Unit) }
            }
        }
    }

    data class TestCase(
        val name: String,
        val cookieDomain: String,
        val cookieName: String,
        val expectCookieRemoved: Boolean,
        val exceptPlatforms: List<String>
    )

    data class FireproofTest(
        val name: String,
        val desc: String,
        val fireproofedSites: List<String>,
        val tests: List<TestCase>
    )

    data class ReferenceTest(
        val fireButtonFireproofing: FireproofTest
    )
}
