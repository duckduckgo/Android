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

package com.duckduckgo.lint.ui

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_STYLE
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import com.android.utils.iterator
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class WrongStyleDetector: ResourceXmlDetector() {
    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val documentElement = document.documentElement
        val root = documentElement ?: return

        if (ATTR_STYLE == root.tagName) {
            val value = root.getAttribute(ATTR_NAME)
            if (!value.startsWith(PREFIX_DDG_STYLE)){
                reportWrongStyleNameIssue(context, root)
            }
            checkSizeParameters(context, root)
        }
    }

    private fun checkSizeParameters(context: XmlContext, parameters: Element) {
        for (parameter in parameters) {
            if (parameter.attributes.getNamedItem(ATTR_NAME) != null){
                val attribute = parameter.attributes.getNamedItem(ATTR_NAME)
                if (attribute.nodeValue == "android:layout_width"){
                    reportWrongStyleParameterIssue(context, parameter)
                }
                if (attribute.nodeValue == "android:layout_height"){
                    reportWrongStyleParameterIssue(context, parameter)
                }
            }
        }
    }

    private fun reportWrongStyleNameIssue(
        context: XmlContext,
        element: Element
    ) {
        context.report(
            issue = WRONG_STYLE_NAME,
            location = context.getNameLocation(element),
            message = WRONG_STYLE_NAME.getExplanation(TextFormat.RAW),
        )
    }

    private fun reportWrongStyleParameterIssue(
        context: XmlContext,
        element: Element
    ) {
        context.report(
            issue = WRONG_STYLE_PARAMETER,
            location = context.getNameLocation(element),
            message = WRONG_STYLE_PARAMETER.getExplanation(TextFormat.RAW),
        )
    }

    companion object {
        const val PREFIX_DDG_STYLE = "Widget.DuckDuckGo."

        val WRONG_STYLE_NAME = Issue
            .create(
                id = "WrongStyleName",
                briefDescription = "Style names should follow the convention and start with Widget.DuckDuckGo.",
                explanation = "Style names should follow the convention and start with Widget.DuckDuckGo.",
                moreInfo = "https://app.asana.com/0/1202857801505092/list",
                category = Category.CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    WrongStyleDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE,
                ),
            )

        val WRONG_STYLE_PARAMETER = Issue
            .create(
                id = "WrongStyleParameter",
                briefDescription = "Styles should not modify android:layout_height or android:layout_width",
                explanation = "Styles should not modify android:layout_height or android:layout_width",
                moreInfo = "https://app.asana.com/0/1202857801505092/list",
                category = Category.CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    WrongStyleDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE,
                ),
            )
    }


}
