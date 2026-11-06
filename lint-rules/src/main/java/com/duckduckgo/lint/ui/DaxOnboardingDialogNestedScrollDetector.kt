/*
 * Copyright (c) 2026 DuckDuckGo
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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getParameterForArgument
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class DaxOnboardingDialogNestedScrollDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = Handler(context)

    internal class Handler(private val context: JavaContext) : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            if (node.methodName !in DIALOG_COMPOSABLES) return
            val content = node.valueArguments.find { node.getParameterForArgument(it)?.name == "content" } ?: return
            content.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    if (node.methodName in VERTICAL_SCROLLERS) report(node)
                    return false
                }
            })
        }

        private fun report(node: UCallExpression) {
            context.report(
                issue = NESTED_SCROLL_IN_DAX_ONBOARDING_DIALOG,
                location = context.getCallLocation(node, includeReceiver = false, includeArguments = false),
                message = NESTED_SCROLL_IN_DAX_ONBOARDING_DIALOG.getExplanation(TextFormat.RAW),
            )
        }
    }

    companion object {
        private val DIALOG_COMPOSABLES = setOf("DaxOnboardingDialog", "DaxContextualOnboardingDialog")
        private val VERTICAL_SCROLLERS = setOf("LazyColumn", "verticalScroll")

        val NESTED_SCROLL_IN_DAX_ONBOARDING_DIALOG: Issue = Issue
            .create(
                id = "NestedScrollInDaxOnboardingDialog",
                briefDescription = "Onboarding dialog content should not add a vertical scroller",
                explanation = """
                    DaxOnboardingDialog and DaxContextualOnboardingDialog already scroll their content vertically.
                    Remove the LazyColumn or Modifier.verticalScroll from the content slot.
                """.trimIndent(),
                moreInfo = "",
                category = CUSTOM_LINT_CHECKS,
                priority = 6,
                severity = Severity.WARNING,
                androidSpecific = true,
                implementation = Implementation(
                    DaxOnboardingDialogNestedScrollDetector::class.java,
                    EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                ),
            )
    }
}
