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

package com.duckduckgo.privacy.config.impl.referencetests.privacyconfig

import androidx.room.Room
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.runBlocking
import com.duckduckgo.privacy.config.impl.FileUtilities
import com.duckduckgo.privacy.config.impl.RealPrivacyConfigPersister
import com.duckduckgo.privacy.config.impl.ReferenceTestUtilities
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(ParameterizedRobolectricTestRunner::class)
class PrivacyConfigEnabledReferenceTest(private val testCase: TestCase) {

    @get:Rule var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealPrivacyConfigPersister
    private val mockTogglesRepository: PrivacyFeatureTogglesRepository = mock()

    private lateinit var db: PrivacyConfigDatabase
    private lateinit var referenceTestUtilities: ReferenceTestUtilities

    companion object {
        private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)
        private lateinit var referenceJsonFile: String

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val referenceTest =
                adapter.fromJson(FileUtilities.loadText("reference_tests/privacyconfig/tests.json"))
            referenceJsonFile = referenceTest?.featuresEnabled?.referenceConfig!!
            return referenceTest.featuresEnabled.tests.filterNot {
                it.exceptPlatforms.contains("android-browser")
            }
        }
    }

    @Before
    fun before() {
        prepareDb()
        referenceTestUtilities = ReferenceTestUtilities(db, coroutineRule.testDispatcherProvider)
        testee =
            RealPrivacyConfigPersister(
                referenceTestUtilities.getPrivacyFeaturePluginPoint(),
                mockTogglesRepository,
                referenceTestUtilities.unprotectedTemporaryRepository,
                referenceTestUtilities.privacyRepository,
                db)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() =
        coroutineRule.runBlocking {
            testee.persistPrivacyConfig(
                referenceTestUtilities.getJsonPrivacyConfig(
                    "reference_tests/privacyconfig/$referenceJsonFile"))

            verify(referenceTestUtilities.privacyFeatureTogglesRepository)
                .insert(PrivacyFeatureToggles(testCase.featureName, testCase.expectFeatureEnabled))
        }

    private fun prepareDb() {
        db =
            Room.inMemoryDatabaseBuilder(mock(), PrivacyConfigDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    data class TestCase(
        val name: String,
        val featureName: String,
        val siteURL: String,
        val expectFeatureEnabled: Boolean,
        val exceptPlatforms: List<String>
    )

    data class FeaturesEnabledTest(
        val name: String,
        val desc: String,
        val referenceConfig: String,
        val tests: List<TestCase>
    )

    data class ReferenceTest(val featuresEnabled: FeaturesEnabledTest)
}
