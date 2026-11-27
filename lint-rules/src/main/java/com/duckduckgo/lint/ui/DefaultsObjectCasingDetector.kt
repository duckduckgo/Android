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
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UClass
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class DefaultsObjectCasingDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = DefaultsObjectHandler(context)

    internal class DefaultsObjectHandler(private val context: JavaContext) : UElementHandler() {

        override fun visitClass(node: UClass) {
            val defaultsObject = node.sourcePsi as? KtObjectDeclaration ?: return
            val objectName = defaultsObject.name ?: return
            if (!objectName.endsWith(DEFAULTS_SUFFIX)) return

            defaultsObject.declarations
                .filterIsInstance<KtProperty>()
                .filter { it.isCamelCaseConstant() }
                .forEach { property ->
                    val propertyName = property.name.orEmpty()
                    context.report(
                        issue = DEFAULTS_PROPERTY_CASING,
                        scope = property,
                        location = context.getNameLocation(property),
                        message = "Constant `$propertyName` in `$objectName` should be PascalCase: " +
                            "`${propertyName.replaceFirstChar { it.uppercase() }}`",
                    )
                }
        }

        private fun KtProperty.isCamelCaseConstant(): Boolean =
            getter == null && name?.firstOrNull()?.isLowerCase() == true
    }

    companion object {
        private const val DEFAULTS_SUFFIX = "Defaults"

        val DEFAULTS_PROPERTY_CASING = Issue
            .create(
                id = "DefaultsPropertyCasing",
                briefDescription = "Constant properties in Defaults objects should use PascalCase",
                explanation = """
                    Constant properties of `*Defaults` objects, the ones without a custom getter (`Dp`, \
                    `PaddingValues`, `const val`, ...), should use PascalCase, e.g. `ContentPadding` or \
                    `DisabledAlpha`. This follows the Compose API guidelines and Material3 (`ButtonDefaults.MinWidth`).

                    Properties with a custom getter, such as a `@Composable get()` that reads the theme, are not \
                    constants and keep camelCase, as do functions like `colors()`.
                """.trimIndent(),
                moreInfo = "",
                category = CUSTOM_LINT_CHECKS,
                priority = 5,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    DefaultsObjectCasingDetector::class.java,
                    EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                ),
            )
    }
}
