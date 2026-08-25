/*
 * Copyright (c) 2025 DuckDuckGo
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
import com.intellij.psi.PsiParameter
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParameterForArgument
import org.jetbrains.uast.toUElement
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class DaxListItemColorUsageDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = DaxListItemColorCallHandler(context)

    internal class DaxListItemColorCallHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val methodName = node.methodName

            if (methodName == "DaxOneLineListItem" || methodName == "DaxTwoLineListItem") {
                checkColorParameter(node, "primaryTextColor")
                checkColorParameter(node, "secondaryTextColor")
            }
        }

        private fun checkColorParameter(node: UCallExpression, parameterName: String) {
            val colorArgument = node.valueArguments.find { arg ->
                node.getParameterForArgument(arg)?.name == parameterName
            } ?: return // No such color parameter provided, using default is fine

            if (!isFromValidDuckDuckGoColorSource(colorArgument)) {
                reportInvalidColorUsage(colorArgument)
            }
        }

        private fun isFromValidDuckDuckGoColorSource(argument: UExpression): Boolean {
            val source = argument.sourcePsi?.text.orEmpty()

            // Direct semantic color access on theme.
            if (containsSemanticThemeColorPath(source)) return true

            // Direct reference to static colors in compose theme package.
            if (resolvesToThemePackageElement(argument)) return true

            // A colour forwarded from the enclosing composable's own parameter is the caller's to justify,
            // and the parameter's default is invisible once the declaration is resolved from bytecode.
            if (resolveExpression(argument) is PsiParameter) return true

            // Reference via defaults object/property: validate declaration implementation.
            return resolvesToValidatedColorDeclaration(
                expression = argument,
                depth = 0,
                visited = mutableSetOf(),
            )
        }

        private fun containsSemanticThemeColorPath(source: String): Boolean {
            return source.contains("DuckDuckGoTheme.textColors") || // legacy
                source.contains(".textColors.") ||
                source.contains(".colors.")
        }

        private fun resolvesToThemePackageElement(expression: UExpression): Boolean {
            val resolved = resolveExpression(expression) ?: return false
            return isThemePackageElement(resolved)
        }

        private fun resolveExpression(expression: UExpression): PsiElement? {
            return when (expression) {
                is UQualifiedReferenceExpression -> expression.resolve()
                is UReferenceExpression -> expression.resolve()
                else -> null
            }
        }

        private fun isThemePackageElement(element: PsiElement): Boolean {
            // Resolve the package rather than a containing class: a theme colour declared as a data-class
            // constructor `val` resolves to a parameter, which has no containing class.
            val packageName = context.evaluator.getPackage(element)?.qualifiedName ?: return false
            return packageName == COLOR_THEME_PACKAGE || packageName.startsWith("$COLOR_THEME_PACKAGE.")
        }

        private fun resolvesToValidatedColorDeclaration(
            expression: UExpression,
            depth: Int,
            visited: MutableSet<PsiElement>,
        ): Boolean {
            if (depth > MAX_VALIDATION_DEPTH) return false

            val resolved = resolveExpression(expression) ?: return false
            if (!visited.add(resolved)) return false

            if (isThemePackageElement(resolved)) return true

            val declaration = resolved.navigationElement ?: resolved
            val declarationText = declaration.text.orEmpty()
            if (declarationText.isBlank()) return false

            if (containsSemanticThemeColorPath(declarationText)) return true
            if (declarationText.contains(COLOR_THEME_PACKAGE)) return true
            if (containsArbitraryComposeColorLiteral(declarationText)) return false

            val body = bodyExpressionOf(resolved) ?: return false
            return resolvesToValidatedColorDeclaration(body, depth + 1, visited)
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

        private fun containsArbitraryComposeColorLiteral(declarationText: String): Boolean {
            // e.g. Color.Red, Color(0xFF123456)
            return declarationText.contains(ARBITRARY_COLOR_ACCESS_REGEX) ||
                declarationText.contains(ARBITRARY_COLOR_CONSTRUCTOR_REGEX)
        }

        private fun reportInvalidColorUsage(colorArgument: UExpression) {
            context.report(
                issue = INVALID_DAX_LIST_ITEM_COLOR_USAGE,
                location = context.getLocation(colorArgument),
                message = INVALID_DAX_LIST_ITEM_COLOR_USAGE.getExplanation(TextFormat.RAW),
            )
        }
    }

    companion object {
        private const val COLOR_THEME_PACKAGE = "com.duckduckgo.common.ui.compose.theme"
        private const val MAX_VALIDATION_DEPTH = 4

        private val ARBITRARY_COLOR_ACCESS_REGEX = Regex("""\bColor\.[A-Za-z_][A-Za-z0-9_]*""")
        private val ARBITRARY_COLOR_CONSTRUCTOR_REGEX = Regex("""\bColor\s*\(""")

        val INVALID_DAX_LIST_ITEM_COLOR_USAGE = Issue
            .create(
                id = "InvalidDaxListItemColorUsage",
                briefDescription = "List item text color parameters should use DuckDuckGoTheme semantic colors",
                explanation = """
                    Use DuckDuckGoTheme semantic colors (e.g. DuckDuckGoTheme.colors.text, DuckDuckGoTheme.textColors) for the primaryTextColor and secondaryTextColor parameters of list items instead of arbitrary Color values.

                    Defaults classes are allowed only when their implementation resolves to DuckDuckGoTheme semantic colors or theme-defined static colors.

                    Examples:
                    • DuckDuckGoTheme.colors.text.primary
                    • theme.colors.text.secondary
                    • DuckDuckGoTheme.textColors.primary
                    • DuckDuckGoTheme.colors.destructive

                    For one-off cases requiring custom colors, use good judgement or consider raising it in the Android Design System AOR.
                """.trimIndent(),
                moreInfo = "",
                category = CUSTOM_LINT_CHECKS,
                priority = 6,
                severity = Severity.WARNING,
                androidSpecific = true,
                implementation = Implementation(
                    DaxListItemColorUsageDetector::class.java,
                    EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                ),
            )
    }
}
