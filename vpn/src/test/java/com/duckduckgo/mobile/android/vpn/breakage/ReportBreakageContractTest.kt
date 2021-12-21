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

package com.duckduckgo.mobile.android.vpn.breakage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportBreakageContractTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun whenCreateIntentForIssueDescriptionFormThenReturnCorrectIntent() {
        val intent =
            ReportBreakageContract()
                .createIntent(
                    context, ReportBreakageScreen.IssueDescriptionForm("myApp", "my.package.com"))
        assertEquals(
            ReportBreakageTextFormActivity::class.java.canonicalName, intent.component?.className)
    }

    @Test
    fun whenCreateIntentForListOfInstalledAppsThenReturnCorrectIntent() {
        val intent =
            ReportBreakageContract().createIntent(context, ReportBreakageScreen.ListOfInstalledApps)
        assertEquals(
            ReportBreakageAppListActivity::class.java.canonicalName, intent.component?.className)
    }

    @Test
    fun whenCreateIntentForLoginInformationThenReturnCorrectIntent() {
        val intent =
            ReportBreakageContract()
                .createIntent(
                    context, ReportBreakageScreen.LoginInformation("myApp", "my.package.com"))
        assertEquals(
            ReportBreakageSingleChoiceFormActivity::class.java.canonicalName,
            intent.component?.className)
    }
}
