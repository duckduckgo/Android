/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui

import com.duckduckgo.navigation.api.GlobalActivityStarter
import java.io.Serializable

/**
 * Model that represents the VPN Report Breakage Screen
 *
 * @param launchFrom string that identifies the origin that launches the vpn breakage report screen
 * @param breakageCategories list of breakage categories you'd like to be displayed in the breakage form to be filled by the user
 */
data class OpenVpnReportBreakageFrom(val launchFrom: String, val breakageCategories: List<AppBreakageCategory>) : GlobalActivityStarter.ActivityParams

/**
 * Model that represents the VPN Report Breakage Category Screen
 *
 * @param launchFrom string that identifies the origin that launches the vpn breakage report screen
 * @param breakageCategories list of breakage categories you'd like to be displayed in the breakage form to be filled by the user
 * @param appName is the name of the app the user reported as broken
 * @param appPackageId is the package ID of the app the user reported as broken
 */
data class OpenVpnBreakageCategoryWithBrokenApp(
    val launchFrom: String,
    val appName: String,
    val appPackageId: String,
    val breakageCategories: List<AppBreakageCategory>,
) : GlobalActivityStarter.ActivityParams

/**
 * @param key category key (short name)
 * @param description localized string with the category description that the user will see
 */
data class AppBreakageCategory(val key: String, val description: String) : Serializable
