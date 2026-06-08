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
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getParameterForArgument
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class DaxDividerColorUsageDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = DaxDividerColorCallHandler(context)

    internal class DaxDividerColorCallHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val methodName = node.methodName
            if (methodName == "DaxHorizontalDivider" || methodName == "DaxVerticalDivider") {
                checkColorParameter(node)
            }
        }

        private fun checkColorParameter(node: UCallExpression) {
            val colorArgument = node.valueArguments.find { arg ->
                val parameterName = node.getParameterForArgument(arg)?.name
                parameterName == "color"
            } ?: return

            if (!isFromValidDuckDuckGoColorSource(colorArgument)) {
                reportInvalidColorUsage(colorArgument)
            }
        }

        private fun isFromValidDuckDuckGoColorSource(argument: UExpression): Boolean {
            val source = argument.sourcePsi?.text.orEmpty()

            if (containsSemanticThemeColorPath(source)) return true

            if (resolvesToThemePackageElement(argument)) return true

            return resolvesToValidatedColorDeclaration(
                expression = argument,
                depth = 0,
                visited = mutableSetOf(),
            )
        }

        private fun containsSemanticThemeColorPath(source: String): Boolean {
            return SEMANTIC_THEME_COLOR_PATH_REGEX.containsMatchIn(source)
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
            val qualifiedName = when (element) {
                is PsiMethod -> element.containingClass?.qualifiedName
                is PsiField -> element.containingClass?.qualifiedName
                else -> null
            } ?: return false

            return qualifiedName.startsWith(COLOR_THEME_PACKAGE)
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

            val referencedIdentifier = extractSimpleReturnedIdentifier(declarationText) ?: return false
            return isImportedFromThemePackage(
                fileText = declaration.containingFile?.text.orEmpty(),
                identifier = referencedIdentifier,
            )
        }

        private fun containsArbitraryComposeColorLiteral(declarationText: String): Boolean {
            return declarationText.contains(ARBITRARY_COLOR_ACCESS_REGEX) ||
                declarationText.contains(ARBITRARY_COLOR_CONSTRUCTOR_REGEX)
        }

        private fun extractSimpleReturnedIdentifier(declarationText: String): String? {
            val getterMatch = GETTER_IDENTIFIER_REGEX.find(declarationText)
            if (getterMatch != null) return getterMatch.groupValues[1]

            val initializerMatch = INITIALIZER_IDENTIFIER_REGEX.find(declarationText)
            if (initializerMatch != null) return initializerMatch.groupValues[1]

            val returnMatch = RETURN_IDENTIFIER_REGEX.find(declarationText)
            if (returnMatch != null) return returnMatch.groupValues[1]

            return null
        }

        private fun isImportedFromThemePackage(
            fileText: String,
            identifier: String,
        ): Boolean {
            if (fileText.isBlank()) return false
            val escapedThemePackage = Regex.escape(COLOR_THEME_PACKAGE)
            val escapedIdentifier = Regex.escape(identifier)
            val importRegex = Regex("""import\s+$escapedThemePackage\.$escapedIdentifier(\s+as\s+\w+)?""")
            return importRegex.containsMatchIn(fileText)
        }

        private fun reportInvalidColorUsage(colorArgument: UExpression) {
            context.report(
                issue = INVALID_DAX_DIVIDER_COLOR_USAGE,
                location = context.getLocation(colorArgument),
                message = INVALID_DAX_DIVIDER_COLOR_USAGE.getExplanation(TextFormat.RAW),
            )
        }
    }

    companion object {
        private const val COLOR_THEME_PACKAGE = "com.duckduckgo.common.ui.compose.theme"
        private const val MAX_VALIDATION_DEPTH = 4

        private val SEMANTIC_THEME_COLOR_PATH_REGEX =
            Regex("""\.colors\.(backgrounds|text|brand|icons|infoPanel|textField|status|system)\.""")

        private val ARBITRARY_COLOR_ACCESS_REGEX = Regex("""\bColor\.[A-Za-z_][A-Za-z0-9_]*""")
        private val ARBITRARY_COLOR_CONSTRUCTOR_REGEX = Regex("""\bColor\s*\(""")

        private val GETTER_IDENTIFIER_REGEX = Regex("""get\s*\(\s*\)\s*=\s*([A-Za-z_][A-Za-z0-9_]*)""")
        private val INITIALIZER_IDENTIFIER_REGEX = Regex("""=\s*([A-Za-z_][A-Za-z0-9_]*)\s*${'$'}""")
        private val RETURN_IDENTIFIER_REGEX = Regex("""return\s+([A-Za-z_][A-Za-z0-9_]*)""")

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
