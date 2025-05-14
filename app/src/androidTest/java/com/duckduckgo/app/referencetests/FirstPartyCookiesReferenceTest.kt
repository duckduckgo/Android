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

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.core.net.toUri
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.fire.FireproofRepository
import com.duckduckgo.app.fire.WebViewDatabaseLocator
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.cookies.impl.DefaultCookieManagerProvider
import com.duckduckgo.cookies.impl.SQLCookieRemover
import com.duckduckgo.cookies.impl.features.CookiesFeature
import com.duckduckgo.cookies.impl.features.firstparty.FirstPartyCookiesModifier
import com.duckduckgo.cookies.impl.features.firstparty.RealFirstPartyCookiesModifier
import com.duckduckgo.cookies.impl.features.firstparty.RealFirstPartyCookiesModifierTest
import com.duckduckgo.cookies.store.CookieExceptionEntity
import com.duckduckgo.cookies.store.CookiesRepository
import com.duckduckgo.cookies.store.FirstPartyCookiePolicyEntity
import com.duckduckgo.cookies.store.toFeatureException
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.config.impl.models.JsonPrivacyConfig
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
@SuppressLint("NoHardcodedCoroutineDispatcher")
class FirstPartyCookiesReferenceTest(private val testCase: TestCase) {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cookieManagerProvider = DefaultCookieManagerProvider()
    private val cookieManager = cookieManagerProvider.get()!!
    private val cookiesRepository = mock<CookiesRepository>()
    private val unprotectedTemporary = mock<UnprotectedTemporary>()
    private val userAllowListRepository = mock<UserAllowListRepository>()
    private val fireproofRepository = mock<FireproofRepository>()
    private val webViewDatabaseLocator = WebViewDatabaseLocator(context)
    private lateinit var cookieModifier: FirstPartyCookiesModifier

    companion object {
        private val acceptedValues = listOf("foo", "max-age", "expires")
        private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)

        @JvmStatic
        @Parameterized.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val referenceTest = adapter.fromJson(
                FileUtilities.loadText(
                    FirstPartyCookiesReferenceTest::class.java.classLoader!!,
                    "reference_tests/firstpartycookies/tests.json",
                ),
            )
            return referenceTest?.expireFirstPartyTrackingCookies?.tests?.filterNot { it.exceptPlatforms.contains("android-browser") } ?: emptyList()
        }
    }

    @Before
    fun before() {
        mockPrivacyConfig()

        cookieModifier = RealFirstPartyCookiesModifier(
            cookiesRepository,
            unprotectedTemporary,
            userAllowListRepository,
            webViewDatabaseLocator,
            mock(),
            fireproofRepository,
            DefaultDispatcherProvider(),
        )
        val host = testCase.siteURL.toUri().host

        val cookie: String = testCase.setDocumentCookie.split(";").mapNotNull { parameter ->
            val key = parameter.trim().split("=").first().lowercase(Locale.getDefault())
            if (acceptedValues.contains(key)) parameter else null
        }.joinToString(";")

        cookieManager.setCookie(host, cookie)
        cookieManager.flush()
    }

    @After
    fun after() = runTest {
        removeExistingCookies()
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() = runTest {
        if (Build.VERSION.SDK_INT == 28) {
            // these tests fail on API 28 due to WAL. This effectively skips these tests on 28.
            return@runTest
        }

        withContext(Dispatchers.Main) {
            val expectedValue: Long = (
                (
                    Instant.now()
                        .plus(testCase.expectExpiryToBe.toLong(), ChronoUnit.SECONDS)
                        .atOffset(ZoneOffset.UTC)
                        .toEpochSecond() * RealFirstPartyCookiesModifierTest.MULTIPLIER
                    ) + RealFirstPartyCookiesModifierTest.TIME_1601_IN_MICRO
                ) * RealFirstPartyCookiesModifierTest.MULTIPLIER

            val host = testCase.siteURL.toUri().host
            cookieModifier.expireFirstPartyCookies()

            openReadableDatabase(webViewDatabaseLocator.getDatabasePath())?.apply {
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
                        if (testCase.expectCookieSet) {
                            assertTrue(cursor.count == 1)
                            cursor.moveToFirst()
                            val value = cursor.getLong(cursor.getColumnIndex("expires_utc"))
                            val diff = (value - expectedValue) / 1000000 // initially in microseconds
                            if (testCase.expectExpiryToBe < 0) {
                                assertTrue(diff < 0L)
                            } else {
                                assertTrue(diff > -5L && diff < 5L) // Diff within +- 5 seconds
                            }
                            assertNotNull(cookieManager.getCookie(host))
                        } else {
                            assertTrue(cursor.count == 0)
                            assertNull(cookieManager.getCookie(host))
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

    private suspend fun removeExistingCookies() {
        withContext(Dispatchers.Main) {
            suspendCoroutine { continuation ->
                cookieManager.removeAllCookies { continuation.resume(Unit) }
            }
        }
    }

    private fun mockPrivacyConfig() {
        val cookieExceptions = mutableListOf<FeatureException>()
        val jsonAdapter: JsonAdapter<JsonPrivacyConfig> = moshi.adapter(JsonPrivacyConfig::class.java)
        val config: JsonPrivacyConfig? = jsonAdapter.fromJson(
            FileUtilities.loadText(
                javaClass.classLoader!!,
                "reference_tests/firstpartycookies/config_reference.json",
            ),
        )
        val cookieAdapter: JsonAdapter<CookiesFeature> = moshi.adapter(CookiesFeature::class.java)
        val cookieFeature: CookiesFeature? = cookieAdapter.fromJson(config?.features?.get("cookie").toString())

        cookieFeature?.exceptions?.map {
            cookieExceptions.add(CookieExceptionEntity(it.domain, it.reason.orEmpty()).toFeatureException())
        }

        val policy = FirstPartyCookiePolicyEntity(
            threshold = cookieFeature!!.settings.firstPartyCookiePolicy.threshold,
            maxAge = cookieFeature.settings.firstPartyCookiePolicy.maxAge,
        )

        val unprotectedTemporaryExceptions = config?.unprotectedTemporary

        whenever(cookiesRepository.exceptions).thenReturn(CopyOnWriteArrayList(cookieExceptions))
        whenever(cookiesRepository.firstPartyCookiePolicy).thenReturn(policy)
        whenever(unprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(unprotectedTemporaryExceptions)
        whenever(fireproofRepository.fireproofWebsites()).thenReturn(emptyList())
    }

    data class TestCase(
        val name: String,
        val siteURL: String,
        val setDocumentCookie: String,
        val expectCookieSet: Boolean,
        val expectExpiryToBe: Int,
        val exceptPlatforms: List<String>,
    )

    data class ExpireFirstPartyCookiesTest(
        val name: String,
        val desc: String,
        val tests: List<TestCase>,
    )

    data class ReferenceTest(
        val expireFirstPartyTrackingCookies: ExpireFirstPartyCookiesTest,
    )
}
