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

package com.duckduckgo.lint.registry

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.duckduckgo.lint.strings.MissingSmartlingRequiredDirectivesDetector.Companion.MISSING_SMARTLING_REQUIRED_DIRECTIVES
import com.duckduckgo.lint.NoFragmentDetector.Companion.NO_FRAGMENT_ISSUE
import com.duckduckgo.lint.NoHardcodedCoroutineDispatcherDetector.Companion.NO_HARCODED_COROUTINE_DISPATCHER
import com.duckduckgo.lint.NoLifecycleObserverDetector.Companion.NO_LIFECYCLE_OBSERVER_ISSUE
import com.duckduckgo.lint.NoRetrofitCreateMethodCallDetector.Companion.NO_RETROFIT_CREATE_CALL
import com.duckduckgo.lint.NoSingletonDetector.Companion.NO_SINGLETON_ISSUE
import com.duckduckgo.lint.NoSystemLoadLibraryDetector.Companion.NO_SYSTEM_LOAD_LIBRARY
import com.duckduckgo.lint.strings.MissingInstructionDetector.Companion.MISSING_INSTRUCTION
import com.duckduckgo.lint.strings.PlaceholderDetector.Companion.PLACEHOLDER_MISSING_POSITION
import com.duckduckgo.lint.ui.DaxTextViewStylingDetector.Companion.INVALID_DAX_TEXT_VIEW_PROPERTY
import com.duckduckgo.lint.ui.DeprecatedAndroidButtonUsedInXmlDetector.Companion.DEPRECATED_BUTTON_IN_XML
import com.duckduckgo.lint.ui.DeprecatedSwitchUsedInXmlDetector.Companion.DEPRECATED_SWITCH_IN_XML
import com.duckduckgo.lint.ui.MissingDividerDetector.Companion.MISSING_HORIZONTAL_DIVIDER
import com.duckduckgo.lint.ui.MissingDividerDetector.Companion.MISSING_VERTICAL_DIVIDER
import com.duckduckgo.lint.ui.NoAlertDialogDetector.Companion.NO_DESIGN_SYSTEM_DIALOG
import com.duckduckgo.lint.ui.NoBottomSheetDialogDetector.Companion.NO_BOTTOM_SHEET
import com.duckduckgo.lint.ui.NoStyleAppliedToDesignSystemComponentDetector.Companion.STYLE_IN_DESIGN_SYSTEM_COMPONENT

@Suppress("UnstableApiUsage")
class DuckDuckGoIssueRegistry : IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(
            NO_SINGLETON_ISSUE,
            NO_LIFECYCLE_OBSERVER_ISSUE,
            NO_FRAGMENT_ISSUE,
            NO_SYSTEM_LOAD_LIBRARY,
            NO_HARCODED_COROUTINE_DISPATCHER,
            MISSING_SMARTLING_REQUIRED_DIRECTIVES,
            MISSING_INSTRUCTION,
            PLACEHOLDER_MISSING_POSITION,
            NO_RETROFIT_CREATE_CALL,

            // Android Design System
            DEPRECATED_BUTTON_IN_XML,
            STYLE_IN_DESIGN_SYSTEM_COMPONENT,
            NO_DESIGN_SYSTEM_DIALOG,
            DEPRECATED_SWITCH_IN_XML,
            MISSING_VERTICAL_DIVIDER,
            MISSING_HORIZONTAL_DIVIDER,
            NO_BOTTOM_SHEET,
            INVALID_DAX_TEXT_VIEW_PROPERTY,
        )

    override val api: Int
        get() = CURRENT_API

    override val vendor = Vendor(
        vendorName = "DuckDuckGo",
        identifier = "com.duckduckgo:lint",
        feedbackUrl = "https://github.com/duckduckgo/android/issues",
        contact = "https://github.com/duckduckgo/android"
    )
}
