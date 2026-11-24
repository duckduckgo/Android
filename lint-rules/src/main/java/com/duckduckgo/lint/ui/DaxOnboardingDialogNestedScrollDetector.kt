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
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getParameterForArgument
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class DaxOnboardingDialogNestedScrollDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = Handler(context)

    internal class Handler(private val context: JavaContext) : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            if (node.methodName !in DIALOG_COMPOSABLES || node.resolvedPackage() != DIALOG_PACKAGE) return
            for (argument in node.valueArguments) {
                if (node.getParameterForArgument(argument)?.name in SCROLLING_SLOTS) checkSlot(argument)
            }
        }

        private fun checkSlot(argument: UExpression) {
            val slot = ((argument as? UNamedExpression)?.expression ?: argument).skipParenthesizedExprDown()
            val initializer = (slot as? UReferenceExpression)?.localLambdaInitializer()
            (initializer ?: slot).accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val expectedPackage = VERTICAL_SCROLLERS[node.methodName] ?: return false
                    if (node.resolvedPackage() == expectedPackage) report(node)
                    return false
                }
            })
        }

        private fun UCallExpression.resolvedPackage() = resolve()?.let { context.evaluator.getPackage(it)?.qualifiedName }

        private fun UReferenceExpression.localLambdaInitializer(): ULambdaExpression? {
            val variable = resolve()?.toUElementOfType<ULocalVariable>() ?: return null
            val property = variable.sourcePsi as? KtProperty ?: return null
            if (property.isVar) return null
            return variable.uastInitializer?.skipParenthesizedExprDown() as? ULambdaExpression
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
        private const val DIALOG_PACKAGE = "com.duckduckgo.common.ui.compose.onboarding"
        private val DIALOG_COMPOSABLES = setOf("DaxOnboardingDialog", "DaxContextualOnboardingDialog")
        private val SCROLLING_SLOTS = setOf("body", "content")
        private val VERTICAL_SCROLLERS = mapOf(
            "verticalScroll" to "androidx.compose.foundation",
            "LazyColumn" to "androidx.compose.foundation.lazy",
            "LazyVerticalGrid" to "androidx.compose.foundation.lazy.grid",
            "LazyVerticalStaggeredGrid" to "androidx.compose.foundation.lazy.staggeredgrid",
        )

        val NESTED_SCROLL_IN_DAX_ONBOARDING_DIALOG: Issue = Issue
            .create(
                id = "NestedScrollInDaxOnboardingDialog",
                briefDescription = "Onboarding dialog content should not add a vertical scroller",
                explanation = """
                    DaxOnboardingDialog and DaxContextualOnboardingDialog already scroll their body and content vertically.
                    Remove the vertical scroller from the dialog slot.
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
