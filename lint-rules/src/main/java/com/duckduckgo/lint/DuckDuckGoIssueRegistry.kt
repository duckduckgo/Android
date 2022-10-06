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

package com.duckduckgo.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.duckduckgo.lint.NoFragmentDetector.Companion.NO_FRAGMENT_ISSUE
import com.duckduckgo.lint.NoLifecycleObserverDetector.Companion.NO_LIFECYCLE_OBSERVER_ISSUE
import com.duckduckgo.lint.NoSingletonDetector.Companion.NO_SINGLETON_ISSUE
import com.duckduckgo.lint.NoSystemLoadLibraryDetector.Companion.NO_SYSTEM_LOAD_LIBRARY
import com.duckduckgo.lint.ui.DeprecatedAndroidButtonUsedInXmlDetector.Companion.DEPRECATED_BUTTON_IN_XML
import com.duckduckgo.lint.ui.DeprecatedSwitchUsedInXmlDetector
import com.duckduckgo.lint.ui.DeprecatedSwitchUsedInXmlDetector.Companion
import com.duckduckgo.lint.ui.NoStyleAppliedToDesignSystemComponentDetector.Companion.STYLE_IN_DESIGN_SYSTEM_COMPONENT

@Suppress("UnstableApiUsage")
class DuckDuckGoIssueRegistry : IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(
            NO_SINGLETON_ISSUE,
            NO_LIFECYCLE_OBSERVER_ISSUE,
            NO_FRAGMENT_ISSUE,
            NO_SYSTEM_LOAD_LIBRARY,
            DeprecatedSwitchUsedInXmlDetector.DEPRECATED_SWITCH_IN_XML,
            DEPRECATED_BUTTON_IN_XML,
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

