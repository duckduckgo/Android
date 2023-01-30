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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.ATTR_TEXT_ALL_CAPS
import com.android.SdkConstants.ATTR_TEXT_APPEARANCE
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.SdkConstants.ATTR_TEXT_STYLE
import com.android.SdkConstants.ATTR_TINT
import com.android.SdkConstants.VIEW
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class SkeletonViewBackgroundDetector : LayoutDetector() {

    override fun getApplicableElements() = listOf(SKELETON_VIEW)

    override fun visitElement(
        context: XmlContext,
        element: Element
    ) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)){
            reportIssue(context, element, ATTR_BACKGROUND)
        }
    }
    private fun reportIssue(context: XmlContext, element: Element, property: String){
        context.report(
            issue = INVALID_SKELETON_VIEW_BACKGROUND,
            location = context.getNameLocation(element),
            message = property + " " + INVALID_SKELETON_VIEW_BACKGROUND.getExplanation(TextFormat.RAW)
        )
    }

    companion object {

        private const val SKELETON_VIEW = "com.duckduckgo.mobile.android.ui.view.SkeletonView"

        val INVALID_SKELETON_VIEW_BACKGROUND = Issue
            .create(
                id = "InvalidSkeletonViewBackground",
                briefDescription = "Skeleton View already has an internal background defined",
                explanation = "Skeleton View already has an internal background defined",
                moreInfo = "https://app.asana.com/0/1202857801505092/1202928695963077",
                category = CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    SkeletonViewBackgroundDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE
                )
            )
    }
}

