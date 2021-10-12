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
import kotlinx.android.parcel.Parcelize

class ReportBreakageContract : ActivityResultContract<ReportBreakageScreen, IssueReport>() {

    override fun createIntent(context: Context, input: ReportBreakageScreen?): Intent {
        return when (input) {
            ReportBreakageScreen.ListOfInstalledApps -> ReportBreakageAppListActivity.intent(context)
            is ReportBreakageScreen.IssueDescriptionForm -> ReportBreakageTextFormActivity.intent(context, input.appPackageId)
            is ReportBreakageScreen.LoginInformation -> ReportBreakageSingleChoiceFormActivity.intent(context, input.appPackageId)
            null -> throw IllegalStateException("Screen must be specified")
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): IssueReport {
        if (resultCode == RESULT_OK) {
            return intent?.getParcelableExtra<IssueReport>(IssueReport::class.java.simpleName) as IssueReport
        }
        return IssueReport.EMPTY
    }

}

sealed class ReportBreakageScreen {
    object ListOfInstalledApps : ReportBreakageScreen()
    data class IssueDescriptionForm(val appPackageId: String) : ReportBreakageScreen()
    data class LoginInformation(val appPackageId: String) : ReportBreakageScreen()
}

@Parcelize
data class IssueReport(
    val appPackageId: String? = null,
    val description: String? = null,
    val loginInfo: String? = null,
    val customMetadata: String? = null,
) : Parcelable {

    fun toMap(): Map<String, String> {
        return mutableMapOf<String, String>().apply {
            appPackageId?.let { this["appPackageId"] = it }
            description?.let { this["breakageDescription"] = it }
            loginInfo?.let { this["breakageLoginInfo"] = it }
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
