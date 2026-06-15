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
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParameterForArgument
import org.jetbrains.uast.toUElement
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class DaxDividerColorUsageDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = DaxDividerColorCallHandler(context)

    internal class DaxDividerColorCallHandler(private val context: JavaContext) : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            val methodName = node.methodName
            if (methodName != "DaxHorizontalDivider" && methodName != "DaxVerticalDivider") return

            val resolvedMethod = node.resolve() ?: return
            val pkg = context.evaluator.getPackage(resolvedMethod)?.qualifiedName
            if (pkg != DAX_DIVIDER_PACKAGE) return

            checkColorParameter(node)
        }

        private fun checkColorParameter(node: UCallExpression) {
            val colorArgument = node.valueArguments.find { arg ->
                node.getParameterForArgument(arg)?.name == "color"
            } ?: return

            if (!resolvesIntoThemePackage(colorArgument, depth = 0, visited = mutableSetOf())) {
                context.report(
                    issue = INVALID_DAX_DIVIDER_COLOR_USAGE,
                    location = context.getLocation(colorArgument),
                    message = INVALID_DAX_DIVIDER_COLOR_USAGE.getExplanation(TextFormat.RAW),
                )
            }
        }

        private fun resolvesIntoThemePackage(
            expression: UExpression,
            depth: Int,
            visited: MutableSet<PsiElement>,
        ): Boolean {
            if (depth > MAX_VALIDATION_DEPTH) return false

            val resolved = resolveExpression(expression) ?: return false
            if (!visited.add(resolved)) return false

            if (isThemePackageElement(resolved)) return true

            val body = bodyExpressionOf(resolved) ?: return false
            return resolvesIntoThemePackage(body, depth + 1, visited)
        }

        private fun resolveExpression(expression: UExpression): PsiElement? {
            return when (expression) {
                is UReferenceExpression -> expression.resolve()
                is UCallExpression -> expression.resolve()
                else -> null
            }
        }

        private fun isThemePackageElement(element: PsiElement): Boolean {
            val packageName = context.evaluator.getPackage(element)?.qualifiedName ?: return false
            return packageName == THEME_PACKAGE || packageName.startsWith("$THEME_PACKAGE.")
        }

        private fun bodyExpressionOf(element: PsiElement): UExpression? {
            return when (val u = element.toUElement()) {
                is UVariable -> u.uastInitializer
                is UMethod -> singleExpressionBody(u.uastBody)
                else -> null
            }
        }

        private fun singleExpressionBody(body: UExpression?): UExpression? {
            return when (body) {
                null -> null
                is UReturnExpression -> body.returnExpression
                is UBlockExpression -> {
                    val single = body.expressions.singleOrNull() ?: return null
                    if (single is UReturnExpression) single.returnExpression else single
                }
                else -> body
            }
        }
    }

    companion object {
        private const val THEME_PACKAGE = "com.duckduckgo.common.ui.compose.theme"
        private const val DAX_DIVIDER_PACKAGE = "com.duckduckgo.common.ui.compose.divider"
        private const val MAX_VALIDATION_DEPTH = 4

        val INVALID_DAX_DIVIDER_COLOR_USAGE = Issue
            .create(
                id = "InvalidDaxDividerColorUsage",
                briefDescription = "DaxHorizontalDivider/DaxVerticalDivider color parameter should use DuckDuckGoTheme semantic colors",
                explanation = """
                    Use a DuckDuckGoTheme semantic color (e.g. DuckDuckGoTheme.colors.system.lines for horizontal dividers, DuckDuckGoTheme.colors.backgrounds.container for vertical dividers) instead of arbitrary Color values.

                    Defaults classes are allowed only when their implementation resolves to DuckDuckGoTheme semantic colors or theme-defined static colors.

                    Examples:
                    • DuckDuckGoTheme.colors.system.lines
                    • DuckDuckGoTheme.colors.backgrounds.container
                    • theme.colors.brand.accentBlue

                    For one-off cases requiring custom colors, use good judgement or consider raising it in the Android Design System AOR.
                """.trimIndent(),
                moreInfo = "",
                category = CUSTOM_LINT_CHECKS,
                priority = 6,
                severity = Severity.WARNING,
                androidSpecific = true,
                implementation = Implementation(
                    DaxDividerColorUsageDetector::class.java,
                    EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                ),
            )
    }
}
