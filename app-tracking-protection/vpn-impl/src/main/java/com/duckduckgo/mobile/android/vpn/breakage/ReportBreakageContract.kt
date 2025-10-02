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

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.duckduckgo.mobile.android.vpn.ui.OpenVpnBreakageCategoryWithBrokenApp
import com.duckduckgo.mobile.android.vpn.ui.OpenVpnReportBreakageFrom
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.android.parcel.Parcelize
import javax.inject.Inject

class ReportBreakageContract @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
) : ActivityResultContract<ReportBreakageScreen, IssueReport>() {

    override fun createIntent(
        context: Context,
        input: ReportBreakageScreen,
    ): Intent {
        return when (input) {
            is ReportBreakageScreen.ListOfInstalledApps -> {
                globalActivityStarter.startIntent(
                    context,
                    OpenVpnReportBreakageFrom(launchFrom = input.origin, breakageCategories = input.breakageCategories),
                )!!
            }
            is ReportBreakageScreen.IssueDescriptionForm -> {
                globalActivityStarter.startIntent(
                    context,
                    OpenVpnBreakageCategoryWithBrokenApp(
                        launchFrom = input.origin,
                        appName = input.appName,
                        appPackageId = input.appPackageId,
                        breakageCategories = input.breakageCategories,
                    ),
                )!!
            }
        }
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): IssueReport {
        if (resultCode == RESULT_OK) {
            return intent?.getParcelableExtra<IssueReport>(IssueReport::class.java.simpleName) as IssueReport
        }
        return IssueReport.EMPTY
    }
}

sealed class ReportBreakageScreen(open val origin: String, open val breakageCategories: List<AppBreakageCategory>) {
    data class ListOfInstalledApps(
        override val origin: String,
        override val breakageCategories: List<AppBreakageCategory>,
    ) : ReportBreakageScreen(origin, breakageCategories)
    data class IssueDescriptionForm(
        override val origin: String,
        override val breakageCategories: List<AppBreakageCategory>,
        val appName: String,
        val appPackageId: String,
    ) : ReportBreakageScreen(origin, breakageCategories)
}

@Parcelize
data class IssueReport(
    val reportedFrom: String? = null,
    val appName: String? = null,
    val appPackageId: String? = null,
    val description: String? = null,
    val category: String? = null,
    val customMetadata: String? = null,
) : Parcelable {

    fun toMap(): Map<String, String> {
        return mutableMapOf<String, String>().apply {
            reportedFrom?.let { this["reportedFrom"] = it }
            appName?.let { this["appName"] = it }
            appPackageId?.let { this["appPackageId"] = it }
            description?.let { this["breakageDescription"] = it }
            category?.let { this["breakageCategory"] = it }
            customMetadata?.let { this["breakageMetadata"] = it }
        }
    }

    fun addToIntent(intent: Intent) {
        intent.putExtra(IssueReport::class.java.simpleName, this)
    }

    fun isEmpty(): Boolean {
        return this == EMPTY
    }

    companion object {
        val EMPTY = IssueReport()
    }
}
