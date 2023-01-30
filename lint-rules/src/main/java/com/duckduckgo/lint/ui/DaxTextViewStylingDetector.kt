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
import com.android.SdkConstants.ATTR_FONT
import com.android.SdkConstants.ATTR_FONT_FAMILY
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.ATTR_TEXT_ALL_CAPS
import com.android.SdkConstants.ATTR_TEXT_APPEARANCE
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.SdkConstants.ATTR_TEXT_SIZE
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

class DaxTextViewStylingDetector : LayoutDetector() {

    override fun getApplicableElements() = listOf(DAX_TEXT_VIEW)

    override fun visitElement(
        context: XmlContext,
        element: Element
    ) {
        if (element.hasAttribute(ATTR_STYLE)){
            reportInvalidPropertyIssue(context, element, ATTR_STYLE)
        }

        checkInvalidAttributes(context, element)
    }

    private fun checkInvalidAttributes(context: XmlContext, element: Element){
        INVALID_ATTRIBUTES.forEach {
            if (checkAttribute(element, it)){
                reportInvalidPropertyIssue(context, element, it)
            }
        }
    }

    private fun checkAttribute(element: Element, property: String): Boolean {
        return (element.hasAttributeNS(ANDROID_URI, property))
    }

    private fun reportInvalidPropertyIssue(context: XmlContext, element: Element, property: String){
        context.report(
            issue = INVALID_DAX_TEXT_VIEW_PROPERTY,
            location = context.getNameLocation(element),
            message = property + " " + INVALID_DAX_TEXT_VIEW_PROPERTY.getExplanation(TextFormat.RAW)
        )
    }

    companion object {

        private const val DAX_TEXT_VIEW = "com.duckduckgo.mobile.android.ui.view.text.DaxTextView"

        val INVALID_ATTRIBUTES = listOf(ATTR_TEXT_APPEARANCE,
            ATTR_TEXT_ALL_CAPS, ATTR_TINT, ATTR_TEXT_SIZE, ATTR_TEXT_STYLE, ATTR_FONT_FAMILY)

        val INVALID_DAX_TEXT_VIEW_PROPERTY = Issue
            .create(
                id = "InvalidDaxTextViewProperty",
                briefDescription = "Property change not valid in DaxTextView",
                explanation = " is defined by the DaxTextView Component, you shouldn't change it",
                moreInfo = "https://app.asana.com/0/1202857801505092/1203091197523221",
                category = CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    DaxTextViewStylingDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE
                )
            )
    }
}

