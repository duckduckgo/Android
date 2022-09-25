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

data class InstalledApp(
    val packageName: String,
    val name: String,
    val isSelected: Boolean = false
)

object ReportBreakageAppListView {
    data class State(
        val installedApps: List<InstalledApp>,
        val canSubmit: Boolean,
    )

    sealed class Command {
        data class LaunchBreakageForm(val selectedApp: InstalledApp) : Command()
        data class SendBreakageInfo(val issueReport: IssueReport) : Command()
    }
}
