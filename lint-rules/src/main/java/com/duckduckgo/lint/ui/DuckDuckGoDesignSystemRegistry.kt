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

package com.duckduckgo.lint.ui

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.duckduckgo.lint.ui.DeprecatedAndroidButtonUsedInXmlDetector.Companion.DEPRECATED_BUTTON_IN_XML
import com.duckduckgo.lint.ui.DeprecatedSwitchUsedInXmlDetector.Companion.DEPRECATED_SWITCH_IN_XML
import com.duckduckgo.lint.ui.MissingDividerDetector.Companion.MISSING_HORIZONTAL_DIVIDER
import com.duckduckgo.lint.ui.MissingDividerDetector.Companion.MISSING_VERTICAL_DIVIDER
import com.duckduckgo.lint.ui.NoAlertDialogDetector.Companion.NO_DESIGN_SYSTEM_DIALOG
import com.duckduckgo.lint.ui.NoStyleAppliedToDesignSystemComponentDetector.Companion.STYLE_IN_DESIGN_SYSTEM_COMPONENT

@Suppress("UnstableApiUsage")
class DuckDuckGoDesignSystemRegistry : IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(
            DEPRECATED_BUTTON_IN_XML,
            STYLE_IN_DESIGN_SYSTEM_COMPONENT,
            NO_DESIGN_SYSTEM_DIALOG,
            DEPRECATED_SWITCH_IN_XML,
            MISSING_VERTICAL_DIVIDER,
            MISSING_HORIZONTAL_DIVIDER
        )

    override val api: Int
        get() = CURRENT_API

    override val vendor = Vendor(
        vendorName = "DuckDuckGo Design System",
        identifier = "com.duckduckgo:lint:ui",
        feedbackUrl = "https://app.asana.com/0/1202857801505092/1202858029631618",
        contact = "https://github.com/duckduckgo/android"
    )
}

