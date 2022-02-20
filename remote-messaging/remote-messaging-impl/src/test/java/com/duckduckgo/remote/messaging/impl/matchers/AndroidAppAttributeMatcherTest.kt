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

package com.duckduckgo.remote.messaging.impl.matchers

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.appbuildconfig.api.BuildFlavor.INTERNAL
import com.duckduckgo.browser.api.AppProperties
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAppAttributeMatcherTest {

    private val appProperties: AppProperties = mock()
    private val appBuildConfig: AppBuildConfig = mock()

    private val testee = AndroidAppAttributeMatcher(appProperties, appBuildConfig)

    @Test
    fun whenFlavorMatchesThenReturnMatch() {
        givenDeviceProperties(flavor = INTERNAL)

        val result = testee.evaluate(
            MatchingAttribute.Flavor(value = listOf("internal"))
        )

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenFlavorDoesNotMatchThenReturnFail() {
        givenDeviceProperties(flavor = INTERNAL)

        val result = testee.evaluate(
            MatchingAttribute.Flavor(value = listOf("play"))
        )

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenAppIdMatchesThenReturnMatch() {
        givenDeviceProperties(appId = "com.duckduckgo.mobile.android")

        val result = testee.evaluate(
            MatchingAttribute.AppId(value = "com.duckduckgo.mobile.android")
        )

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenAppIdDoesNotMatchThenReturnFail() {
        givenDeviceProperties(appId = "com.duckduckgo.mobile.android")

        val result = testee.evaluate(
            MatchingAttribute.AppId(value = "com.duckduckgo.mobile.android.debug")
        )

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenAppVersionEqualOrLowerThanMaxThenReturnMatch() {
        givenDeviceProperties(appVersion = "5.100.0")

        val result = testee.evaluate(
            MatchingAttribute.AppVersion(max = "5.100.0")
        )

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenAppVersionGreaterThanMaxThenReturnFail() {
        givenDeviceProperties(appVersion = "5.100.0")

        val result = testee.evaluate(
            MatchingAttribute.AppVersion(max = "5.99.0")
        )

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenAppVersionEqualOrGreaterThanMinThenReturnMatch() {
        givenDeviceProperties(appVersion = "5.100.0")

        val result = testee.evaluate(
            MatchingAttribute.AppVersion(min = "5.100.0")
        )

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenAppVersionLowerThanMinThenReturnFail() {
        givenDeviceProperties(appVersion = "5.99.0")

        val result = testee.evaluate(
            MatchingAttribute.AppVersion(min = "5.100.0")
        )

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenAppVersionInRangeThenReturnMatch() {
        givenDeviceProperties(appVersion = "5.150.0")

        val result = testee.evaluate(
            MatchingAttribute.AppVersion(min = "5.99.0", max = "5.200.0")
        )

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenAppVersionNotInRangeThenReturnMatch() {
        givenDeviceProperties(appVersion = "5.000.0")

        val result = testee.evaluate(
            MatchingAttribute.AppVersion(min = "5.100.0", max = "5.200.0")
        )

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenAppVersionSameAsDeviceThenReturnMatch() {
        givenDeviceProperties(appVersion = "5.100.0")

        val result = testee.evaluate(
            MatchingAttribute.AppVersion(value = "5.100.0")
        )

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenAppVersionDifferentToDeviceThenReturnFail() {
        givenDeviceProperties(appVersion = "5.99.0")

        val result = testee.evaluate(
            MatchingAttribute.AppVersion(value = "5.100.0")
        )

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenAtbMatchesThenReturnMatch() {
        givenDeviceProperties(atb = "v105-2")

        val result = testee.evaluate(
            MatchingAttribute.Atb(value = "v105-2")
        )

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenAtbDoesNotMatchThenReturnFail() {
        givenDeviceProperties(atb = "v105-2")

        val result = testee.evaluate(
            MatchingAttribute.Atb(value = "v105-0")
        )

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenAppAtbMatchesThenReturnMatch() {
        givenDeviceProperties(appAtb = "v105-2")

        val result = testee.evaluate(
            MatchingAttribute.AppAtb(value = "v105-2")
        )

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenAppAtbDoesNotMatchThenReturnFail() {
        givenDeviceProperties(appAtb = "v105-2")

        val result = testee.evaluate(
            MatchingAttribute.AppAtb(value = "v105-0")
        )

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenSearchAtbMatchesThenReturnMatch() {
        givenDeviceProperties(searchAtb = "v105-2")

        val result = testee.evaluate(
            MatchingAttribute.SearchAtb(value = "v105-2")
        )

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenSearchAtbDoesNotMatchThenReturnFail() {
        givenDeviceProperties(searchAtb = "v105-2")

        val result = testee.evaluate(
            MatchingAttribute.SearchAtb(value = "v105-0")
        )

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenExpVariantMatchesThenReturnMatch() {
        givenDeviceProperties(expVariant = "zo")

        val result = testee.evaluate(
            MatchingAttribute.ExpVariant(value = "zo")
        )

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenExpVariantDoesNotMatchThenReturnFail() {
        givenDeviceProperties(expVariant = "zo")

        val result = testee.evaluate(
            MatchingAttribute.ExpVariant(value = "zz")
        )

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenInstalledGPlayMatchesThenReturnMatch() {
        givenDeviceProperties(installedGPlay = true)

        val result = testee.evaluate(
            MatchingAttribute.InstalledGPlay(value = true)
        )

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenInstalledGPlayDoesNotMatchThenReturnFail() {
        givenDeviceProperties(installedGPlay = false)

        val result = testee.evaluate(
            MatchingAttribute.InstalledGPlay(value = true)
        )

        assertEquals(EvaluationResult.Fail, result)
    }

    private fun givenDeviceProperties(
        flavor: BuildFlavor = BuildFlavor.PLAY,
        appId: String = "com.duckduckgo.mobile.android.debug",
        appVersion: String = "5.106.0",
        atb: String = "v105-2",
        appAtb: String = "v105-2",
        searchAtb: String = "v105-2",
        expVariant: String = "zo",
        installedGPlay: Boolean = true
    ) {
        whenever(appBuildConfig.flavor).thenReturn(flavor)
        whenever(appBuildConfig.applicationId).thenReturn(appId)
        whenever(appBuildConfig.versionName).thenReturn(appVersion)
        whenever(appProperties.atb()).thenReturn(atb)
        whenever(appProperties.appAtb()).thenReturn(appAtb)
        whenever(appProperties.searchAtb()).thenReturn(searchAtb)
        whenever(appProperties.expVariant()).thenReturn(expVariant)
        whenever(appProperties.installedGPlay()).thenReturn(installedGPlay)
    }

}
