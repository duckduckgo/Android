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

class DaxButtonStylingDetector : LayoutDetector() {

    override fun getApplicableElements() = DAX_BUTTONS

    override fun visitElement(
        context: XmlContext,
        element: Element
    ) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)){
            val heightNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)
            if (heightNode.value != "wrap_content"){
                reportIssue(context, element, ATTR_LAYOUT_HEIGHT)
            }
        }

        if (element.hasAttribute(ATTR_STYLE)){
            reportIssue(context, element, ATTR_STYLE)
        }

        checkInvalidAttributes(context, element)
    }

    private fun checkInvalidAttributes(context: XmlContext, element: Element){
        INVALID_ATTRIBUTES.forEach {
            if (checkAttribute(element, it)){
                reportIssue(context, element, it)
            }
        }
    }

    private fun checkAttribute(element: Element, property: String): Boolean {
        return (element.hasAttributeNS(ANDROID_URI, property))
    }

    private fun reportIssue(context: XmlContext, element: Element, property: String){
        context.report(
            issue = INVALID_DAX_BUTTON_PROPERTY,
            location = context.getNameLocation(element),
            message = property + " " + INVALID_DAX_BUTTON_PROPERTY.getExplanation(TextFormat.RAW)
        )
    }

    companion object {

        private const val DAX_BUTTON_PRIMARY = "com.duckduckgo.common.ui.view.button.DaxButtonPrimary"
        private const val DAX_BUTTON_SECONDARY = "com.duckduckgo.common.ui.view.button.DaxButtonSecondary"
        private const val DAX_BUTTON_GHOST = "com.duckduckgo.common.ui.view.button.DaxButtonGhost"
        private const val DAX_BUTTON_DESTRUCTIVE = "com.duckduckgo.common.ui.view.button.DaxButtonDestructive"
        private const val DAX_BUTTON_GHOST_DESTRUCTIVE = "com.duckduckgo.common.ui.view.button.DaxButtonGhostDestructive"
        private const val DAX_BUTTON_DESTRUCTIVE_SECONDARY = "com.duckduckgo.common.ui.view.button.DaxButtonDestructiveSecondary"

        val DAX_BUTTONS = listOf(DAX_BUTTON_PRIMARY, DAX_BUTTON_DESTRUCTIVE, DAX_BUTTON_SECONDARY, DAX_BUTTON_GHOST, DAX_BUTTON_GHOST_DESTRUCTIVE, DAX_BUTTON_DESTRUCTIVE_SECONDARY)

        val INVALID_ATTRIBUTES = listOf(ATTR_TEXT_STYLE, ATTR_TEXT_COLOR, ATTR_TEXT_APPEARANCE, ATTR_TEXT_ALL_CAPS, ATTR_TINT)

        val INVALID_DAX_BUTTON_PROPERTY = Issue
            .create(
                id = "InvalidDaxButtonProperty",
                briefDescription = "Property change not valid in DaxButton",
                explanation = " is defined by the DaxButton Component, you shouldn't change it",
                moreInfo = "https://app.asana.com/0/1202857801505092/1202928695963077",
                category = CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    DaxButtonStylingDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE
                )
            )
    }
}

