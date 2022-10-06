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

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

@Suppress("UnstableApiUsage")
class NoStyleAppliedToDesignSystemComponentDetector : LayoutDetector() {

    override fun getApplicableElements() = DESIGN_COMPONENTS

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun visitElement(
        context: XmlContext,
        element: Element
    ) {
        if (element.hasAttribute("style")) {
            reportIssue(context, element)
        }
    }

    private fun reportIssue(
        context: XmlContext,
        element: Element
    ) {
        context.report(
            issue = STYLE_IN_DESIGN_SYSTEM_COMPONENT,
            location = context.getNameLocation(element),
            message = STYLE_IN_DESIGN_SYSTEM_COMPONENT.getExplanation(TextFormat.RAW)
        )
    }

    companion object {
        private const val BUTTON_PRIMARY_LARGE = "com.duckduckgo.mobile.android.ui.view.button.ButtonPrimaryLarge"
        private const val BUTTON_PRIMARY_SMALL = "com.duckduckgo.mobile.android.ui.view.button.ButtonPrimarySmall"
        private const val BUTTON_SECONDARY_LARGE = "com.duckduckgo.mobile.android.ui.view.button.ButtonSecondaryLarge"
        private const val BUTTON_SECONDARY_SMALL = "com.duckduckgo.mobile.android.ui.view.button.ButtonSecondarySmall"
        private const val BUTTON_GHOST_LARGE = "com.duckduckgo.mobile.android.ui.view.button.ButtonGhostLarge"
        private const val BUTTON_GHOST_SMALL = "com.duckduckgo.mobile.android.ui.view.button.ButtonGhostSmall"
        private const val ONE_LINE_LIST_ITEM = "com.duckduckgo.mobile.android.ui.view.listitem.OneLineListItem"
        private const val SWITCH = "com.duckduckgo.mobile.android.ui.view.SwitchView"

        val DESIGN_COMPONENTS =
            listOf(
                BUTTON_PRIMARY_LARGE,
                BUTTON_PRIMARY_SMALL,
                BUTTON_SECONDARY_LARGE,
                BUTTON_SECONDARY_SMALL,
                BUTTON_GHOST_LARGE,
                BUTTON_GHOST_SMALL,
                ONE_LINE_LIST_ITEM,
                SWITCH
            )

        val STYLE_IN_DESIGN_SYSTEM_COMPONENT = Issue
            .create(
                id = "StyleInDesignSystemComponent",
                briefDescription = "Design System Components should not be styled.",
                explanation = "Design System Components should not be styled. Consider creating a new Component or use one of the Components already created",
                moreInfo = "https://app.asana.com/0/1202857801505092/list",
                category = CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    NoStyleAppliedToDesignSystemComponentDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE
                )
            )
    }
}
