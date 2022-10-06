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
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
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

class MissingDividerDetector : LayoutDetector() {

    override fun getApplicableElements() = listOf(VIEW)

    override fun visitElement(
        context: XmlContext,
        element: Element
    ) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)){
            val heightNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)
            if (heightNode.value == "1dp"){
                context.report(
                    issue = MISSING_HORIZONTAL_DIVIDER,
                    location = context.getNameLocation(element),
                    message = MISSING_HORIZONTAL_DIVIDER.getExplanation(TextFormat.RAW)
                )
            }
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)){
            val widthNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
            if (widthNode.value == "1dp"){
                context.report(
                    issue = MISSING_VERTICAL_DIVIDER,
                    location = context.getNameLocation(element),
                    message = MISSING_VERTICAL_DIVIDER.getExplanation(TextFormat.RAW)
                )
            }
        }
    }

    companion object {

        val MISSING_VERTICAL_DIVIDER = Issue
            .create(
                id = "MissingVerticalDivider",
                briefDescription = "View used instead of [VerticalDivider] Component from the Design System",
                explanation = "1dp width used in a View. Please, use the [VerticalDivider] Component from the Design System",
                moreInfo = "https://app.asana.com/0/1202857801505092/1203028257237192",
                category = CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    MissingDividerDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE
                )
            )

        val MISSING_HORIZONTAL_DIVIDER = Issue
            .create(
                id = "MissingHorizontalDivider",
                briefDescription = "View used instead of [HorizontalDivider] Component from the Design System",
                explanation = "1dp height used in a View. Please, use the [HorizontalDivider] Component from the Design System",
                moreInfo = "https://app.asana.com/0/1202857801505092/1203028257237192",
                category = CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    MissingDividerDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE
                )
            )
    }
}

